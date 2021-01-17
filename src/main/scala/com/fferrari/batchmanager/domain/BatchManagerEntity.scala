package com.fferrari.batchmanager.domain

import java.time.Instant
import java.util.UUID

import akka.Done
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.{ActorRef, Behavior}
import akka.pattern.StatusReply
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}
import com.fferrari.auction.domain.Auction
import com.fferrari.batch.application.BatchActor
import com.fferrari.batch.domain.BatchEntity
import com.fferrari.common.Clock
import com.fferrari.common.Entity.{EntityCommand, EntityEvent}

import scala.concurrent.duration._

object BatchManagerEntity {
  sealed trait Command extends EntityCommand
  final case class Create(replyTo: ActorRef[StatusReply[Done]]) extends Command
  final case class CreateBatch(batchID: BatchEntity.ID,
                               batchSpecificationID: BatchSpecification.ID,
                               auctions: List[Auction],
                               replyTo: ActorRef[StatusReply[Done]]) extends Command

  sealed trait Event extends EntityEvent
  final case class Created(timestamp: Instant) extends Event
  final case class BatchCreated(batchID: BatchEntity.ID,
                                timestamp: Instant,
                                batchSpecificationID: BatchSpecification.ID,
                                auctions: List[Auction]) extends Event

  type ID = UUID

  type ReplyEffect = akka.persistence.typed.scaladsl.ReplyEffect[Event, BatchManager]

  sealed trait BatchManager {
    def applyCommand(cmd: Command): ReplyEffect

    def applyEvent(event: Event)(implicit context: ActorContext[Command]): BatchManager
  }

  case object EmptyBatchManager extends BatchManager {
    override def applyCommand(cmd: Command): ReplyEffect = cmd match {
      case Create(replyTo) =>
        Effect
          .persist(Created(Clock.now))
          .thenReply(replyTo)(_ => StatusReply.Ack)

      case _ =>
        Effect
          .unhandled
          .thenNoReply()
    }

    override def applyEvent(event: Event)(implicit context: ActorContext[Command]): BatchManager = event match {
      case Created(timestamp) =>
        ActiveBatchManager

      case _ =>
        throw new IllegalStateException(s"Unexpected event $event in state [EmptyBatchManager]")
    }
  }

  case object ActiveBatchManager extends BatchManager {
    override def applyCommand(cmd: Command): ReplyEffect = cmd match {
      case CreateBatch(batchID, batchSpecificationID, auctions, replyTo) =>
        Effect
          .persist(BatchCreated(batchID, Clock.now, batchSpecificationID, auctions))
          .thenReply(replyTo)(_ => StatusReply.Ack)

      case _ =>
        Effect
          .unhandled
          .thenNoReply()
    }

    override def applyEvent(event: Event)(implicit context: ActorContext[Command]): BatchManager = event match {
      case BatchCreated(batchID, timestamp, batchSpecificationID, auctions) =>
        // TODO Maybe not the best way to handle the child actor creation ???
        val batchActor = context.spawn(BatchActor(batchID), s"batch-${batchID}")
        batchActor.ask(BatchEntity.Create(batchID, batchSpecificationID, auctions, _))(3.seconds, context.system.scheduler)
        this

      case _ =>
        throw new IllegalStateException(s"Unexpected event $event in state [ActiveBatchManager]")
    }
  }

  def apply(persistenceId: PersistenceId)(implicit context: ActorContext[Command]): Behavior[Command] = {
    EventSourcedBehavior.withEnforcedReplies[Command, Event, BatchManager](
      persistenceId,
      EmptyBatchManager,
      (state, cmd) => state.applyCommand(cmd),
      (state, event) => state.applyEvent(event))
  }
}
