package com.fferrari.batchscheduler.application

import akka.Done
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, Routers, TimerScheduler}
import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import akka.pattern.StatusReply
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect}
import com.fferrari.auction.application.AuctionScraperActor
import com.fferrari.auction.domain.es.AuctionScraperProtocol
import com.fferrari.auction.domain.es.AuctionScraperProtocol.AuctionScraperCommand
import com.fferrari.auction.validator.DelcampeValidator
import com.fferrari.batchscheduler.domain.BatchSpecification
import com.fferrari.batchscheduler.domain.es.{BatchSchedulerCommand, BatchSchedulerEvent}
import com.fferrari.batchscheduler.domain.es.BatchSchedulerCommand.{AddBatchScheduler, ProcessBatchScheduler$}
import com.fferrari.batchscheduler.domain.es.BatchSchedulerEvent.BatchSchedulerAdded

import scala.concurrent.duration._

object BatchSchedulerActor {

  val actorName = "batch-specification"

  final case class State(batchSpecifications: List[BatchSpecification])

  val BatchSpecificationServiceKey = ServiceKey[BatchSchedulerCommand]("batchSpecificationService")

  case class Scrapers(delcampeScraperRouter: ActorRef[AuctionScraperCommand])

  // TODO: how to remove this var?
  var batchIdx = 0

  def apply(): Behavior[BatchSchedulerCommand] = Behaviors.setup { context =>
    context.log.info("Starting")

    // Register with the Receptionist
    context.system.receptionist ! Receptionist.Register(BatchSpecificationServiceKey, context.self)

    // Start the scrapers actors
    val scrapers = spawnScrappers(context)

    // Allows to start rolling through batch specifications
    context.self ! ProcessBatchScheduler$

    Behaviors.withTimers[BatchSchedulerCommand] { timers =>
      EventSourcedBehavior.withEnforcedReplies[BatchSchedulerCommand, BatchSchedulerEvent, State](
        persistenceId = PersistenceId.ofUniqueId(actorName),
        emptyState = State(Nil),
        commandHandler = commandHandler(context, timers, scrapers),
        eventHandler = eventHandler
      )
    }
  }

  def commandHandler(context: ActorContext[BatchSchedulerCommand], timers: TimerScheduler[BatchSchedulerCommand], scrapers: Scrapers)
                    (state: State, command: BatchSchedulerCommand): ReplyEffect[BatchSchedulerEvent, State] =
    command match {
      case cmd: AddBatchScheduler =>
        if (!state.batchSpecifications.exists(_.name == cmd.name)) {
          context.log.info(s"BatchSpecificationAdded $cmd")

          Effect
            .persist(cmd.toBatchSpecificationAdded)
            .thenNoReply()
        }
        /*
        else
          Effect
            .reply(replyTo)(StatusReply.Error(s"BatchSpecification ${batchSpecification.name} already exists"))
         */

      case cmd@ProcessBatchScheduler$ =>
        context.log.info("ProcessBatchSpecification received")

        state.batchSpecifications.lift(batchIdx) match {
          case Some(batchSpecification@BatchSpecification(id, name, description, provider, url, intervalSeconds, paused, updatedAt, lastUrlScraped)) if batchSpecification.needsUpdate() =>
            Effect
              .persist(cmd.toBatchSpecificationProcessed(id, name, description, provider, url, intervalSeconds))
              .thenRun(extractBachSpecification(scrapers, batchSpecification))
              .thenNoReply()

          case _ =>
            context.log.info(s"No BatchSpecification found, rescheduling for the next batch specification")
            timers.startSingleTimer(ProcessBatchScheduler$, 30.seconds)
            nextBatchIdx(state)
            Effect.noReply
        }

        /*
      case UpdateLastUrl(batchSpecificationId, lastUrlScraped) =>
        context.log.info("UpdateLastUrl received")
        state.batchSpecifications
          .find(_.id == batchSpecificationId)
          .fold[ReplyEffect[BatchSpecificationEvent, State]](Effect.noReply) { _ =>
            context.log.info(s"Updating the last url of batchId $batchSpecificationId to $lastUrlScraped")
            Effect
              .persist(LastUrlUpdated(batchSpecificationId, lastUrlScraped))
              .thenNoReply()
          }

      case PauseBatchSpecification(batchSpecificationId, replyTo) =>
        state.batchSpecifications
          .find(_.id == batchSpecificationId)
          .fold[ReplyEffect[BatchSpecificationEvent, State]](Effect.reply(replyTo)(StatusReply.Error(s"BatchSpecification unknown ${batchSpecificationId}, can't pause it"))) { _ =>
            Effect
              .persist(BatchSpecificationPaused(batchSpecificationId))
              .thenReply(replyTo)(_ => StatusReply.Ack)
          }
         */
    }

