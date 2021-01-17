package com.fferrari.auction.application

import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, TimerScheduler}
import akka.actor.typed.{ActorRef, Behavior}
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

  sealed trait Command
  final case object LookupBatchManager extends Command
  final case object ExtractUrls extends Command
  final case class ExtractListingPageUrls(batchSpecificationID: BatchSpecification.ID,
                                          listingPageUrl: String,
                                          lastUrlVisited: Option[String],
                                          pageNumber: Int = 1) extends Command
  final case class ExtractAuctions(batchSpecificationID: BatchSpecification.ID,
                                   listingPageUrl: String,
                                   lastUrlVisited: Option[String],
                                   firstAuctionUrl: Option[String],
                                   auctionLinks: ListingPageAuctionLinks,
                                   pageNumber: Int,
                                   auctions: List[Auction]) extends Command
  final case class ListingResponse(listing: Receptionist.Listing) extends Command

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
          processListingPage(listings.head)

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

  private def processListingPage(batchManagerRef: ActorRef[BatchManagerEntity.Command]): Behavior[Command] =
    Behaviors.receivePartial {
      case (context, ExtractListingPageUrls(batchSpecificationID, listingPageUrl, lastUrlVisited, pageNumber)) =>
        context.log.info(s"Scraping website URL $listingPageUrl PAGE $pageNumber")

        validator.fetchListingPage(listingPageUrl, getPage, pageNumber) match {
          case Valid(jsoupDocument) =>
            validator.fetchListingPageAuctionLinks(listingPageUrl, lastUrlVisited)(jsoupDocument) match {
              case Valid(listingPageAuctionLinks@ListingPageAuctionLinks(_, auctionLinks)) if auctionLinks.nonEmpty =>
                context.log.info(s"Auction links successfully fetched")
                timers.startSingleTimer(ExtractAuctions(batchSpecificationID, listingPageUrl, lastUrlVisited, None, listingPageAuctionLinks, pageNumber, Nil), randomDurationMs())
                Behaviors.same

              case Valid(ListingPageAuctionLinks(_, auctionLinks)) if auctionLinks.isEmpty =>
                context.log.info(s"No auction links fetched from the listing page")
                Behaviors.same

              case i =>
                context.log.error(s"Error while fetching the auction links ($i)")
                // TODO Fix me??
                Behaviors.same
            }

          case Invalid(Chain(LastListingPageReached)) =>
            context.log.info(s"Last listing page reached, no more auction links to process")
            Behaviors.same

          case Invalid(i) =>
            context.log.error(s"No more auction links to process ($i)")
            Behaviors.same
        }

      case (context, ExtractAuctions(batchSpecificationID, listingPageUrl, lastUrlVisited, firstAuctionUrl, listingPageAuctionLinks@ListingPageAuctionLinks(_, batchAuctionLinks), pageNumber, auctions)) =>
        batchAuctionLinks match {
          case auctionLink :: remainingAuctionLinks =>
            context.log.info(s"Scraping auction at URL: ${auctionLink.auctionUrl}")

            validator.fetchAuction(auctionLink, batchSpecificationID) match {
              case Valid(auction) =>
                context.log.info(s"Auction fetched successfully ${auction.url}")
                timers.startSingleTimer(
                  ExtractAuctions(
                    batchSpecificationID,
                    listingPageUrl,
                    lastUrlVisited,
                    firstAuctionUrl.orElse(Some(auctionLink.auctionUrl)),
                    listingPageAuctionLinks.copy(auctionLinks = remainingAuctionLinks),
                    pageNumber,
                    auctions :+ auction), randomDurationMs())
                Behaviors.same

              case Invalid(e) =>
                context.log.error(s"Error while fetching auction $auctionLink, moving to the next auction ($e)")
                Behaviors.same
            }

          case _ =>
            context.log.info("No more auction links to process, creating a Batch, then moving to the next listing page")
            batchManagerRef.ask(ref => BatchManagerEntity.CreateBatch(BatchEntity.generateID, batchSpecificationID, auctions, ref))(3.seconds, context.system.scheduler)
            val newLastUrlVisited = if (pageNumber == 1) firstAuctionUrl else lastUrlVisited
            timers.startSingleTimer(ExtractListingPageUrls(batchSpecificationID, listingPageUrl, newLastUrlVisited, pageNumber + 1), randomDurationMs())
            Behaviors.same
        }

      case (context, msg) =>
        context.log.error(s"Not handling this message while in listing page behavior ($msg)")
        Behaviors.same
    }

  def getPage(url: String): Try[JsoupBrowser.JsoupDocument] = Try(jsoupBrowser.get(url))
}