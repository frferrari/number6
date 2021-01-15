package com.fferrari.auction.application

import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import cats.data.Chain
import cats.data.Validated.{Invalid, Valid}
import com.fferrari.auction.application.DelcampeUtil.randomDurationMs
import com.fferrari.auction.domain.es.AuctionScraperProtocol._
import com.fferrari.auction.validator.{AuctionValidator, LastListingPageReached}
import com.fferrari.batch.domain.Batch
import com.fferrari.batchmanager.application.BatchManagerActor
import com.fferrari.batchmanager.domain.es.BatchManagerCommand
import com.fferrari.batchscheduler.domain.BatchSpecification
import net.ruippeixotog.scalascraper.browser.JsoupBrowser

import scala.concurrent.duration._
import scala.util.Try

object AuctionScraperActor {
  val actorName = "auction-scraper"
  val batchManagerMaxRetries = 3

  implicit val jsoupBrowser: JsoupBrowser = JsoupBrowser.typed()

  def apply[V <: AuctionValidator](validator: () => V): Behavior[AuctionScraperCommand] =
    Behaviors.setup { context =>
      context.log.info("Starting")

      val listingResponseAdapter: ActorRef[Receptionist.Listing] =
        context.messageAdapter[Receptionist.Listing](ListingResponse)

      context.self ! LookupBatchManager
      context.log.info("Asking the Receptionist for the BatchManager ref")
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
          context.log.info("The Receptionist successfully provided the BatchManager ref")
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
                                                        batchManagerRef: ActorRef[BatchManagerCommand]): Behavior[AuctionScraperCommand] =
    Behaviors.withTimers[AuctionScraperCommand] { implicit timers =>
      Behaviors.receivePartial {
        case (context, ExtractListingPageUrls(batchSpecification, pageNumber)) =>
          context.log.info(s"Scraping website URL ${batchSpecification.url} PAGE $pageNumber")

          validator.fetchListingPage(batchSpecification, getPage, pageNumber) match {
            case Valid(jsoupDocument) =>
              validator.fetchAuctionUrls(batchSpecification, pageNumber)(jsoupDocument) match {
                case Valid(batch@Batch(_, _, _, auctionUrls, _, _)) if auctionUrls.nonEmpty =>
                  context.log.info(s"Auction urls fetched, batchId $batch")
                  timers.startSingleTimer(ExtractAuctions(batchSpecification, batch, pageNumber), randomDurationMs())
                  Behaviors.same

                case Valid(Batch(_, _, _, auctionUrls, _, _)) if auctionUrls.isEmpty =>
                  context.log.info(s"No URLs fetched from the listing page")
                  Behaviors.same

                case Invalid(i) =>
                  context.log.error(s"Error while fetching the auction urls ($i)")
                  // TODO Fix me??
                  Behaviors.same
              }

            case Invalid(Chain(LastListingPageReached)) =>
              context.log.info(s"Last listing page reached, no more auction urls to process")
              Behaviors.same

            case Invalid(i) =>
              context.log.error(s"No more auction urls to process ($i)")
              Behaviors.same
          }

        case (context, ExtractAuctions(batchSpecification, batch@Batch(batchId, _, _, batchAuctionLinks, auctions, firstUrlScraped), pageNumber)) =>
          batchAuctionLinks match {
            case batchAuctionLink :: remainingAuctionUrls =>
              context.log.info(s"Scraping auction URL: ${batchAuctionLink.auctionUrl}")

              validator.fetchAuction(batchAuctionLink, batchSpecification) match {
                case Valid(auction) =>
                  context.log.info(s"Auction fetched successfully ${auction.url}")
                  val newBatchSpecification: BatchSpecification = {
                    batchSpecification.lastUrlScraped match {
                      case Some(_) =>
                        batchSpecification
                      case None =>
                        batchSpecification.copy(lastUrlScraped = Some(batchAuctionLink.auctionUrl))
                    }
                  }
                  val newBatch = batch.copy(auctionUrls = remainingAuctionUrls, auctions = batch.auctions :+ auction)
                  timers.startSingleTimer(ExtractAuctions(newBatchSpecification, newBatch, pageNumber), randomDurationMs())
                  Behaviors.same

                case Invalid(e) =>
                  context.log.error(s"Error while fetching auction $batchAuctionLink, moving to the next auction ($e)")
                  Behaviors.same
              }

            case _ =>
              context.log.info("No more auction urls to process, creating a Batch, then moving to the next listing page")
              batchManagerRef.ask(ref => BatchManagerCommand.CreateBatch(batch.batchId, batchSpecification.id, firstUrlScraped, auctions, ref))(3.seconds, context.system.scheduler)
              timers.startSingleTimer(ExtractListingPageUrls(batchSpecification, pageNumber + 1), randomDurationMs())
              Behaviors.same
          }

        case (context, msg) =>
          context.log.error(s"Not handling this message while in listing page behavior ($msg)")
          Behaviors.same
      }
    }

  def getPage(url: String): Try[JsoupBrowser.JsoupDocument] = Try(jsoupBrowser.get(url))
}
