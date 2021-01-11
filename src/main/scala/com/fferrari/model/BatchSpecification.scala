package com.fferrari.model

case class BatchSpecification(name: String,
                              description: String,
                              provider: String,
                              url: String,
                              intervalSeconds: Long,
                              lastScrappedTimestamp: Long = 0L,
                              lastUrlScrapped: Option[String] = None
                             ) {
  def needsUpdate(relativeTo: java.time.Instant = java.time.Instant.now()): Boolean =
    (lastScrappedTimestamp + intervalSeconds) > relativeTo.getEpochSecond
}
