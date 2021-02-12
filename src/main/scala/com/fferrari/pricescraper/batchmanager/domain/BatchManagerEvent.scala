package com.fferrari.pricescraper.batchmanager.domain

import java.time.Instant

import com.fferrari.pricescraper.auction.domain.Auction
import com.fferrari.pricescraper.batch.domain.Batch.BatchID
import com.fferrari.pricescraper.batchmanager.domain.BatchManager.BatchManagerID
import com.fferrari.pricescraper.batchmanager.domain.BatchSpecification.BatchSpecificationID

sealed trait BatchManagerEvent {
  def timestamp: Instant
}

object BatchManagerEvent {

  final case class BatchManagerCreated(batchManagerID: BatchManagerID, timestamp: Instant) extends BatchManagerEvent

  final case class BatchCreated(batchID: BatchID,
                                batchSpecification: BatchSpecification,
                                auctions: List[Auction],
                                timestamp: Instant) extends BatchManagerEvent

  final case class BatchSpecificationAdded(batchSpecificationID: BatchSpecificationID,
                                           name: String,
                                           description: String,
                                           url: String,
                                           provider: String,
                                           intervalSeconds: Long,
                                           timestamp: Instant) extends BatchManagerEvent

  final case class LastUrlVisitedUpdated(batchSpecificationID: BatchSpecificationID,
                                         lastUrlVisited: String,
                                         timestamp: Instant) extends BatchManagerEvent

  final case class LastVisitedTimeRefreshed(batchSpecificationID: BatchSpecificationID,
                                            timestamp: Instant) extends BatchManagerEvent

  final case class BatchSpecificationPaused(batchSpecificationID: BatchSpecificationID,
                                            timestamp: Instant) extends BatchManagerEvent

  final case class BatchSpecificationReleased(batchSpecificationID: BatchSpecificationID,
                                              timestamp: Instant) extends BatchManagerEvent

  final case class ProviderPaused(provider: String,
                                  timestamp: Instant) extends BatchManagerEvent

  final case class ProviderReleased(provider: String,
                                    timestamp: Instant) extends BatchManagerEvent

  final case class NextBatchSpecificationProvided(batchSpecification: BatchSpecification,
                                                  timestamp: Instant) extends BatchManagerEvent

  final case class NothingToProceedTo(timestamp: Instant) extends BatchManagerEvent

}
