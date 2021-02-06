package com.fferrari.pricescraper.batch.domain

import java.time.Instant
import java.util.UUID

import akka.Done
import akka.actor.typed.{ActorRef, Behavior}
import akka.pattern.StatusReply
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}
import com.fferrari.pricescraper.auction.domain.Auction
import com.fferrari.pricescraper.batchmanager.domain.BatchSpecification
import com.fferrari.pricescraper.common.Clock
import com.fferrari.pricescraper.common.Entity.{EntityCommand, EntityEvent}

object BatchEntity {

  val allEventsTag = "batch"

  // Commands
  sealed trait Command extends EntityCommand

  final case class Create(batchID: BatchEntity.ID,
                          batchSpecification: BatchSpecification,
                          auctions: List[Auction],
                          replyTo: ActorRef[StatusReply[Done]]) extends Command

  final case class MatchAuction(batchID: BatchEntity.ID,
                                auctionID: Auction.ID,
                                itemID: UUID,
                                replyTo: ActorRef[StatusReply[Done]]) extends Command

  final case class Stop(replyTo: ActorRef[StatusReply[Done]]) extends Command

  // Events
  sealed trait Event extends EntityEvent

  final case class Created(batchID: BatchEntity.ID,
                           timestamp: Instant,
                           batchSpecification: BatchSpecification,
                           auctions: List[Auction]) extends Event

  final case class AuctionMatched(batchID: BatchEntity.ID,
                                  auctionID: Auction.ID,
                                  timestamp: Instant,
                                  itemID: UUID) extends Event

  final case class Stopped(timestamp: Instant) extends Event

  type ID = UUID

  def generateID: UUID = UUID.randomUUID()

  type ReplyEffect = akka.persistence.typed.scaladsl.ReplyEffect[Event, Batch]

  sealed trait Batch {
    def applyCommand(cmd: Command): ReplyEffect

    def applyEvent(event: Event): Batch
  }

  case object EmptyBatch extends Batch {
    override def applyCommand(cmd: Command): ReplyEffect = cmd match {
      case Create(batchID, batchSpecification, auctions, replyTo) =>
        Effect
          .persist(Created(batchID, Clock.now, batchSpecification, auctions))
          .thenReply(replyTo)(_ => StatusReply.Ack)

      case _ =>
        Effect
          .unhandled
          .thenNoReply()
    }

    override def applyEvent(event: Event): Batch = event match {
      case Created(batchID, timestamp, batchSpecification, auctions) =>
        ActiveBatch(batchID, timestamp, batchSpecification, auctions)

      case _ =>
        throw new IllegalStateException(s"Unexpected event $event in state [EmptyBatch]")
    }
  }

  case class ActiveBatch(batchID: BatchEntity.ID,
                         timestamp: Instant,
                         batchSpecification: BatchSpecification,
                         auctions: List[Auction]) extends Batch {
    override def applyCommand(cmd: Command): ReplyEffect = cmd match {
      case MatchAuction(batchID, auctionID, itemID, replyTo) =>
        if (auctions.indexWhere(_.auctionID == auctionID) >= 0) {
          Effect
            .persist(AuctionMatched(batchID, auctionID, Clock.now, itemID))
            .thenReply(replyTo)(_ => StatusReply.Ack)
        } else {
          Effect
            .none
            .thenReply(replyTo)(_ => StatusReply.error(s"Unknown auctionId ${auctionID}, MatchAuction command rejected"))
        }

      case Stop(replyTo) =>
        Effect
          .persist(Stopped(Clock.now))
          .thenReply(replyTo)(_ => StatusReply.Ack)

      case _ =>
        Effect
          .unhandled
          .thenNoReply()
    }

    override def applyEvent(event: Event): Batch = event match {
      case AuctionMatched(batchID, auctionID, timestamp, itemID) =>
        val idx = auctions.indexWhere(_.auctionID == auctionID)
        val newAuction: Auction = auctions(idx).copy(matchID = Some(itemID))
        copy(auctions = auctions.updated(idx, newAuction))

      case Stopped(timestamp) =>
        InactiveBatch

      case _ =>
        throw new IllegalStateException(s"Unexpected event $event in state [ActiveBatch]")
    }
  }

  case object InactiveBatch extends Batch {
    override def applyCommand(cmd: Command): ReplyEffect = cmd match {
      case _ =>
        throw new IllegalStateException(s"Unexpected command $cmd in state [InactiveBatch]")
    }

    override def applyEvent(event: Event): Batch = event match {
      case _ =>
        throw new IllegalStateException(s"Unexpected event $event in state [InactiveBatch]")
    }
  }

  def apply(persistenceId: PersistenceId): Behavior[Command] = {
    EventSourcedBehavior.withEnforcedReplies[Command, Event, Batch](
      persistenceId,
      EmptyBatch,
      (state, cmd) => state.applyCommand(cmd),
      (state, event) => state.applyEvent(event))
      .withTagger(_ => Set(allEventsTag))
  }
}
