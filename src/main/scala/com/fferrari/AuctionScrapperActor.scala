package com.fferrari

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{Behaviors, TimerScheduler}
import cats.data.Validated.{Invalid, Valid}
import com.fferrari.PriceScrapperProtocol._
import com.fferrari.model.Delcampe
import com.fferrari.scrapper.DelcampeTools.randomDurationMs
import com.fferrari.validation.{AuctionValidator, DelcampeValidator}
import net.ruippeixotog.scalascraper.browser.JsoupBrowser

object AuctionScrapperActor {
  val itemsPerPage: Int = 480

  implicit val jsoupBrowser: JsoupBrowser = JsoupBrowser.typed()

  def apply(): Behavior[PriceScrapperCommand] = Behaviors.setup { context =>
    // TODO fetch from DB
    val websiteInfos: List[WebsiteInfo] = List(
      WebsiteInfo(Delcampe, "https://www.delcampe.net/en_US/collectibles/stamps/zambezia?search_mode=all&order=sale_start_datetime&display_state=sold_items&duration_selection=all&seller_localisation_choice=world", None),
      WebsiteInfo(Delcampe, "https://www.delcampe.net/en_US/collectibles/search?term=&categories%5B%5D=3268&search_mode=all&order=sale_start_datetime&display_state=sold_items&duration_selection=all&seller_localisation_choice=world", None)
    )

    context.self ! ExtractUrls

    processWebsiteInfo(websiteInfos)
  }

  private def processWebsiteInfo(websiteInfos: List[WebsiteInfo], websiteInfosIdx: Int = 0): Behavior[PriceScrapperCommand] =
    Behaviors.withTimers[PriceScrapperCommand] { timers =>
      Behaviors.receivePartial {
        case (context, ExtractUrls) =>
          val auctionValidator = new DelcampeValidator
          context.self ! ExtractAuctionUrls

          if (websiteInfosIdx < websiteInfos.size)
            processAuctionUrls(websiteInfos, websiteInfosIdx, 1, auctionValidator)
          else
            processAuctionUrls(websiteInfos, 0, 1, auctionValidator)
      }
    }

  private def processAuctionUrls(websiteInfos: List[WebsiteInfo],
                                 websiteInfosIdx: Int,
                                 pageNumber: Int,
                                 auctionValidator: AuctionValidator): Behavior[PriceScrapperCommand] =
    Behaviors.withTimers[PriceScrapperCommand] { implicit timers =>
      Behaviors.receivePartial {

        case (context, ExtractAuctionUrls) =>

          val websiteInfo: WebsiteInfo = websiteInfos(websiteInfosIdx)

          context.log.info(s"Scraping Website Info: $websiteInfo")

          auctionValidator.validateListingPage(websiteInfo, 480, pageNumber) match {
            case Valid(jsoupDocument) =>
              auctionValidator.validateAuctionUrls(websiteInfo)(jsoupDocument) match {
                case Valid(urls) if urls.nonEmpty =>
                  timers.startSingleTimer(ExtractAuctions(urls), randomDurationMs())
                  Behaviors.same

                case Valid(urls) if urls.isEmpty =>
                  context.log.info(s"No URLs fetched from the listing page, moving to the next website")
                  processNextWebsite(websiteInfos, websiteInfosIdx)
                // timers.startSingleTimer(ExtractAuctionUrls, randomDurationMs())
                // processAuctionUrls(websiteInfos, websiteInfosIdx, List(), 1, auctionValidator)

                case Invalid(e) =>
                  context.log.error(s"$e, moving to the next website")
                  processNextWebsite(websiteInfos, websiteInfosIdx)
              }

            case Invalid(e) =>
              context.log.info(s"$e, moving to the next website")
              processNextWebsite(websiteInfos, websiteInfosIdx)

            case e =>
              context.log.error(s"Unknown state $e, moving to the next website")
              processNextWebsite(websiteInfos, websiteInfosIdx)
          }

        case (context, ExtractAuctions(auctionUrls)) =>

          auctionUrls match {
            case auctionUrl :: remainingAuctionUrls =>
              context.log.info(s"Scraping auction URL: $auctionUrl")
              auctionValidator.validateAuction(auctionUrl) match {
                case Valid(auction) =>
                  context.log.info(s"Auction fetched successfully $auction")
                case Invalid(e) =>
                  context.log.error(s"Error while fetching auction $auctionUrl ($e)")
              }
              timers.startSingleTimer(ExtractAuctions(remainingAuctionUrls), randomDurationMs())
              Behaviors.same

            case _ =>
              context.log.info("No more urls to process, moving to the next page")
              timers.startSingleTimer(ExtractAuctionUrls, randomDurationMs())
              processAuctionUrls(websiteInfos, 0, pageNumber + 1, auctionValidator)
          }
      }
    }

  def processNextWebsite(websiteInfos: List[WebsiteInfo], websiteInfosIdx: Int)
                        (implicit timers: TimerScheduler[PriceScrapperCommand]): Behavior[PriceScrapperCommand] = {
    timers.startSingleTimer(ExtractUrls, randomDurationMs())
    processWebsiteInfo(websiteInfos, websiteInfosIdx + 1)
  }
}
