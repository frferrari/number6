package com.fferrari.batchmanager.domain

import java.time.Instant
import java.util.UUID

case class BatchSpecification(batchSpecificationID: BatchSpecification.ID,
                              name: String,
                              description: String,
                              url: String,
                              provider: String,
                              intervalSeconds: Long,
                              updatedAt: Instant,
                              paused: Boolean,
                              lastUrlVisited: Option[String]) {
  def needsUpdate(now: java.time.Instant = java.time.Instant.now()): Boolean =
    now.isBefore(updatedAt.plusSeconds(intervalSeconds)) && !paused
}

object BatchSpecification {
  type ID = UUID
}
