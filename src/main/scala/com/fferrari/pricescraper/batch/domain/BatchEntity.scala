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

  // Commands
  sealed trait Command extends EntityCommand

  final case class Create(batchID: BatchEntity.ID,
                          batchSpecificationID: BatchSpecification.ID,
                          auctions: List[Auction],
                          replyTo: ActorRef[StatusReply[Done]]) extends Command

  final case class MatchAuction(auctionID: Auction.ID,
                                matchID: UUID,
                                replyTo: ActorRef[StatusReply[Done]]) extends Command

  final case class Stop(replyTo: ActorRef[StatusReply[Done]]) extends Command

  // Events
  sealed trait Event extends EntityEvent

  final case class Created(batchID: BatchEntity.ID,
                           timestamp: Instant,
                           batchSpecificationID: BatchSpecification.ID,
                           auctions: List[Auction]) extends Event

  final case class AuctionMatched(auctionID: Auction.ID,
                                  timestamp: Instant,
                                  matchID: UUID) extends Event

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
      case Create(batchID, batchSpecificationID, auctions, replyTo) =>
        Effect
          .persist(Created(batchID, Clock.now, batchSpecificationID, auctions))
          .thenReply(replyTo)(_ => StatusReply.Ack)

      case _ =>
        Effect
          .unhandled
          .thenNoReply()
    }

    override def applyEvent(event: Event): Batch = event match {
      case Created(batchID, timestamp, batchSpecificationID, auctions) =>
        ActiveBatch(batchID, batchSpecificationID, auctions)

      case _ =>
        throw new IllegalStateException(s"Unexpected event $event in state [EmptyBatch]")
    }
  }

  case class ActiveBatch(batchID: BatchEntity.ID, batchSpecificationID: BatchSpecification.ID, auctions: List[Auction]) extends Batch {
    override def applyCommand(cmd: Command): ReplyEffect = cmd match {
      case MatchAuction(auctionID, matchID, replyTo) =>
        Effect
          .persist(AuctionMatched(auctionID, Clock.now, matchID))
          .thenReply(replyTo)(_ => StatusReply.Ack)

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
      case AuctionMatched(auctionID, timestamp, matchID) =>
        val idx = auctions.indexWhere(_.auctionID == auctionID)
        if (idx >= 0) {
          val newAuction: Auction = auctions(idx).copy(matchID = Some(matchID))
          copy(auctions = auctions.updated(idx, newAuction))
        } else throw new IllegalArgumentException(s"Trying to match an unknown auction ($auctionID)")

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
  }
}
