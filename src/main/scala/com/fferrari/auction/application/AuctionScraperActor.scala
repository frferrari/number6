package com.fferrari.auction.application

import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, TimerScheduler}
import akka.actor.typed.{ActorRef, Behavior}
import akka.pattern.StatusReply
import cats.data.Chain
import cats.data.Validated.{Invalid, Valid}
import com.fferrari.auction.application.DelcampeUtil.randomDurationMs
import com.fferrari.auction.domain.{Auction, AuctionLink, Batch, ListingPageAuctionLinks}
import com.fferrari.batch.domain.BatchEntity
import com.fferrari.batchmanager.application.BatchManagerActor
import com.fferrari.batchmanager.domain.{BatchManagerEntity, BatchSpecification}
import net.ruippeixotog.scalascraper.browser.JsoupBrowser

import scala.concurrent.duration._
import scala.util.Try

object AuctionScraperActor {
  val actorName = "auction-scraper"
  val batchManagerMaxRetries = 3

  // Command
  sealed trait Command
  final case object LookupBatchManager extends Command
  final case class ProcessBatchSpecification(batchSpecification: BatchSpecification, replyTo: ActorRef[StatusReply[Reply]]) extends Command
  final case object ExtractListingPageUrls extends Command
  final case object ExtractAuctions extends Command
  final case class ListingResponse(listing: Receptionist.Listing) extends Command

  // Reply
  sealed trait Reply
  final case class Busy(request: BatchSpecification.ID, busy: BatchSpecification.ID) extends Reply
  final case class StartProcessing(batchSpecificationID: BatchSpecification.ID) extends Reply

  implicit val jsoupBrowser: JsoupBrowser = JsoupBrowser.typed()

  def apply[V <: AuctionValidator](validator: () => V): Behavior[Command] =
    Behaviors.setup { context =>
      Behaviors.withTimers { timers =>
        context.log.info("Starting")

        val listingResponseAdapter: ActorRef[Receptionist.Listing] =
          context.messageAdapter[Receptionist.Listing](ListingResponse)

        context.log.info("Asking the Receptionist for the BatchManager ref")
        new AuctionScraperActor(validator(), listingResponseAdapter, context, timers).lookupBatchManager()
      }
    }
}

