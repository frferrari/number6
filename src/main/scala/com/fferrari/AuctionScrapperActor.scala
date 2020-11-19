package com.fferrari

import java.util.Date

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{Behavior, scaladsl}
import com.fferrari.DelcampeTools.{randomDurationMs, relativeToAbsoluteUrl}
import com.fferrari.PriceScrapperProtocol._
import net.ruippeixotog.scalascraper.browser.{Browser, JsoupBrowser}
import net.ruippeixotog.scalascraper.dsl.DSL.Extract._
import net.ruippeixotog.scalascraper.dsl.DSL._
import net.ruippeixotog.scalascraper.model.Element

import scala.util.Try

object AuctionScrapperActor {
  self: AuctionScrapper =>

  val itemsPerPage: Int = 480

  lazy val jsoupBrowser: Browser = JsoupBrowser()

  case class SellerDetails(nickname: Option[String], location: Option[String], isProfessional: Option[Boolean])

  case class Bid(nickname: String, price: BigDecimal, currency: String, quantity: Int, isAutomatic: Boolean, bidAt: Date)

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

          context.log.info(s"Scraping URL: $websiteInfo")
          extractAuctionUrls(websiteInfo, context, pageNumber) match {
            case urls@h :: t =>
              // We schedule the next extraction so that we don't query the website too frequently
              // urls.foreach { url => context.log.info(s"==> ExtractAuctions $url") }
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
              extractAuction(auctionUrl)
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

  def extractAuction(auctionUrl: String) = {
    val htmlDoc: jsoupBrowser.DocumentType = jsoupBrowser.get(auctionUrl)

    // Seller details
    // val sellerDetails: Option[SellerDetails] = extractSellerDetails(htmlDoc)

    for {
      htmlId <- this.fetchId(htmlDoc)
      htmlAuctionTitle <- this.fetchTitle(htmlDoc)
      htmlSellerNickname <- this.fetchSellerNickname(htmlDoc)
      htmlSellerLocation <- this.fetchSellerLocation(htmlDoc)
      htmlAuctionType <- this.fetchAuctionType(htmlDoc)
      htmlStartPrice <- this.fetchStartPrice(htmlDoc)
      htmlFinalPrice <- this.fetchFinalPrice(htmlDoc)
      htmlStartDate <- this.fetchStartDate(htmlDoc)
      htmlEndDate <- this.fetchEndDate(htmlDoc)
      htmlLargeImageUrl <- this.fetchLargeImageUrl(htmlDoc)
      bids = this.fetchBids(htmlDoc)
      bidCount = this.fetchBidCount(htmlDoc)
    } yield {
      println(s">>>>>> AuctionId $htmlId")
      println(s"            htmlSellerNickname $htmlSellerNickname")
      println(s"            htmlSellerLocation $htmlSellerLocation")
      println(s"            htmlLargeImageUrl $htmlLargeImageUrl")
      println(s"            htmlStartDate $htmlStartDate")
      println(s"            htmlEndDate $htmlEndDate")
      println(s"            bidCount $bidCount")
      println(s"            htmlStartPrice $htmlStartPrice")
      println(s"            htmlFinalPrice $htmlFinalPrice")
      println(s"            htmlAuctionType $htmlAuctionType")
      println(s"            htmlAuctionTitle $htmlAuctionTitle")
      println(s"            >>>>>> bids")
      bids.foreach { bid => println(s"                       bid $bid") }
    }

    // Auction details
    /*
    val htmlAuctionTitle: String = htmlDoc >> text("div.item-title h1 span")
    val htmlSellingType = if (htmlDoc >> attr("title")("div.price-info div") == "Auction") BidType else FixedPriceType
    val htmlItemPrice: String = htmlDoc >> text("div#closed-sell div.price-info span.price")
    val currencyAndPrice: Option[(String, BigDecimal)] = DelcampeTools.parseHtmlPrice(htmlItemPrice)
    val htmlImageUrl: String = htmlDoc >> attr("src")("div.item-thumbnails img.img-lense")
    val htmlItemId: String = htmlDoc >> attr("data-id")("div#confirm_question_modal")
    val htmlDescriptionInfo: List[Element] = htmlDoc >> elementList("div#tab-description div.description-info ul > li")
    val htmlSoldAt: Option[String] = Try(htmlDescriptionInfo(1) >> text("div")).toOption

    val (bidCount, bids) =
      htmlSellingType match {
        case BidType =>
          val htmlBidCount = htmlDoc >?> text("ul.bottom-infos-actions li a.orange")
          val bids: List[Bid] = extractBids(htmlDoc, htmlSellingType)
          (DelcampeTools.bidCountFromText(htmlBidCount), bids)
        case FixedPriceType =>
          val bids: List[Bid] = extractBids(htmlDoc, htmlSellingType, currencyAndPrice)
          (1, bids)
      }

    println(s">>>>>> AuctionId $htmlId")
    println(s"            htmlImageUrl $htmlImageUrl")
    println(s"            htmlSoldAt $htmlSoldAt")
    println(s"            bidCount $bidCount")
    println(s"            htmlPrice $htmlItemPrice")
    println(s"            currencyAndPrice $currencyAndPrice")
    println(s"            htmlSellingType $htmlSellingType")
    println(s"            htmlAuctionTitle $htmlAuctionTitle")
    println(s"            >>>>>> bids")
    bids.foreach { bid => println(s"                       bid $bid") }
    */
  }

  /**
   * Extracts the details of a seller
   * @param htmlDoc The html document to be parsed
   * @return
   */
  def extractSellerDetails(htmlDoc: jsoupBrowser.DocumentType): Option[SellerDetails] = {
    (htmlDoc >> elementList("div#seller-info ul > li"))
      .foldLeft(Option.empty[SellerDetails]) {
        case (acc, htmlSellerInfo) =>
          (htmlSellerInfo >?> text("strong")) match {
            case Some(htmlText) =>
              DelcampeTools.extractSellerInfoLabel(htmlText) match {
                case "LOCATION" =>
                  val htmlLocation: String = htmlSellerInfo >> text("div")
                  acc.map(_.copy(location = Some(htmlLocation)))

                case "SELLER" =>
                  val htmlNickname: String = htmlSellerInfo >> text("span.nickname")
                  val isProfessional: Option[Boolean] = Option((htmlSellerInfo >?> element("i.icon-pro")).nonEmpty)
                  acc.map(_.copy(nickname = Some(htmlNickname), isProfessional = isProfessional))

                case _ =>
                  acc
              }
            case None =>
              acc
          }
      }
  }

  /**
   * Extracts the list of bids from an auction page
   * @param htmlDoc The html document to be parsed
   * @return
   */
  def extractBids(htmlDoc: jsoupBrowser.DocumentType, htmlSellingType: AuctionType, currencyAndPrice: Option[(String, BigDecimal)]): List[Bid] =
    htmlSellingType match {
      case BidType =>
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
                Bid(nickname, price, currency, 1, isAutomaticBid, bidDate)
            }
        }
      case FixedPriceType =>
        val htmlPurchaseTable = htmlDoc >> elementList("""div[.id="sales"] div.table-view ul.table-body-list""")
        val htmlPurchaseWithDetails = htmlPurchaseTable.map(purchase => (purchase, purchase >> elementList("li"), currencyAndPrice))

        htmlPurchaseWithDetails.collect {
          case (purchase, htmlTableColumns, Some(cp)) if htmlTableColumns.length >= 3 =>
            val htmlNickname: String = purchase >> text("li.list-user span")
            // val htmlCurrencyAndPrice: Option[(String, BigDecimal)] = (htmlTableColumns(1) >?> text("strong")).flatMap(DelcampeTools.parseHtmlPrice)
            val htmlPurchaseDate: String = htmlTableColumns(2) >> text
            val htmlPurchaseTime: String = htmlTableColumns(3) >> text
            val purchaseDate: Option[Date] = DelcampeTools.parseHtmlShortDate(s"$htmlPurchaseDate $htmlPurchaseTime")
            val purchaseQuantity = DelcampeTools.parseHtmlQuantity(htmlTableColumns(1) >> text)

            Bid(htmlNickname, cp._2, cp._1, purchaseQuantity.getOrElse(0), isAutomatic = false, purchaseDate.get) // TODO remove .get
        }
    }
}
