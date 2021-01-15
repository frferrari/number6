package com.fferrari.batchmanager.domain.es

import java.time.Instant

import com.fferrari.auction.domain.Auction

sealed trait BatchManagerEvent {
  def batchId: String
  def batchSpecificationId: String
}

object BatchManagerEvent {
  final case class BatchCreated(batchId: String,
                                batchSpecificationId: String,
                                firstUrlScraped: String,
                                auctions: List[Auction],
                                createdAt: Instant) extends BatchManagerEvent
}

