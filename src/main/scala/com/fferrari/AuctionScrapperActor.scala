package com.fferrari

import java.util.Date

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{Behavior, scaladsl}
import com.fferrari.DelcampeTools.{randomDurationMs, relativeToAbsoluteUrl}
import com.fferrari.PriceScrapperProtocol._
import net.ruippeixotog.scalascraper.browser.{Browser, JsoupBrowser}
import net.ruippeixotog.scalascraper.dsl.DSL.Extract._
import net.ruippeixotog.scalascraper.dsl.DSL._

object AuctionScrapperActor {
  val itemsPerPage: Int = 480

  val jsoupBrowser: JsoupBrowser = JsoupBrowser.typed()

  case class SellerDetails(nickname: Option[String], location: Option[String], isProfessional: Option[Boolean])

  case class Bid(nickname: String, price: BigDecimal, currency: String, quantity: Int, isAutomatic: Boolean, bidAt: Date)

  case class AuctionDetails(dateClosed: Option[Date], sellerDetails: Option[SellerDetails], bids: List[Bid])

  def apply(): Behavior[PriceScrapperCommand] = Behaviors.setup { context =>
    // TODO fetch from DB
    val websiteInfos: List[WebsiteInfo] = List(
      WebsiteInfo("https://www.delcampe.net/en_US/collectibles/search?term=&categories%5B%5D=3268&search_mode=all&order=sale_start_datetime&display_state=sold_items&duration_selection=all&seller_localisation_choice=world", None)
    )

    context.self ! ExtractUrls

    processWebsiteInfo(websiteInfos, new DelcampeAuctionScrapper)
  }

  private def processWebsiteInfo[S <: AuctionScrapper](websiteInfos: Seq[WebsiteInfo],
                                                       auctionScrapper: S): Behavior[PriceScrapperCommand] =
    Behaviors.withTimers[PriceScrapperCommand] { timers =>
      Behaviors.receivePartial {
        case (context, ExtractUrls) if websiteInfos.nonEmpty =>

          websiteInfos match {
            case websiteInfo :: remainingWebsiteInfos =>
              context.self ! ExtractAuctionUrls(websiteInfo, List(), 1)
              processWebsiteInfo(remainingWebsiteInfos, auctionScrapper)
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
              extractAuction(auctionUrl, auctionScrapper)
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
  def extractAuctionUrls[S <: AuctionScrapper](websiteInfo: WebsiteInfo,
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

  def extractAuction[S <: AuctionScrapper](auctionUrl: String, auctionScrapper: S) = {
    val htmlDoc: jsoupBrowser.DocumentType = jsoupBrowser.get(auctionUrl)

    val htmlId = auctionScrapper.fetchId(htmlDoc)
    val htmlAuctionTitle = auctionScrapper.fetchTitle(htmlDoc)
    val htmlIsSold = auctionScrapper.fetchIsSold(htmlDoc)
    val htmlSellerNickname = auctionScrapper.fetchSellerNickname(htmlDoc)
    val htmlSellerLocation = auctionScrapper.fetchSellerLocation(htmlDoc)
    val htmlAuctionType = auctionScrapper.fetchAuctionType(htmlDoc)
    val htmlStartPrice = auctionScrapper.fetchStartPrice(htmlDoc)
    val htmlFinalPrice = auctionScrapper.fetchFinalPrice(htmlDoc)
    val htmlStartDate = auctionScrapper.fetchStartDate(htmlDoc)
    val htmlEndDate = auctionScrapper.fetchEndDate(htmlDoc)
    val htmlLargeImageUrl = auctionScrapper.fetchLargeImageUrl(htmlDoc)
    val bids = auctionScrapper.fetchBids(htmlDoc)
    val bidCount = auctionScrapper.fetchBidCount(htmlDoc)
    println(s">>>>>> AuctionId $htmlId")
    println(s"            htmlIsSold $htmlIsSold")
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

    for {
      htmlId <- auctionScrapper.fetchId(htmlDoc)
      htmlAuctionTitle <- auctionScrapper.fetchTitle(htmlDoc)
      htmlSellerNickname <- auctionScrapper.fetchSellerNickname(htmlDoc)
      htmlSellerLocation <- auctionScrapper.fetchSellerLocation(htmlDoc)
      htmlAuctionType <- auctionScrapper.fetchAuctionType(htmlDoc)
      htmlStartPrice <- auctionScrapper.fetchStartPrice(htmlDoc)
      htmlFinalPrice = auctionScrapper.fetchFinalPrice(htmlDoc)
      htmlStartDate <- auctionScrapper.fetchStartDate(htmlDoc)
      htmlEndDate = auctionScrapper.fetchEndDate(htmlDoc)
      htmlLargeImageUrl <- auctionScrapper.fetchLargeImageUrl(htmlDoc)
      bids = auctionScrapper.fetchBids(htmlDoc)
      bidCount = auctionScrapper.fetchBidCount(htmlDoc)
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

    ()
  }
}
