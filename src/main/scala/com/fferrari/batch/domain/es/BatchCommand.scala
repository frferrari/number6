package com.fferrari.batch.domain.es

import com.fferrari.auction.domain.Auction
import com.fferrari.batch.domain.es.BatchEvent.BatchCreated
import com.fferrari.util.Clock

sealed trait BatchCommand

object BatchCommand {
  final case class CreateBatch(batchId: String, batchSpecificationId: String, auctions: List[Auction]) extends BatchCommand {
    def toBatchCreated: BatchCreated = {
      BatchCreated(batchId, batchSpecificationId, auctions, Clock.now)
    }
  }
}
