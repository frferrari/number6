package com.fferrari.pricescraper.batch.domain

import java.time.Instant
import java.util.UUID

import com.fferrari.pricescraper.auction.domain.Auction
import com.fferrari.pricescraper.batch.domain.BatchCommand.{CreateBatch, MatchAuction}
import com.fferrari.pricescraper.batch.domain.BatchEvent.{AuctionMatched, BatchCreated}
import com.fferrari.pricescraper.batchmanager.domain.BatchSpecification

import scala.util.{Failure, Success, Try}

sealed trait Batch {
  def processCommand(command: BatchCommand): Try[BatchEvent]

  def applyEvent(event: BatchEvent): Try[Batch]
}

object Batch {
  type BatchID = UUID
  val tag = "batch"

  final case object EmptyBatch extends Batch {
    override def processCommand(command: BatchCommand): Try[BatchEvent] = command match {
      case cmd: CreateBatch =>
        Success(cmd.toBatchCreated)

      case _ =>
        Failure(new IllegalStateException(s"Unexpected command $command in state EmptyBatch"))
    }

    override def applyEvent(event: BatchEvent): Try[Batch] = event match {
      case BatchCreated(batchID, batchSpecification, auctions, timestamp) =>
        Success(ActiveBatch(batchID, batchSpecification, auctions, timestamp))

      case _ =>
        Failure(new IllegalStateException(s"Unexpected Event $event in state EmptyBatch"))
    }
  }

  final case class ActiveBatch(batchID: BatchID,
                               batchSpecification: BatchSpecification,
                               auctions: List[Auction],
                               timestamp: Instant) extends Batch {
    override def processCommand(command: BatchCommand): Try[BatchEvent] = command match {
      case cmd: MatchAuction =>
        if (auctions.indexWhere(_.auctionID == cmd.auctionID) >= 0)
          Success(cmd.toAuctionMatched)
        else
          Failure(new IllegalArgumentException(s"Unable to MatchAuction ${cmd.auctionID} against Batch $batchID in state ActiveBatch"))

      case _ =>
        Failure(new IllegalStateException(s"Unexpected command $command in state ActiveBatch"))
    }

    override def applyEvent(event: BatchEvent): Try[Batch] = event match {
      case AuctionMatched(batchID, auctionID, itemID, timestamp) =>
        val idx = auctions.indexWhere(_.auctionID == auctionID)
        if (idx >= 0) {
          val newAuction: Auction = auctions(idx).copy(matchID = Some(itemID))
          Success(copy(auctions = auctions.updated(idx, newAuction)))
        } else {
          Failure(new IllegalStateException(s"Unable to AuctionMatched $auctionID against Batch $batchID in state ActiveBatch"))
        }

      case _ =>
        Failure(new IllegalStateException(s"Unexpected event $event in state ActiveBatch"))
    }
  }
}