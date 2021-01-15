package com.fferrari.batch.domain.es

import java.time.Instant

import com.fferrari.auction.domain.Auction

sealed trait BatchEvent

object BatchEvent {
  final case class BatchCreated(batchId: String,
                                batchSpecificationId: String,
                                auctions: List[Auction],
                                createdAt: Instant
                               ) extends BatchEvent
}
