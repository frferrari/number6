package com.fferrari

import java.util.Date

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{Behavior, scaladsl}
import com.fferrari.DelcampeTools.{parseHtmlDate, randomDurationMs, relativeToAbsoluteUrl}
import com.fferrari.PriceScrapperProtocol._
import net.ruippeixotog.scalascraper.browser.{Browser, JsoupBrowser}
import net.ruippeixotog.scalascraper.dsl.DSL.Extract._
import net.ruippeixotog.scalascraper.dsl.DSL._
import net.ruippeixotog.scalascraper.model.Element

import scala.jdk.CollectionConverters._

object PriceScrapperDelcampe {
  lazy val jsoupBrowser: Browser = JsoupBrowser()

  case class SellerDetails(nickname: Option[String], location: Option[String], isProfessional: Option[Boolean])

  case class Bid(nickname: String, price: BigDecimal, currency: String, isAutomatic: Boolean, bidAt: Date)

  case class AuctionDetails(dateClosed: Option[Date], sellerDetails: Option[SellerDetails], bids: List[Bid])

  def apply(): Behavior[PriceScrapperCommand] = Behaviors.setup { context =>
    // TODO fetch from DB
    val websiteInfos: List[WebsiteInfo] = List(
      WebsiteInfo("https://www.delcampe.net/en_US/collectibles/search?term=&categories%5B%5D=3268&search_mode=all&order=sale_start_datetime&display_state=sold_items&duration_selection=all&seller_localisation_choice=world", None)
    )

    context.self ! ExtractUrls

    processWebsiteInfo(websiteInfos)
  }

  private def processWebsiteInfo(websiteInfos: Seq[WebsiteInfo]): Behavior[PriceScrapperCommand] =
    Behaviors.withTimers[PriceScrapperCommand] { timers =>
      Behaviors.receivePartial {
        case (context, ExtractUrls) if websiteInfos.nonEmpty =>

          websiteInfos match {
            case websiteInfo :: remainingWebsiteInfos =>
              context.self ! ExtractAuctionUrls(websiteInfo, List(), 1)
              processWebsiteInfo(remainingWebsiteInfos)
          }

        case (context, ExtractAuctionUrls(websiteInfo, auctionUrls, pageNumber)) =>

          context.log.info(s"Scrapping URL: $websiteInfo")
          extractAuctionUrls(websiteInfo, context, pageNumber) match {
            case urls@h :: t =>
              // We schedule the next extraction so that we don't query the website too frequently
              urls.foreach { url => context.log.info(s"==> ExtractAuctions $url") }
              timers.startSingleTimer(ExtractAuctions(urls), randomDurationMs())
            // timers.startSingleTimer(ExtractAuctionUrls(websiteInfo, auctionUrls ++ urls, pageNumber + 1), randomDurationMs())

            case _ =>
              timers.startSingleTimer(ExtractAuctions(auctionUrls), randomDurationMs())
          }

          Behaviors.same

        case (context, ExtractAuctions(auctionUrls)) =>

          auctionUrls match {
            case auctionUrl :: remainingAuctionUrls =>
              context.log.info(s"Scraping auction URL: $auctionUrl")
              timers.startSingleTimer(ExtractAuctions(remainingAuctionUrls), randomDurationMs())

            case _ =>
              timers.startSingleTimer(ExtractUrls, randomDurationMs())
          }

          Behaviors.same
      }
    }

  /**
   * Extracts the list of auction urls from a website html page, and returns only those urls that have not yet
   * been processed (since the last run)
   * @param websiteInfo The details about the website page to process (url, url of the last auction processed)
   * @param context An actor context to allow for logging (mostly)
   * @param pageNumber The page number to fetch (relative to the websiteInfo.url)
   * @return
   */
  def extractAuctionUrls(websiteInfo: WebsiteInfo,
                         context: scaladsl.ActorContext[PriceScrapperCommand],
                         pageNumber: Int = 1): List[String] = {
    // https://github.com/ruippeixotog/scala-scraper
    val itemsPerPage: Int = 480
    val pagedUrl: String = s"${websiteInfo.url}&size=$itemsPerPage&page=$pageNumber"

    context.log.info(s"Parsing url $pagedUrl")

    // Fetch the html page content
    val htmlDoc: jsoupBrowser.DocumentType = jsoupBrowser.get(pagedUrl)

    // Extract all the auction urls
    val htmlAuctionUrls: List[String] =
      for {
        htmlItem <- htmlDoc >> elementList(".item-listing .item-main-infos")
        htmlItemInfo = htmlItem >> element("div.item-info")
        htmlAuctionUrl = relativeToAbsoluteUrl(websiteInfo.url, htmlItemInfo >> element("a") >> attr("href"))
      } yield htmlAuctionUrl

    // Keep only the auction urls that have not yet been processed (since the last run)
    websiteInfo.lastScrappedUrl match {
      case Some(url) if htmlAuctionUrls.contains(url) =>
        htmlAuctionUrls.takeWhile(_ == url)
      case _ =>
        htmlAuctionUrls
    }
  }

