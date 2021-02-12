package com.fferrari.pricescraper.batchmanager.application

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.pattern.StatusReply
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect}
import com.fferrari.pricescraper.auction.application.{AuctionScraperActor, DelcampeValidator}
import com.fferrari.pricescraper.auction.domain.AuctionScraperCommand
import com.fferrari.pricescraper.auction.domain.AuctionScraperCommand.StartAuctionScraper
import com.fferrari.pricescraper.batch.application
import com.fferrari.pricescraper.batch.domain.BatchCommand
import com.fferrari.pricescraper.batchmanager.domain.BatchManager.EmptyBatchManager
import com.fferrari.pricescraper.batchmanager.domain.BatchManagerCommand._
import com.fferrari.pricescraper.batchmanager.domain.BatchManagerEvent._
import com.fferrari.pricescraper.batchmanager.domain._

import scala.concurrent.duration._
import scala.util.{Failure, Success}

object BatchManagerActor {
  val actorName = "batch-manager"

  val BatchManagerServiceKey = ServiceKey[BatchManagerCommand]("batchManagerService")

  case class Scrapers(delcampeScraperRouter: ActorRef[AuctionScraperCommand])

  def apply: Behavior[BatchManagerCommand] =
    Behaviors.setup { implicit context =>
      context.log.info("Starting")

      // Register with the Receptionist
      context.system.receptionist ! Receptionist.Register(BatchManagerServiceKey, context.self)

      // Start the scrapers actors
      val scrapers = spawnScrappers(context, context.self)

      // Start the scraper process
      scrapers.delcampeScraperRouter ! StartAuctionScraper

      EventSourcedBehavior.withEnforcedReplies[BatchManagerCommand, BatchManagerEvent, BatchManager](
        PersistenceId.ofUniqueId(actorName),
        EmptyBatchManager,
        (state, cmd) => processCommand(state, cmd),
        (state, event) => applyEvent(state, event)
      ).withTagger(_ => Set("batchManager"))
    }

  /**
   * Allows to spawn actors that will scrap auctions from the different web sites that will be our sources of prices.
   * We use routers to create actors so that we have a pool of actors for each provider (web sites)
   * @param context An actor context to allow to spawn actors
   * @return A class containing the actor ref of the different routers for the different providers
   */
  def spawnScrappers(context: ActorContext[BatchManagerCommand], batchManagerRef: ActorRef[BatchManagerCommand]): Scrapers = {
    val delcampeScraperRouter: ActorRef[AuctionScraperCommand] =
      context.spawn(AuctionScraperActor(() => new DelcampeValidator, batchManagerRef), AuctionScraperActor.actorName)

    Scrapers(delcampeScraperRouter)
  }

  def processCommand(state: BatchManager, command: BatchManagerCommand)
                    (implicit context: ActorContext[BatchManagerCommand]): ReplyEffect[BatchManagerEvent, BatchManager] =
    (command, state.processCommand(command)) match {
      case (cmd: AddBatchSpecification, Success(event: BatchSpecificationAdded)) =>
        Effect
          .persist(event)
          .thenReply(cmd.replyTo)(_ => StatusReply.success(event.batchSpecificationID))

      case (cmd: AddBatchSpecification, Failure(f)) =>
        context.log.error(f.getMessage)
        Effect
          .none
          .thenReply(cmd.replyTo)(_ => StatusReply.error(f.getMessage))

      case (cmd: ProvideNextBatchSpecification, Success(event: NextBatchSpecificationProvided)) =>
        Effect
          .persist(event)
          .thenReply(cmd.replyTo)(_ => StatusReply.success(AuctionScraperCommand.ProceedToBatchSpecification(event.batchSpecification)))

      case (cmd: ProvideNextBatchSpecification, Success(event: NothingToProceedTo)) =>
        Effect
          .none
          .thenNoReply() // The sender will timeout and this is what we want

      case (cmd: ProvideNextBatchSpecification, Failure(f)) =>
        context.log.error(f.getMessage)
        Effect
          .none
          .thenReply(cmd.replyTo)(_ => StatusReply.error(f.getMessage))

      case (cmd: BatchManagerCommandSimpleReply, Success(event)) =>
        Effect
          .persist(event)
          .thenReply(cmd.replyTo)(_ => StatusReply.Ack)

      case (cmd: BatchManagerCommandSimpleReply, Failure(f)) =>
        context.log.error(f.getMessage)
        Effect
          .none
          .thenReply(cmd.replyTo)(_ => StatusReply.error(f.getMessage))

      case (cmd, event) =>
        context.log.error(s"Unexpected event $event produced for command $cmd")
        Effect
          .unhandled
          .thenNoReply()
    }

  def applyEvent(state: BatchManager, event: BatchManagerEvent)
                (implicit context: ActorContext[BatchManagerCommand]): BatchManager = {
    (event, state.applyEvent(event)) match {
      case (evt: BatchCreated, Success(newState)) =>
        val batchActor = context.spawn(application.BatchActor(evt.batchID), s"batch-${evt.batchID}")
        batchActor.ask(BatchCommand.CreateBatch(evt.batchSpecification, evt.auctions, _))(3.seconds, context.system.scheduler)
        newState

      case (_, Success(newState)) =>
        newState

      case (_, Failure(f)) =>
        context.log.error(f.getMessage)
        throw f
    }
  }
}
