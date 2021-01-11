package com.fferrari.model

case class BatchSpecification(id: String,
                              name: String,
                              description: String,
                              provider: String,
                              url: String,
                              intervalSeconds: Long,
                              paused: Boolean = false,
                              updatedAt: Long = 0L,
                              lastUrlScrapped: Option[String] = None
                             ) {
  def needsUpdate(relativeTo: java.time.Instant = java.time.Instant.now()): Boolean =
    (updatedAt + intervalSeconds) > relativeTo.getEpochSecond
}

object BatchSpecification {
  def apply(name: String,
            description: String,
            provider: String,
            url: String,
            intervalSeconds: Long) =
    new BatchSpecification(
      java.util.UUID.randomUUID().toString,
      name,
      description,
      provider,
      url,
      intervalSeconds,
      paused = false,
      updatedAt = 0L,
      lastUrlScrapped = None)
}
