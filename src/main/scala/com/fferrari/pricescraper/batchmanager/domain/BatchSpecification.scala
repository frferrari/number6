package com.fferrari.pricescraper.batchmanager.domain

import java.time.Instant
import java.util.UUID

case class BatchSpecification(batchSpecificationID: BatchSpecification.BatchSpecificationID,
                              name: String,
                              description: String,
                              listingPageUrl: String,
                              provider: String,
                              intervalSeconds: Long,
                              updatedAt: Instant,
                              paused: Boolean,
                              lastUrlVisited: Option[String],
                              familyId: Option[UUID] = None,
                              countryId: Option[UUID] = None,
                              topicId: Option[UUID] = None,
                              startYear: Option[Int] = None,
                              endYear: Option[Int] = None,
                              conditionId: Option[UUID] = None) {
  def needsUpdate(now: java.time.Instant = java.time.Instant.now()): Boolean =
    now.isAfter(updatedAt.plusSeconds(intervalSeconds)) && !paused
}

object BatchSpecification {
  type BatchSpecificationID = UUID
}