  def extractPrices(url: String, context: scaladsl.ActorContext[PriceScrapperCommand], pageNumber: Int = 1): Unit = {
    // https://github.com/ruippeixotog/scala-scraper
    val itemsPerPage: Int = 480
    val pagedUrl: String = s"$url&size=$itemsPerPage&page=$pageNumber"

    context.log.info(s"Parsing url $pagedUrl")

    val htmlDoc: jsoupBrowser.DocumentType = jsoupBrowser.get(pagedUrl)

    val htmlItems: List[Element] = htmlDoc >> elementList(".item-listing .item-main-infos")

    if (htmlItems.nonEmpty) {
      val commands: List[CreateAuction] =
        for {
          htmlItem <- htmlItems
          htmlItemInfo = htmlItem >> element("div.item-info")
          htmlItemSellingType = htmlItemInfo >> element("div.selling-type")
          htmlAuctionUrl = relativeToAbsoluteUrl(url, htmlItemInfo >> element("a") >> attr("href"))
          htmlAuctionTitle = htmlItemInfo >> element("a") >> attr("title")
          // itemSellingTypeText can be icon-fixed-price OR icon-bid
          htmlItemSellingTypeText = htmlItemSellingType >> element("span.selling-type-icon svg") >> attr("class")
          sellingType = if (htmlItemSellingTypeText == "icon-fixed-price") "fixed" else "bid" // TODO enum
          htmlItemPrice = htmlItemInfo >> text("strong.item-price")
          currencyAndPrice = DelcampeTools.parseHtmlPrice(htmlItemPrice)
          htmlBidCount = DelcampeTools.bidCountFromText(htmlItemInfo >?> text("li.orange"))
          htmlImageInfo = htmlItem >> element("div.image-container a.img-view")
          htmlImageUrl = htmlImageInfo >> attr("href")
          htmlItemId = htmlImageInfo >> attr("data-item-id")
          htmlSaleClosed = htmlItem >> text("span")
          auctionDetails = fetchAuctionDetails(htmlAuctionUrl)
          _ = Thread.sleep(1000) // TODO this is temporary, to be REMOVED
          currency <- currencyAndPrice.map(_._1)
          price <- currencyAndPrice.map(_._2)
        } yield CreateAuction(htmlItemId, htmlAuctionUrl, htmlImageUrl, sellingType, htmlAuctionTitle, currency, price, htmlBidCount)

      commands.foreach(command => context.log.info(s"$command"))

      // extractPrices(url, context, pageNumber + 1)
    }
  }

  def fetchAuctionDetails(url: String): AuctionDetails = {
    val htmlDoc: jsoupBrowser.DocumentType = jsoupBrowser.get(url)

    val htmlDateClosed: String = htmlDoc >> element("ul.bottom-infos-actions li") >> text("p")

    AuctionDetails(
      parseHtmlDate(htmlDateClosed),
      extractSellerDetails(htmlDoc),
      extractBids(htmlDoc)
    )
  }

  def extractSellerDetails(htmlDoc: jsoupBrowser.DocumentType): Option[SellerDetails] = {
    (htmlDoc >> elementList("div#seller-info li"))
      .foldLeft(Option.empty[SellerDetails]) {
        case (acc, htmlSellerInfo) =>
          (htmlSellerInfo >> "strong" >> text).trim match {
            case "Location:" =>
              val htmlLocation: String = htmlSellerInfo >> text("div")
              acc.map(_.copy(location = Some(htmlLocation)))

            case "Seller:" =>
              val htmlNickname: String = htmlSellerInfo >> text("span.nickname")
              val isProfessional: Option[Boolean] = Option((htmlSellerInfo >?> element("i.icon-pro")).nonEmpty)
              acc.map(_.copy(nickname = Some(htmlNickname), isProfessional = isProfessional))
          }
      }
  }

  /**
   * Extracts the list of bids from an auction page
   * @param htmlDoc A jsoupBrowser instance
   * @return
   */
  def extractBids(htmlDoc: jsoupBrowser.DocumentType): List[Bid] = {
    val htmlBidsTable = htmlDoc >> elementList("div.bids-container ul.table-body-list")
    val htmlBidsWithDetails = htmlBidsTable.map(bid => (bid, bid >> elementList("li")))

    htmlBidsWithDetails.collect {
      case (bid, htmlTableColumns) if htmlTableColumns.length >= 3 =>
        val htmlNickname: Option[String] = bid >?> text("span.nickname")
        val htmlCurrencyAndPrice: Option[(String, BigDecimal)] = (htmlTableColumns(1) >?> text("strong")).flatMap(DelcampeTools.parseHtmlPrice)
        val htmlBidDate: Option[Date] = (htmlTableColumns(2) >?> text).flatMap(DelcampeTools.parseHtmlShortDate)
        val isAutomaticBid: Boolean = (htmlTableColumns(1) >?> text("span")).contains("automatic")

        (htmlNickname, htmlCurrencyAndPrice, htmlBidDate) match {
          case (Some(nickname), Some((currency, price)), Some(bidDate)) =>
            Bid(nickname, price, currency, isAutomaticBid, bidDate)
        }
    }
  }
}
