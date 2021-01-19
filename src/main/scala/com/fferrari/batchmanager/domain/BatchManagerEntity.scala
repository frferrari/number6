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
import com.fferrari.auction.application.AuctionScraperActor
import com.fferrari.auction.domain.Auction
import com.fferrari.batch.application.BatchActor
import com.fferrari.batch.domain.BatchEntity
import com.fferrari.batchmanager.application.BatchManagerActor.Scrapers
import com.fferrari.common.Clock
import com.fferrari.common.Entity.{EntityCommand, EntityEvent}

import scala.concurrent.duration._

object BatchManagerEntity {
  // Command
  sealed trait Command extends EntityCommand
  final case class Create(replyTo: ActorRef[StatusReply[Done]]) extends Command
  final case class AddBatchSpecification(name: String, description: String, url: String, provider: String, intervalSeconds: Long, replyTo: ActorRef[StatusReply[Done]]) extends Command
  final case class ProcessNextBatchSpecification(provider: String, replyTo: ActorRef[AuctionScraperActor.Command]) extends Command
  final case class UpdateLastUrlVisited(batchSpecificationID: BatchSpecification.ID, lastUrlVisited: String, replyTo: ActorRef[StatusReply[Done]]) extends Command
  final case class PauseBatchSpecification(batchSpecificationID: BatchSpecification.ID, replyTo: ActorRef[StatusReply[Done]]) extends Command

  final case class CreateBatch(batchSpecificationID: BatchSpecification.ID,
                               auctions: List[Auction],
                               replyTo: ActorRef[StatusReply[Done]]) extends Command

  // Event
  sealed trait Event extends EntityEvent
  final case class Created(timestamp: Instant) extends Event
  final case class BatchCreated(batchID: BatchEntity.ID,
                                timestamp: Instant,
                                batchSpecificationID: BatchSpecification.ID,
                                auctions: List[Auction]) extends Event
  final case class BatchSpecificationAdded(batchSpecificationID: BatchSpecification.ID, timestamp: Instant, name: String, description: String, url: String, provider: String, intervalSeconds: Long) extends Event
  final case class LastUrlVisitedUpdated(batchSpecificationID: BatchSpecification.ID, timestamp: Instant, lastUrl: String) extends Event
  final case class BatchSpecificationPaused(batchSpecificationID: BatchSpecification.ID, timestamp: Instant) extends Event

  type ID = UUID

  type ReplyEffect = akka.persistence.typed.scaladsl.ReplyEffect[Event, BatchManager]

  sealed trait BatchManager {
    def applyCommand(cmd: Command, scrapers: Scrapers)(implicit context: ActorContext[Command]): ReplyEffect

    def applyEvent(event: Event)(implicit context: ActorContext[Command]): BatchManager
  }

  case object EmptyBatchManager extends BatchManager {
    override def applyCommand(cmd: Command, scrapers: Scrapers)(implicit context: ActorContext[Command]): ReplyEffect = cmd match {
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
        ActiveBatchManager(Nil)

      case _ =>
        throw new IllegalStateException(s"Unexpected event $event in state [EmptyBatchManager]")
    }
  }

