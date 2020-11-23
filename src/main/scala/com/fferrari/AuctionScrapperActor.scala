package com.fferrari

import java.util.Date

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import cats.data.Validated.{Invalid, Valid}
import com.fferrari.scrapper.DelcampeTools.randomDurationMs
import com.fferrari.PriceScrapperProtocol._
import com.fferrari.scrapper.{AuctionScrapper, Delcampe, DelcampeExtractor, DelcampeScrapper}
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
              val auctionScrapper = new DelcampeScrapper with DelcampeExtractor
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

          auctionScrapper.fetchListingPageUrls(websiteInfo)(jsoupDocument) match {
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
              auctionScrapper.fetchAuction(auctionUrl) match {
                case Valid(v) =>
                  println("====> Auction fetched successfully")
                case Invalid(n) =>
                  println(s"====> $n")
              }
              timers.startSingleTimer(ExtractAuctions(remainingAuctionUrls), randomDurationMs())

            case _ =>
              timers.startSingleTimer(ExtractUrls, randomDurationMs())
          }

          Behaviors.same
      }
    }
}
