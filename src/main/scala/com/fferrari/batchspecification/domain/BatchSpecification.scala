package com.fferrari.batchspecification.domain

import java.time.Instant
import java.util.UUID

case class BatchSpecification(id: BatchSpecification.ID,
                              name: String,
                              description: String,
                              provider: Provider,
                              url: String,
                              intervalSeconds: Long,
                              updatedAt: Instant,
                              paused: Boolean = false,
                              lastUrlScrapped: Option[String] = None)

object BatchSpecification {
  type ID = UUID
}