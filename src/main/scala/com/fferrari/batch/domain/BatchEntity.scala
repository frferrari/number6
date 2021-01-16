package com.fferrari.batch.domain

import java.util.UUID

import akka.Done
import akka.actor.typed.{ActorRef, Behavior}
import akka.pattern.StatusReply
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}
import com.fferrari.auction.domain.Auction
import com.fferrari.batchspecification.domain.BatchSpecificationEntity

object BatchEntity {
  sealed trait BatchCommand
  final case class CreateBatch(batchID: BatchEntity.ID,
                               batchSpecificationID: BatchSpecificationEntity.ID,
                               auctions: List[Auction],
                               replyTo: ActorRef[StatusReply[Done]]) extends BatchCommand
  final case class MatchAuction(auctionID: Auction.ID,
                                matchID: UUID,
                                replyTo: ActorRef[StatusReply[Done]]) extends BatchCommand

  sealed trait BatchEvent
  final case class BatchCreated(batchID: BatchEntity.ID,
                                batchSpecificationID: BatchSpecificationEntity.ID,
                                auctions: List[Auction]) extends BatchEvent
  final case class AuctionMatched(auctionID: Auction.ID,
                                  matchID: UUID) extends BatchEvent

  type ID = UUID

  type ReplyEffect = akka.persistence.typed.scaladsl.ReplyEffect[BatchEvent, Batch]

  sealed trait Batch {
    def applyCommand(cmd: BatchCommand): ReplyEffect

    def applyEvent(event: BatchEvent): Batch
  }

  case object EmptyBatch extends Batch {
    override def applyCommand(cmd: BatchCommand): ReplyEffect = cmd match {
      case CreateBatch(batchID, batchSpecificationID, auctions, replyTo) =>
        Effect
          .persist(BatchCreated(batchID, batchSpecificationID, auctions))
          .thenReply(replyTo)(_ => StatusReply.Ack)

      case _ =>
        Effect
          .unhandled
          .thenNoReply()
    }

    override def applyEvent(event: BatchEvent): Batch = event match {
      case BatchCreated(batchID, batchSpecificationID, auctions) =>
        ActiveBatch(batchID, batchSpecificationID, auctions)

      case _ =>
        throw new IllegalStateException(s"Unexpected event $event in state [EmptyBatch]")
    }
  }

  case class ActiveBatch(batchID: BatchEntity.ID, batchSpecificationID: BatchSpecificationEntity.ID, auctions: List[Auction]) extends Batch {
    override def applyCommand(cmd: BatchCommand): ReplyEffect = cmd match {
      case MatchAuction(auctionID, matchID, replyTo) =>
        Effect
          .persist(AuctionMatched(auctionID, matchID))
          .thenReply(replyTo)(_ => StatusReply.Ack)

      case _ =>
        Effect
          .unhandled
          .thenNoReply()
    }

    override def applyEvent(event: BatchEvent): Batch = event match {
      case AuctionMatched(auctionID, matchID) =>
        val idx = auctions.indexWhere(_.auctionID == auctionID)
        if (idx >= 0) {
          val newAuction: Auction = auctions(idx).copy(matchID)
          copy(auctions = auctions.updated(idx, newAuction))
        } else throw new IllegalArgumentException(s"Trying to match an unknown auction ($auctionID)")

      case _ =>
        throw new IllegalStateException(s"Unexpected event $event in state [ActiveBatch]")
    }
  }

  def apply(persistenceId: PersistenceId): Behavior[BatchCommand] = {
    EventSourcedBehavior.withEnforcedReplies[BatchCommand, BatchEvent, Batch](
      persistenceId,
      EmptyBatch,
      (state, cmd) => state.applyCommand(cmd),
      (state, event) => state.applyEvent(event))
  }
}