  case class ActiveBatchManager(batchSpecifications: List[BatchSpecification]) extends BatchManager {
    override def applyCommand(cmd: Command, scrapers: Scrapers)(implicit context: ActorContext[Command]): ReplyEffect = cmd match {
      case CreateBatch(batchSpecificationID, auctions, replyTo) =>
        Effect
          .persist(BatchCreated(BatchEntity.generateID, Clock.now, batchSpecificationID, auctions))
          .thenReply(replyTo)(_ => StatusReply.Ack)

      case AddBatchSpecification(name, description, url, provider, intervalSeconds, replyTo) =>
        if (!batchSpecifications.exists(_.name == name)) {
          Effect
            .persist(BatchSpecificationAdded(UUID.randomUUID(), Clock.now, name, description, url, provider, intervalSeconds))
            .thenReply(replyTo)(_ => StatusReply.Ack)
        } else
          Effect
            .reply(replyTo)(StatusReply.error(s"A batchSpecification with the name $name already exists"))

      case UpdateLastUrlVisited(batchSpecificationID, lastUrlVisited, replyTo) =>
        batchSpecifications.find(_.batchSpecificationID == batchSpecificationID) match {
          case Some(_) =>
            Effect
              .persist(LastUrlVisitedUpdated(batchSpecificationID, Clock.now, lastUrlVisited))
              .thenReply(replyTo)(_ => StatusReply.Ack)

          case None =>
            context.log.error(s"Trying to update the last url visited for an unknown batch specification ID $batchSpecificationID (command)")
            Effect
              .reply(replyTo)(StatusReply.error(s"Trying to update the last url visited for an unknown batch specification ID $batchSpecificationID (command)"))
        }

      case PauseBatchSpecification(batchSpecificationID, replyTo) =>
        batchSpecifications.find(_.batchSpecificationID == batchSpecificationID) match {
          case Some(_) =>
            Effect
              .persist(BatchSpecificationPaused(batchSpecificationID, Clock.now))
              .thenReply(replyTo)(_ => StatusReply.Ack)
          case None =>
            Effect.reply(replyTo)(StatusReply.error(s"Trying to pause an unknown bach specification ID $batchSpecificationID (command)"))
        }

      case ProcessNextBatchSpecification(provider, replyTo) =>
        context.log.info(s"Received ProcessNextBatchSpecification($provider)")
        batchSpecifications
          .filter(_.provider == provider)
          .filter(_.needsUpdate())
          .sortBy(_.updatedAt)
          .headOption match {
            case Some(batchSpecification) =>
              Effect
                .none
                .thenReply(replyTo)((state: BatchManager) => AuctionScraperActor.ProceedToBatchSpecification(batchSpecification))
            case None =>
              Effect
                .none
                .thenNoReply()
          }

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

      case BatchSpecificationAdded(batchSpecificationID, timestamp, name, description, url, provider, intervalSeconds) =>
        val batchSpecification = BatchSpecification(batchSpecificationID, name, description, url, provider, intervalSeconds, Clock.now.minusSeconds(intervalSeconds), false, None)
        copy(batchSpecifications = batchSpecifications :+ batchSpecification)

      case LastUrlVisitedUpdated(batchSpecificationID, timestamp, lastUrlVisited) =>
        context.log.info(s"LastUrlVisitedUpdated to $lastUrlVisited for batch $batchSpecificationID")
        val idx = batchSpecifications.indexWhere(_.batchSpecificationID == batchSpecificationID)
        if (idx >= 0) {
          val newBatchSpecification = batchSpecifications(idx).copy(lastUrlVisited = Some(lastUrlVisited), updatedAt = Clock.now)
          copy(batchSpecifications = batchSpecifications.updated(idx, newBatchSpecification))
        } else throw new IllegalStateException(s"Trying to update the last url visited for an unknown batch specification ID $batchSpecificationID (event)")

      case BatchSpecificationPaused(batchSpecificationID, timestamp) =>
        context.log.info(s"BatchSpecificationPaused for batch $batchSpecificationID")
        val idx = batchSpecifications.indexWhere(_.batchSpecificationID == batchSpecificationID)
        if (idx >= 0) {
          val newBatchSpecification = batchSpecifications(idx).copy(paused = true)
          copy(batchSpecifications = batchSpecifications.updated(idx, newBatchSpecification))
        } else throw new IllegalStateException(s"Trying to pause an unknown bach specification ID $batchSpecificationID (event)")

      case _ =>
        throw new IllegalStateException(s"Unexpected event $event in state [ActiveBatchManager]")
    }
  }

  def apply(persistenceId: PersistenceId, scrapers: Scrapers)(implicit context: ActorContext[Command]): Behavior[Command] = {
    EventSourcedBehavior.withEnforcedReplies[Command, Event, BatchManager](
      persistenceId,
      EmptyBatchManager,
      (state, cmd) => state.applyCommand(cmd, scrapers: Scrapers),
      (state, event) => state.applyEvent(event))
  }
}