class AuctionScraperActor[V <: AuctionValidator] private(validator: V,
                                                         listingResponseAdapter: ActorRef[Receptionist.Listing],
                                                         context: ActorContext[AuctionScraperActor.Command],
                                                         timers: TimerScheduler[AuctionScraperActor.Command]) {

  import AuctionScraperActor._

  private def lookupBatchManager(retries: Int = AuctionScraperActor.batchManagerMaxRetries): Behavior[Command] = {
    context.self ! LookupBatchManager

    Behaviors.withTimers[Command] { implicit timers =>
      Behaviors.receivePartial {
        // Querying the Receptionist for the BatchManager actor ref
        case (context, LookupBatchManager) =>
          context.system.receptionist ! Receptionist.Find(BatchManagerActor.BatchManagerServiceKey, listingResponseAdapter)
          Behaviors.same

        // Receiving the BatchManager actor ref from the Receptionist
        case (context, ListingResponse(BatchManagerActor.BatchManagerServiceKey.Listing(listings))) if listings.nonEmpty =>
          context.log.info("The Receptionist successfully provided the BatchManager ref")
          idle(listings.head)

        // Scheduling a new query for the Receptionist to obtain the BatchManager actor ref
        case _ if AuctionScraperActor.batchManagerMaxRetries > 0 =>
          timers.startSingleTimer(LookupBatchManager, 20.seconds)
          lookupBatchManager(retries - 1)

        // The Receptionist could not find the BatchManager actor ref in the allowed time range, FAIL
        case (context, _) =>
          context.log.error(s"The Receptionist could not find the BatchManager actor ref after $batchManagerMaxRetries retries, FAILING")
          throw new IllegalStateException(s"The Receptionist could not find the BatchManager actor ref after $batchManagerMaxRetries retries")
      }
    }
  }

  private def idle(batchManagerRef: ActorRef[BatchManagerEntity.Command]): Behavior[Command] =
    Behaviors.receivePartial {
      case (context, ProcessBatchSpecification(batchSpecification, replyTo)) =>
        replyTo ! StatusReply.success(StartProcessing(batchSpecification.batchSpecificationID))
        context.self ! ExtractListingPageUrls
        processListingPage(batchManagerRef, batchSpecification, 1)

      case (context, cmd) =>
        throw new IllegalStateException(s"Unexpected command $cmd received in behavior [idle]")
    }

  private def processListingPage(batchManagerRef: ActorRef[BatchManagerEntity.Command],
                                 batchSpecification: BatchSpecification,
                                 pageNumber: Int,
                                 firstAuctionUrl: Option[String] = None): Behavior[Command] =
    Behaviors.receivePartial {
      case (context, LookupBatchManager) =>
        throw new IllegalStateException(s"Unexpected command LookupBatchManager received in [processListingPage] behavior")

      case (context, msg@ProcessBatchSpecification(_, replyTo)) =>
        replyTo ! StatusReply.success(Busy(request = msg.batchSpecification.batchSpecificationID, busy = batchSpecification.batchSpecificationID))
        Behaviors.same

      case (context, ExtractListingPageUrls) =>
        context.log.info(s"Scraping website URL ${batchSpecification.url} PAGE $pageNumber")
        validator.fetchListingPage(batchSpecification.url, getPage, pageNumber) match {
          case Valid(jsoupDocument) =>
            validator.fetchListingPageAuctionLinks(batchSpecification.url, batchSpecification.lastUrlVisited)(jsoupDocument) match {
              case Valid(listingPageAuctionLinks@ListingPageAuctionLinks(_, auctionLinks)) if auctionLinks.nonEmpty =>
                context.log.info(s"Listing page auction links successfully extracted")
                context.self ! ExtractAuctions
                processAuctions(batchManagerRef, batchSpecification, pageNumber, firstAuctionUrl, listingPageAuctionLinks)

              case Valid(ListingPageAuctionLinks(_, auctionLinks)) if auctionLinks.isEmpty =>
                context.log.info(s"No auction links extracted from the listing page, going back to [idle] behavior")
                idle(batchManagerRef)

              case i =>
                context.log.error(s"Error while fetching the listing page auction links ($i)")
                idle(batchManagerRef)
            }

          case Invalid(Chain(LastListingPageReached)) =>
            context.log.info(s"Last listing page reached, no more auction links to process")
            idle(batchManagerRef)

          case Invalid(i) =>
            context.log.error(s"No more auction links to process ($i)")
            idle(batchManagerRef)
        }

      case (context, cmd) =>
        context.log.error(s"Unexpected command $cmd received while in [processListingPage] behavior")
        Behaviors.same
    }

  private def processAuctions(batchManagerRef: ActorRef[BatchManagerEntity.Command],
                              batchSpecification: BatchSpecification,
                              pageNumber: Int,
                              firstAuctionUrl: Option[String],
                              listingPageAuctionLinks: ListingPageAuctionLinks,
                              auctions: List[Auction] = Nil): Behavior[Command] = {
    Behaviors.receivePartial {
      case (context, msg@ProcessBatchSpecification(_, replyTo)) =>
        replyTo ! StatusReply.success(Busy(request = msg.batchSpecification.batchSpecificationID, busy = batchSpecification.batchSpecificationID))
        Behaviors.same

      case (context, ExtractAuctions) =>
        listingPageAuctionLinks.auctionLinks match {
          case auctionLink :: remainingAuctionLinks =>
            context.log.info(s"Scraping auction at URL: ${auctionLink.auctionUrl}")

            validator.fetchAuction(auctionLink, batchSpecification.batchSpecificationID) match {
              case Valid(auction) =>
                context.log.info(s"Auction fetched successfully: ${auction.url}")
                timers.startSingleTimer(ExtractAuctions, randomDurationMs())
                processAuctions(
                  batchManagerRef,
                  batchSpecification,
                  pageNumber,
                  firstAuctionUrl.orElse(Some(auctionLink.auctionUrl)),
                  listingPageAuctionLinks.copy(auctionLinks = remainingAuctionLinks),
                  auctions :+ auction)

              case Invalid(e) =>
                context.log.error(s"Error while fetching auction $auctionLink, moving to the next auction ($e)")
                Behaviors.same
            }

          case _ =>
            context.log.info("No more auction links to process, creating a Batch, then moving to the next listing page")

            // Create a Batch with the extracted auctions
            batchManagerRef.ask(ref => BatchManagerEntity.CreateBatch(batchSpecification.batchSpecificationID, auctions, ref))(3.seconds, context.system.scheduler)

            // Move the the next listing page
            val newLastUrlVisited = if (pageNumber == 1) firstAuctionUrl else batchSpecification.lastUrlVisited
            timers.startSingleTimer(ExtractListingPageUrls, randomDurationMs())
            processListingPage(batchManagerRef, batchSpecification, pageNumber + 1, firstAuctionUrl)
        }
    }
  }

  def getPage(url: String): Try[JsoupBrowser.JsoupDocument] = Try(jsoupBrowser.get(url))
}