package com.fferrari.actor

import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior, Scheduler}
import akka.util.Timeout
import cats.data.Validated.{Invalid, Valid}
import com.fferrari.actor.AuctionScraperProtocol._
import com.fferrari.model.{Batch, BatchSpecification}
import com.fferrari.scraper.DelcampeUtil.randomDurationMs
import com.fferrari.validation.AuctionValidator
import net.ruippeixotog.scalascraper.browser.JsoupBrowser

import scala.concurrent.duration._
import scala.util.Try

object AuctionScraperActor {
  val actorName = "auction-scraper"
  val batchManagerMaxRetries = 3

  implicit val jsoupBrowser: JsoupBrowser = JsoupBrowser.typed()

  def apply[V <: AuctionValidator](validator: () => V): Behavior[AuctionScraperCommand] =
    Behaviors.setup { context =>
      val listingResponseAdapter: ActorRef[Receptionist.Listing] =
        context.messageAdapter[Receptionist.Listing](ListingResponse)

      context.self ! LookupBatchManager
      lookupBatchManager(validator(), listingResponseAdapter, batchManagerMaxRetries)
    }

  private def lookupBatchManager[V <: AuctionValidator](validator: V,
                                                        listingResponseAdapter: ActorRef[Receptionist.Listing],
                                                        maxLookup: Int = 3): Behavior[AuctionScraperCommand] = {
    Behaviors.withTimers[AuctionScraperCommand] { implicit timers =>
      Behaviors.receivePartial {
        // Querying the Receptionist for the BatchManager actor ref
        case (context, LookupBatchManager) =>
          context.system.receptionist ! Receptionist.Find(BatchManagerActor.BatchManagerServiceKey, listingResponseAdapter)
          Behaviors.same

        // Receiving the BatchManager actor ref from the Receptionist
        case (context, ListingResponse(BatchManagerActor.BatchManagerServiceKey.Listing(listings))) if listings.nonEmpty =>
          processListingPage(validator, listings.head)

        // Scheduling a new query for the Receptionist to obtain the BatchManager actor ref
        case _ if maxLookup > 0 =>
          timers.startSingleTimer(LookupBatchManager, 20.seconds)
          lookupBatchManager(validator, listingResponseAdapter, maxLookup - 1)

        // The Receptionist could not find the BatchManager actor ref in the allowed time range, FAIL
        case (context, _) =>
          context.log.error(s"The Receptionist could not find the BatchManager actor ref after $batchManagerMaxRetries retries, FAILING")
          throw new IllegalStateException(s"The Receptionist could not find the BatchManager actor ref after $batchManagerMaxRetries retries")
      }
    }
  }

