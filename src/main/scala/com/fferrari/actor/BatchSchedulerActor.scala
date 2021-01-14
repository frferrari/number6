package com.fferrari.actor

import akka.Done
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, Routers, TimerScheduler}
import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import akka.pattern.StatusReply
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect}
import com.fferrari.actor.AuctionScraperProtocol.AuctionScraperCommand
import com.fferrari.model.BatchSpecification
import com.fferrari.validation.DelcampeValidator

import scala.concurrent.duration._

object BatchSchedulerActor {

  val actorName = "batch-scheduler"

  sealed trait Command
  case class AddBatchSpecification(batchSpecification: BatchSpecification, replyTo: ActorRef[StatusReply[Done]]) extends Command
  case object ProcessNextBatchSpecification extends Command
  case class UpdateLastUrl(batchSpecificationId: String, lastUrl: String, replyTo: ActorRef[StatusReply[Done]]) extends Command
  case class PauseBatchSpecification(batchSpecificationId: String, replyTo: ActorRef[StatusReply[Done]]) extends Command
  // case class PauseProvider(provider: String) extends Command

  sealed trait Event
  final case class BatchSpecificationAdded(batchSpecification: BatchSpecification) extends Event
  final case class NextBatchSpecificationProcessed(batchSpecification: BatchSpecification) extends Event
  final case class LastUrlUpdated(batchSpecificationId: String, lastUrl: String) extends Event
  final case class BatchSpecificationPaused(batchSpecificationId: String) extends Event

  final case class State(batchSpecifications: List[BatchSpecification])

  case class Scrapers(delcampeScraperRouter: ActorRef[AuctionScraperCommand])

  // TODO: how to remove this var?
  var batchIdx = 0

  def apply(): Behavior[Command] = Behaviors.setup { context =>
    context.log.info("Starting")

    // Start the scrapers actors
    val scrapers = spawnScrappers(context)

    // Keep an eye on the scrappers
    context.watch(scrapers.delcampeScraperRouter) // TODO implement handling of messages

    // Allows to start rolling through batch specifications
    context.self ! ProcessNextBatchSpecification

    Behaviors.withTimers[Command] { timers =>
      EventSourcedBehavior.withEnforcedReplies[Command, Event, State](
        persistenceId = PersistenceId.ofUniqueId(actorName),
        emptyState = State(Nil),
        commandHandler = commandHandler(context, timers, scrapers),
        eventHandler = eventHandler
      )
    }
  }

  def commandHandler(context: ActorContext[Command], timers: TimerScheduler[Command], scrapers: Scrapers)
                    (state: State, command: Command): ReplyEffect[Event, State] =
    command match {
      case AddBatchSpecification(batchSpecification, replyTo) =>
        if (!state.batchSpecifications.exists(_.name == batchSpecification.name)) {
          context.log.info(s"BatchSpecificationAdded $batchSpecification")

          Effect
            .persist(BatchSpecificationAdded(batchSpecification))
            .thenReply(replyTo)(_ => StatusReply.Ack)
        } else
          Effect
            .reply(replyTo)(StatusReply.Error(s"BatchSpecification ${batchSpecification.name} already exists"))

      case ProcessNextBatchSpecification =>
        context.log.info("ProcessNextBatchSpecification received")

        state.batchSpecifications.lift(batchIdx) match {
          case Some(batchSpecification) if batchSpecification.needsUpdate() =>
            Effect
              .persist(NextBatchSpecificationProcessed(batchSpecification))
              .thenRun(extractBachSpecification(scrapers, batchSpecification))
              .thenNoReply()

          case _ =>
            context.log.info(s"No BatchSpecification found, rescheduling for the next batch specification")
            timers.startSingleTimer(ProcessNextBatchSpecification, 30.seconds)
            nextBatchIdx(state)
            Effect.noReply
        }

      case UpdateLastUrl(batchSpecificationId, lastUrl, replyTo) =>
        state.batchSpecifications
          .find(_.id == batchSpecificationId)
          .fold[ReplyEffect[Event, State]](Effect.reply(replyTo)(StatusReply.Error(s"BatchSpecification unknown ${batchSpecificationId}, can't update the lastUrl"))) { _ =>
            Effect
              .persist(LastUrlUpdated(batchSpecificationId, lastUrl))
              .thenReply(replyTo)(_ => StatusReply.Ack)
          }

      case PauseBatchSpecification(batchSpecificationId, replyTo) =>
        state.batchSpecifications
        .find(_.id == batchSpecificationId)
        .fold[ReplyEffect[Event, State]](Effect.reply(replyTo)(StatusReply.Error(s"BatchSpecification unknown ${batchSpecificationId}, can't pause it"))) { _ =>
          Effect
            .persist(BatchSpecificationPaused(batchSpecificationId))
            .thenReply(replyTo)(_ => StatusReply.Ack)
        }
    }

  def eventHandler(state: State, event: Event): State =
    event match {
      case BatchSpecificationAdded(batchSpecification) =>
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
  def spawnScrappers(context: ActorContext[Command]): Scrapers = {
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
