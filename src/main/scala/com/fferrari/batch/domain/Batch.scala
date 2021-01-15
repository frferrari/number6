package com.fferrari.batch.domain

import java.util.UUID

import com.fferrari.auction.domain.Auction
import com.fferrari.batchspecification.domain.BatchSpecification
import com.fferrari.escommon.{Clock, CommandProcessor, EventApplier, InitialCommandProcessor, InitialEventApplier}

case class Batch(id: Batch.ID,
                 batchSpecificationId: BatchSpecification.ID,
                 auctions: List[Auction])

object Batch {
  type ID = UUID

  implicit def initialCommandProcessor: InitialCommandProcessor[BatchCommand, BatchEvent] = {
    case CreateBatch(batchID, batchSpecificationID, auctions) =>
      List(BatchCreated(batchID, Clock.now, batchSpecificationID, auctions))
    case otherCommand =>
      Nil
  }

  implicit val initialEventApplier: InitialEventApplier[Batch, BatchEvent] = {
    case BatchCreated(batchID, _, batchSpecificationID, auctions) =>
      Some(Batch(batchID, batchSpecificationID, auctions))
    case otherEvent =>
      None
  }

  implicit val eventApplier: EventApplier[Batch, BatchEvent] = (batch, event) =>
    event match {
      case _: BatchCreated =>
        batch
    }

  implicit def commandProcessor: CommandProcessor[Batch, BatchCommand, BatchEvent] = (state, command) => command match {
    case MatchAuction(batchID, auctionID, matchID) =>
      state.auctions
        .find(_.id == auctionID)
        .map(_ => List(AuctionMatched(batchID, Clock.now, auctionID, matchID)))
        .getOrElse(Nil)

    case _: BatchCreated => Nil // TODO Why this event here ???
  }

  implicit val eventApplier: EventApplier[Batch, BatchEvent] = (batch, event) =>
    event match {
      case AuctionMatched(entityID, timestamp, auctionID, matchID) =>
        val idx = batch.auctions.indexWhere(_.id == auctionID)
        if (idx >= 0) {
          val newAuction: Auction = batch.auctions(idx).copy(matchID)
          batch.copy(auctions = batch.auctions.updated(idx, newAuction))
        } else batch

      case _: BatchCreated => batch
    }
}
