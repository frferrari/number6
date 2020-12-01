package com.fferrari.actor

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{Behaviors, TimerScheduler}
import cats.data.Validated.{Invalid, Valid}
import com.fferrari.actor.AuctionScrapperProtocol._
import com.fferrari.model.{Delcampe, Batch, WebsiteConfig}
import com.fferrari.scrapper.DelcampeUtil.randomDurationMs
import com.fferrari.validation.{AuctionValidator, DelcampeValidator}
import net.ruippeixotog.scalascraper.browser.JsoupBrowser

import scala.util.Try

object AuctionScrapperActor {
  val itemsPerPage: Int = 480

  def getPage(url: String): Try[JsoupBrowser.JsoupDocument] = Try(jsoupBrowser.get(url))

  implicit val jsoupBrowser: JsoupBrowser = JsoupBrowser.typed()

  def apply(): Behavior[PriceScrapperCommand] = Behaviors.setup { context =>
    // TODO fetch from DB
    val websiteConfigs: List[WebsiteConfig] = List(
      WebsiteConfig(Delcampe, "https://www.delcampe.net/en_US/collectibles/stamps/zambezia?search_mode=all&duration_selection=all", None),
      WebsiteConfig(Delcampe, "https://www.delcampe.net/en_US/collectibles/search?term=&categories%5B%5D=3268&search_mode=all&duration_selection=all", None)
    )

    context.self ! ExtractUrls

    processWebsites(websiteConfigs)
  }

  private def processWebsites(websiteConfigs: List[WebsiteConfig], websiteConfigsIdx: Int = 0): Behavior[PriceScrapperCommand] =
    Behaviors.withTimers[PriceScrapperCommand] { timers =>
      Behaviors.receivePartial {
        case (context, ExtractUrls) =>
          val auctionValidator = new DelcampeValidator
          context.self ! ExtractAuctionUrls

          if (websiteConfigsIdx < websiteConfigs.size)
            processAuctions(websiteConfigs, websiteConfigsIdx, 1, auctionValidator)
          else
            processAuctions(websiteConfigs, 0, 1, auctionValidator)
      }
    }

  private def processAuctions(websiteConfigs: List[WebsiteConfig],
                              websiteConfigsIdx: Int,
                              pageNumber: Int,
                              auctionValidator: AuctionValidator): Behavior[PriceScrapperCommand] =
    Behaviors.withTimers[PriceScrapperCommand] { implicit timers =>
      Behaviors.receivePartial {

        case (context, ExtractAuctionUrls) =>

          val websiteInfo: WebsiteConfig = websiteConfigs(websiteConfigsIdx)

          context.log.info(s"Scraping website URL ${websiteInfo.url} PAGE $pageNumber")

          auctionValidator.fetchListingPage(websiteInfo, getPage, itemsPerPage, pageNumber) match {
            case Valid(jsoupDocument) =>
              auctionValidator.fetchAuctionUrls(websiteInfo)(jsoupDocument) match {
                case Valid(batch@Batch(_, _, auctionUrls)) if auctionUrls.nonEmpty =>
                  timers.startSingleTimer(ExtractAuctions(batch), randomDurationMs())
                  Behaviors.same

                case Valid(Batch(_, _, auctionUrls)) if auctionUrls.isEmpty =>
                  context.log.info(s"No URLs fetched from the listing page, moving to the next website")
                  moveToNextWebsite(websiteConfigs, websiteConfigsIdx)

                case Invalid(e) =>
                  context.log.error(s"$e, moving to the next website")
                  moveToNextWebsite(websiteConfigs, websiteConfigsIdx)
              }

            case Invalid(e) =>
              context.log.info(s"$e, moving to the next website")
              moveToNextWebsite(websiteConfigs, websiteConfigsIdx)

            case e =>
              context.log.error(s"Unknown state $e, moving to the next website")
              moveToNextWebsite(websiteConfigs, websiteConfigsIdx)
          }

        case (context, ExtractAuctions(batch@Batch(_, _, batchAuctionLinks))) =>

          batchAuctionLinks match {
            case batchAuctionLink :: remainingAuctionUrls =>
              context.log.info(s"Scraping auction URL: ${batchAuctionLink.auctionUrl}")

              auctionValidator.fetchAuction(batchAuctionLink.auctionUrl) match {
                case Valid(auction) =>
                  context.log.info(s"Auction fetched successfully ${auction.url}")
                  timers.startSingleTimer(ExtractAuctions(batch.copy(auctionUrls = remainingAuctionUrls)), randomDurationMs())

                  websiteConfigs(websiteConfigsIdx).lastScrappedUrl match {
                    case Some(_) =>
                      Behaviors.same
                    case None =>
                      val updatedWebsiteInfo = websiteConfigs(websiteConfigsIdx).copy(lastScrappedUrl = Some(batchAuctionLink.auctionUrl))
                      processAuctions(websiteConfigs.updated(websiteConfigsIdx, updatedWebsiteInfo), websiteConfigsIdx, pageNumber, auctionValidator)
                  }
                case Invalid(e) =>
                  context.log.error(s"Error while fetching auction $batchAuctionLink, moving to the next website ($e)")
                  moveToNextWebsite(websiteConfigs, websiteConfigsIdx)
              }

            case _ =>
              context.log.info("No more urls to process, moving to the next page")
              timers.startSingleTimer(ExtractAuctionUrls, randomDurationMs())
              processAuctions(websiteConfigs, 0, pageNumber + 1, auctionValidator)
          }
      }
    }

  def moveToNextWebsite(websiteInfos: List[WebsiteConfig], websiteInfosIdx: Int)
                       (implicit timers: TimerScheduler[PriceScrapperCommand]): Behavior[PriceScrapperCommand] = {
    timers.startSingleTimer(ExtractUrls, randomDurationMs())
    processWebsites(websiteInfos, websiteInfosIdx + 1)
  }
}
