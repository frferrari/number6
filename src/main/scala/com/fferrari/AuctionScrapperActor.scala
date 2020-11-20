package com.fferrari

import java.util.Date

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import com.fferrari.DelcampeTools.randomDurationMs
import com.fferrari.PriceScrapperProtocol._
import net.ruippeixotog.scalascraper.browser.JsoupBrowser
import net.ruippeixotog.scalascraper.browser.JsoupBrowser.JsoupDocument

object AuctionScrapperActor {
  val itemsPerPage: Int = 480

  implicit val jsoupBrowser: JsoupBrowser = JsoupBrowser.typed()

  case class SellerDetails(nickname: Option[String], location: Option[String], isProfessional: Option[Boolean])

  case class Bid(nickname: String, price: BigDecimal, currency: String, quantity: Int, isAutomatic: Boolean, bidAt: Date)

  case class AuctionDetails(dateClosed: Option[Date], sellerDetails: Option[SellerDetails], bids: List[Bid])

  def apply(): Behavior[PriceScrapperCommand] = Behaviors.setup { context =>
    // TODO fetch from DB
    val websiteInfos: List[WebsiteInfo] = List(
      WebsiteInfo(Delcampe, "https://www.delcampe.net/en_US/collectibles/search?term=&categories%5B%5D=3268&search_mode=all&order=sale_start_datetime&display_state=sold_items&duration_selection=all&seller_localisation_choice=world", None)
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
              val auctionScrapper = new DelcampeAuctionScrapper
              context.self ! ExtractAuctionUrls(websiteInfo, List(), 1)
              processAuctionUrls(remainingWebsiteInfos, auctionScrapper)
          }
      }
    }

  private def processAuctionUrls(websiteInfos: Seq[WebsiteInfo],
                                 auctionScrapper: AuctionScrapper): Behavior[PriceScrapperCommand] =
    Behaviors.withTimers[PriceScrapperCommand] { timers =>
      Behaviors.receivePartial {

        case (context, ExtractAuctionUrls(websiteInfo, auctionUrls, pageNumber)) =>

          context.log.info(s"Scraping URL: $websiteInfo")
          val jsoupDocument: JsoupDocument = auctionScrapper.fetchListingPage(websiteInfo, 480, pageNumber)

          auctionScrapper.fetchAuctionUrls(websiteInfo)(jsoupDocument) match {
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


  def extractAuction(auctionUrl: String): Unit = {
    implicit val htmlDoc: jsoupBrowser.DocumentType = jsoupBrowser.get(auctionUrl)
    val auctionScrapper = new DelcampeAuctionScrapper

    val htmlId = auctionScrapper.fetchId
    val htmlAuctionTitle = auctionScrapper.fetchTitle
    val htmlIsSold = auctionScrapper.fetchIsSold
    val htmlSellerNickname = auctionScrapper.fetchSellerNickname
    val htmlSellerLocation = auctionScrapper.fetchSellerLocation
    val htmlAuctionType = auctionScrapper.fetchAuctionType
    val htmlStartPrice = auctionScrapper.fetchStartPrice
    val htmlFinalPrice = auctionScrapper.fetchFinalPrice
    val htmlStartDate = auctionScrapper.fetchStartDate
    val htmlEndDate = auctionScrapper.fetchEndDate
    val htmlLargeImageUrl = auctionScrapper.fetchLargeImageUrl
    val bids = auctionScrapper.fetchBids
    val bidCount = auctionScrapper.fetchBidCount
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

    /*
    for {
      htmlId <- auctionScrapper.fetchId
      htmlAuctionTitle <- auctionScrapper.fetchTitle
      htmlSellerNickname <- auctionScrapper.fetchSellerNickname
      htmlSellerLocation <- auctionScrapper.fetchSellerLocation
      htmlAuctionType <- auctionScrapper.fetchAuctionType
      htmlStartPrice <- auctionScrapper.fetchStartPrice
      htmlFinalPrice = auctionScrapper.fetchFinalPrice
      htmlStartDate <- auctionScrapper.fetchStartDate
      htmlEndDate = auctionScrapper.fetchEndDate
      htmlLargeImageUrl <- auctionScrapper.fetchLargeImageUrl
      bids = auctionScrapper.fetchBids
      bidCount = auctionScrapper.fetchBidCount
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
     */

    ()
  }
}
