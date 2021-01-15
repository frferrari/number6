package com.fferrari.batch.domain

import java.util.UUID

import com.fferrari.auction.domain.Auction
import com.fferrari.batchspecification.domain.BatchSpecification
import com.fferrari.escommon.{Clock, EventApplier, InitialCommandProcessor, InitialEventApplier}

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
}