  def eventHandler(state: State, event: BatchSchedulerEvent): State =
    event match {
      case BatchSchedulerAdded(batchSpecification) =>
        state.copy(batchSpecifications = state.batchSpecifications :+ batchSpecification)

      case NextBatchSpecificationProcessed(batchSpecification) =>
        nextBatchIdx(state)
        val idx = state.batchSpecifications.indexWhere(_.name == batchSpecification.name)
        if (idx >= 0) {
          val newBatchSpecification = batchSpecification.copy(updatedAt = java.time.Instant.now().getEpochSecond)
          state.copy(batchSpecifications = state.batchSpecifications.updated(idx, newBatchSpecification))
        } else
          state

      case LastUrlUpdated(batchSpecificationId, lastUrl) =>
        val idx = state.batchSpecifications.indexWhere(_.id == batchSpecificationId)
        if (idx >= 0) {
          val newBatchSpecification = state.batchSpecifications(idx).copy(lastUrl)
          state.copy(batchSpecifications = state.batchSpecifications.updated(idx, newBatchSpecification))
        } else
          state

      case BatchSpecificationPaused(batchSpecificationId) =>
        val idx = state.batchSpecifications.indexWhere(_.id == batchSpecificationId)
        if (idx >= 0) {
          val newBatchSpecification = state.batchSpecifications(idx).copy(paused = true)
          state.copy(batchSpecifications = state.batchSpecifications.updated(idx, newBatchSpecification))
        } else
          state
    }

  /**
   * Tell the appropriate scraper to extract the auctions for the given batch specification
   * @param scrapers An object containing the different available scrapers
   * @param batchSpecification A batch specification for which to extract the auctions
   * @param newState The new state
   */
  def extractBachSpecification(scrapers: Scrapers, batchSpecification: BatchSpecification)(newState: State): Unit = {
    scrapers.delcampeScraperRouter ! AuctionScraperProtocol.ExtractListingPageUrls(batchSpecification)
  }

  /**
   * Moves to the next batch specification to process
   * @param state The state containing the batch specifications
   */
  def nextBatchIdx(state: State): Unit =
    if (batchIdx + 1 < state.batchSpecifications.size)
      batchIdx = batchIdx + 1
    else
      batchIdx = 0

  /**
   * Allows to spawn actors that will scrap auctions from the different web sites that will be our sources of prices.
   * We use routers to create actors so that we have a pool of actors for each provider (web sites)
   * @param context An actor context to allow to spawn actors
   * @return A class containing the actor ref of the different routers for the different providers
   */
  def spawnScrappers(context: ActorContext[BatchSchedulerCommand]): Scrapers = {
    // Start a pool of DELCAMPE auction scrapper
    val pool =
      Routers
        .pool(poolSize = 5) {
          Behaviors
            .supervise(AuctionScraperActor(() => new DelcampeValidator)).onFailure[Exception](SupervisorStrategy.restart)
        }
    val delcampeScraperRouter: ActorRef[AuctionScraperCommand] = context.spawn(pool, AuctionScraperActor.actorName)

    Scrapers(delcampeScraperRouter)
  }
}
