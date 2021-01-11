package com.fferrari.actor

import akka.Done
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, TimerScheduler}
import akka.actor.typed.{ActorRef, Behavior}
import akka.pattern.StatusReply
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect}
import com.fferrari.model.BatchSpecification

import scala.concurrent.duration._

object BatchScheduler {

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

  // TODO: how to remove this var?
  var batchIdx = 0

  def apply(): Behavior[Command] = Behaviors.setup { context =>
    // Allows to start rolling through batch specifications
    context.self ! ProcessNextBatchSpecification

    Behaviors.withTimers[Command] { timers =>
      EventSourcedBehavior.withEnforcedReplies[Command, Event, State](
        persistenceId = PersistenceId.ofUniqueId(actorName),
        emptyState = State(Nil),
        commandHandler = commandHandler(context, timers),
        eventHandler = eventHandler
      )
    }
  }

  def commandHandler(context: ActorContext[Command], timers: TimerScheduler[Command])
                    (state: State, command: Command): ReplyEffect[Event, State] =
    command match {
      case AddBatchSpecification(batchSpecification, replyTo) =>
        if (!state.batchSpecifications.exists(_.name == batchSpecification.name))
          Effect
            .persist(BatchSpecificationAdded(batchSpecification))
            .thenReply(replyTo)(_ => StatusReply.Ack)
        else
          Effect
            .reply(replyTo)(StatusReply.Error(s"BatchSpecification ${batchSpecification.name} already exists"))

      case ProcessNextBatchSpecification =>
        state.batchSpecifications.lift(batchIdx) match {
          case Some(batchSpecification) if batchSpecification.needsUpdate() =>
            context.log.info(s"Scrapping ${batchSpecification.url}")
            // TODO send a message to the proper scrapper
            Effect
              .persist(NextBatchSpecificationProcessed(batchSpecification))
              .thenNoReply()

          case _ =>
            timers.startSingleTimer(ProcessNextBatchSpecification, 60.seconds)
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

      case NextBatchSpecificationProcessed(batchSpecification) if state.batchSpecifications.exists(_.name == batchSpecification.name) =>
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
   * Moves to the next batch specification to process
   * @param state The state containing the batch specifications
   */
  def nextBatchIdx(state: State): Unit =
    if (batchIdx + 1 < state.batchSpecifications.size)
      batchIdx = batchIdx + 1
    else
      batchIdx = 0
}
