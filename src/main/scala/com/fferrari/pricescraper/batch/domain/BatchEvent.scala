package com.fferrari.pricescraper.batch.domain

import java.time.Instant
import java.util.UUID

import com.fferrari.pricescraper.auction.domain.Auction
import com.fferrari.pricescraper.auction.domain.Auction.AuctionID
import com.fferrari.pricescraper.batch.domain.Batch.BatchID
import com.fferrari.pricescraper.batchmanager.domain.BatchSpecification

sealed trait BatchEvent {
  def timestamp: Instant
}

object BatchEvent {
  final case class BatchCreated private[domain](batchID: BatchID,
                                                batchSpecification: BatchSpecification,
                                                auctions: List[Auction],
                                                timestamp: Instant) extends BatchEvent

  final case class AuctionMatched private[domain](batchID: BatchID,
                                                  auctionID: AuctionID,
                                                  itemID: UUID,
                                                  timestamp: Instant) extends BatchEvent
}
