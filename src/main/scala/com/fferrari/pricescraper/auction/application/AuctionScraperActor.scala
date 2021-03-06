package com.fferrari.pricescraper.auction.application

import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.scaladsl.{Behaviors, TimerScheduler}
import akka.actor.typed.{ActorRef, Behavior}
import akka.pattern.StatusReply
import cats.data.Chain
import cats.data.Validated.{Invalid, Valid}
import com.fferrari.pricescraper.auction.application.DelcampeUtil.randomDurationMs
import com.fferrari.pricescraper.auction.domain.AuctionScraperCommand._
import com.fferrari.pricescraper.auction.domain.{Auction, AuctionScraperCommand, ListingPageAuctionLinks}
import com.fferrari.pricescraper.batchmanager.domain.{BatchManagerCommand, BatchSpecification}
import net.ruippeixotog.scalascraper.browser.JsoupBrowser

import scala.concurrent.duration._
import scala.util.{Success, Try}

object AuctionScraperActor {
  val actorName = "auction-scraper"
  val batchManagerMaxRetries = 3

  implicit val jsoupBrowser: JsoupBrowser = JsoupBrowser.typed()

  def apply[V <: AuctionValidator](validator: () => V,
                                   batchManagerRef: ActorRef[BatchManagerCommand]): Behavior[AuctionScraperCommand] =
    Behaviors.setup { context =>
      Behaviors.withTimers { implicit timers =>
        context.log.info("Starting")

        new AuctionScraperActor(validator(), batchManagerRef).idle
      }
    }
}

class AuctionScraperActor[V <: AuctionValidator] private(validator: V,
                                                         batchManagerRef: ActorRef[BatchManagerCommand])
                                                        (implicit timers: TimerScheduler[AuctionScraperCommand]) {

  import AuctionScraperActor._

  private def idle: Behavior[AuctionScraperCommand] =
    Behaviors.receivePartial {
      case (context, StartAuctionScraper) =>
        context.self ! AskNextBatchSpecification
        Behaviors.same

      case (context, AskNextBatchSpecification) =>
        context.pipeToSelf(batchManagerRef.ask(BatchManagerCommand.ProvideNextBatchSpecification("delcampe", _))(3.seconds,context.system.scheduler)) {
          case Success(StatusReply.Success(ProceedToBatchSpecification(batchSpecification))) =>
            ProcessBatchSpecification(batchSpecification)

          case _ =>
            AskNextBatchSpecification
        }
        Behaviors.same

      case (context, ProcessBatchSpecification(batchSpecification)) =>
        context.self ! ExtractListingPageUrls
        processListingPage(batchSpecification, 1)

      case (context, StopAuctionScraper) =>
        Behaviors.stopped

      case (context, cmd) =>
        throw new IllegalStateException(s"Unexpected command $cmd received in behavior [idle]")
    }

  private def processListingPage(batchSpecification: BatchSpecification,
                                 pageNumber: Int,
                                 firstAuctionUrl: Option[String] = None): Behavior[AuctionScraperCommand] =
    Behaviors.receivePartial {
      case (context, cmd: ProcessBatchSpecification) =>
        throw new IllegalStateException(s"Unexpected command $cmd received in state [processListingPage]")

      case (context, ExtractListingPageUrls) =>
        context.log.info(s"Scraping website URL ${batchSpecification.listingPageUrl} PAGE $pageNumber")
        validator.fetchListingPage(batchSpecification.listingPageUrl, getPage, pageNumber) match {
          case Valid(jsoupDocument) =>
            validator.fetchListingPageAuctionLinks(batchSpecification.listingPageUrl, batchSpecification.lastUrlVisited)(jsoupDocument) match {
              case Valid(listingPageAuctionLinks@ListingPageAuctionLinks(_, auctionLinks)) if auctionLinks.nonEmpty =>
                context.self ! ExtractAuctions
                processAuctions(batchSpecification, pageNumber, firstAuctionUrl, listingPageAuctionLinks)

              case Valid(ListingPageAuctionLinks(_, auctionLinks)) if auctionLinks.isEmpty =>
                context.log.info(s"No auction links extracted from the listing page, going back to [idle] behavior")
                backToIdle

              case e =>
                context.log.error(s"Error while fetching the listing page auction links ($e)")
                backToIdle
            }

          case Invalid(Chain(LastListingPageReached)) =>
            context.log.info(s"Last listing page reached, no more auction links to process")
            backToIdle

          case Invalid(i) =>
            context.log.error(s"No more auction links to process ($i)")
            backToIdle
        }

      case (context, StopAuctionScraper) =>
        Behaviors.stopped

      case (context, cmd) =>
        context.log.error(s"Unexpected command $cmd received while in [processListingPage] behavior")
        backToIdle
    }

  private def processAuctions(batchSpecification: BatchSpecification,
                              pageNumber: Int,
                              firstAuctionUrl: Option[String],
                              listingPageAuctionLinks: ListingPageAuctionLinks,
                              auctions: List[Auction] = Nil): Behavior[AuctionScraperCommand] = {
    Behaviors.receivePartial {
      case (context, cmd: ProcessBatchSpecification) =>
        throw new IllegalStateException(s"Unexpected command $cmd received in state [processListingPage]")

      case (context, ExtractAuctions) =>
        listingPageAuctionLinks.auctionLinks match {
          case auctionLink :: remainingAuctionLinks =>
            validator.fetchAuction(auctionLink, batchSpecification.batchSpecificationID) match {
              case Valid(auction) =>
                context.log.info(s"Auction scraped successfully: ${auction.url}")
                timers.startSingleTimer(ExtractAuctions, randomDurationMs())
                processAuctions(
                  batchSpecification,
                  pageNumber,
                  firstAuctionUrl.orElse(Some(auctionLink.auctionUrl)),
                  listingPageAuctionLinks.copy(auctionLinks = remainingAuctionLinks),
                  auctions :+ auction)

              case Invalid(e) =>
                context.log.error(s"Error while scraping auction $auctionLink, moving to the next auction ($e)")
                timers.startSingleTimer(ExtractAuctions, randomDurationMs())
                processAuctions(
                  batchSpecification,
                  pageNumber,
                  firstAuctionUrl.orElse(Some(auctionLink.auctionUrl)),
                  listingPageAuctionLinks.copy(auctionLinks = remainingAuctionLinks),
                  auctions)
            }

          case _ =>
            context.log.info("No more auction links to process, creating a Batch, then moving to the next listing page")

            // Create a Batch with the extracted auctions
            batchManagerRef.ask(BatchManagerCommand.CreateBatch(batchSpecification, auctions, _))(3.seconds, context.system.scheduler)

            // Update the lastUrlVisited
            firstAuctionUrl
              .collect { case url if pageNumber == 1 =>
                batchManagerRef.ask(BatchManagerCommand.UpdateLastUrlVisited(batchSpecification.batchSpecificationID, url, _))(3.seconds, context.system.scheduler)
              }

            // Move the the next listing page
            timers.startSingleTimer(ExtractListingPageUrls, randomDurationMs())
            processListingPage(batchSpecification, pageNumber + 1, firstAuctionUrl)
        }

      case (context, StopAuctionScraper) =>
        Behaviors.stopped
    }
  }

  private def backToIdle(implicit timers: TimerScheduler[AuctionScraperCommand]): Behavior[AuctionScraperCommand] = {
    timers.startSingleTimer(AskNextBatchSpecification, 10.seconds)
    idle
  }

  def getPage(url: String): Try[JsoupBrowser.JsoupDocument] = Try(jsoupBrowser.get(url))
}