  private def processListingPage[V <: AuctionValidator](validator: V,
                                                        batchManagerRef: ActorRef[BatchManagerActor.Command]): Behavior[AuctionScraperCommand] =
    Behaviors.withTimers[AuctionScraperCommand] { implicit timers =>
      Behaviors.receivePartial {
        case (context, ExtractListingPageUrls(batchSpecification, pageNumber)) =>
          context.log.info(s"Scraping website URL ${batchSpecification.url} PAGE $pageNumber")

          validator.fetchListingPage(batchSpecification, getPage, pageNumber) match {
            case Valid(jsoupDocument) =>
              validator.fetchAuctionUrls(batchSpecification)(jsoupDocument) match {
                case Valid(batch@Batch(_, _, auctionUrls, _)) if auctionUrls.nonEmpty =>
                  timers.startSingleTimer(ExtractAuctions(batchSpecification, batch, pageNumber), randomDurationMs())
                  Behaviors.same

                //                case Valid(Batch(_, _, auctionUrls, _)) if auctionUrls.isEmpty =>
                //                  context.log.info(s"No URLs fetched from the listing page, moving to the next website")
                //                  moveToNextWebsite(websiteConfigs, websiteConfigsIdx)
                //
                //                case Invalid(e) =>
                //                  context.log.error(s"$e, moving to the next website")
                //                  moveToNextWebsite(websiteConfigs, websiteConfigsIdx)
              }
            //
            //            case Invalid(e) =>
            //              context.log.info(s"$e, moving to the next website")
            //              moveToNextWebsite(websiteConfigs, websiteConfigsIdx)
            //
            //            case e =>
            //              context.log.error(s"Unknown state $e, moving to the next website")
            //              moveToNextWebsite(websiteConfigs, websiteConfigsIdx)
          }

        case (context, ExtractAuctions(batchSpecification, batch@Batch(batchId, _, batchAuctionLinks, auctions), pageNumber)) =>
          batchAuctionLinks match {
            case batchAuctionLink :: remainingAuctionUrls =>
              context.log.info(s"Scraping auction URL: ${batchAuctionLink.auctionUrl}")

              validator.fetchAuction(batchAuctionLink, batchSpecification) match {
                case Valid(auction) =>
                  context.log.info(s"Auction fetched successfully ${auction.url}")
                  val newBatchSpecification: BatchSpecification =
                    batchSpecification.lastUrlScrapped match {
                      case Some(_) =>
                        batchSpecification
                      case None =>
                        batchSpecification.copy(lastUrlScrapped = Some(batchAuctionLink.auctionUrl))
                    }
                  timers.startSingleTimer(
                    ExtractAuctions(
                      newBatchSpecification,
                      batch.copy(auctionUrls = remainingAuctionUrls, auctions = batch.auctions :+ auction),
                      pageNumber + 1
                    ), randomDurationMs())
                  Behaviors.same

                case Invalid(e) =>
                  context.log.error(s"Error while fetching auction $batchAuctionLink, moving to the next auction ($e)")
                  Behaviors.same
              }

            case _ =>
              context.log.info("No more urls to process, moving to the next page")
              implicit val timeout: Timeout = 3.seconds
              implicit val scheduler: Scheduler = context.system.scheduler
              batchManagerRef.ask(ref => BatchManagerActor.CreateBatch(batchSpecification, auctions, ref))
              timers.startSingleTimer(ExtractListingPageUrls(batchSpecification, pageNumber + 1), randomDurationMs())
              Behaviors.same
              // processAuctions(websiteConfigs, 0, pageNumber + 1, validator)
          }

        case (context, msg) =>
          context.log.error(s"Not handling this message while in listing page behavior ($msg)")
          Behaviors.same
      }
    }

//  private def processAuctions[V <: AuctionValidator](validator: V): Behavior[AuctionScraperCommand] =
//    Behaviors.withTimers[AuctionScraperCommand] { implicit timers =>
//      Behaviors.receivePartial {
//        case (context, ExtractAuctions(batchSpecification, batch@Batch(batchId, _, batchAuctionLinks))) =>
//          batchAuctionLinks match {
//            case batchAuctionLink :: remainingAuctionUrls =>
//              context.log.info(s"Scraping auction URL: ${batchAuctionLink.auctionUrl}")
//
//              validator.fetchAuction(batchAuctionLink, batchId) match {
//                case Valid(auction) =>
//                  context.log.info(s"Auction fetched successfully ${auction.url}")
//                  timers.startSingleTimer(ExtractAuctions(batch.copy(auctionUrls = remainingAuctionUrls)), randomDurationMs())
//
//                  batchSpecification.lastUrlScrapped match {
//                    case Some(_) =>
//                      Behaviors.same
//                    case None =>
//                      val updatedWebsiteInfo = batchSpecification.copy(lastUrlScrapped = Some(batchAuctionLink.auctionUrl))
//                      processAuctions(websiteConfigs.updated(websiteConfigsIdx, updatedWebsiteInfo), websiteConfigsIdx, pageNumber, validator)
//                  }
//                case Invalid(e) =>
//                  context.log.error(s"Error while fetching auction $batchAuctionLink, moving to the next website ($e)")
//                  moveToNextWebsite(websiteConfigs, websiteConfigsIdx)
//              }
//
//            case _ =>
//              context.log.info("No more urls to process, moving to the next page")
//              timers.startSingleTimer(ExtractListingPageUrls, randomDurationMs())
//              processAuctions(websiteConfigs, 0, pageNumber + 1, validator)
//          }
//      }
//    }

//  def moveToNextWebsite(websiteInfos: List[WebsiteConfig], websiteInfosIdx: Int)
//                       (implicit timers: TimerScheduler[AuctionScraperCommand]): Behavior[AuctionScraperCommand] = {
//    timers.startSingleTimer(ExtractUrls, randomDurationMs())
//    processWebsites(websiteInfos, websiteInfosIdx + 1)
//  }

  def getPage(url: String): Try[JsoupBrowser.JsoupDocument] = Try(jsoupBrowser.get(url))
}
