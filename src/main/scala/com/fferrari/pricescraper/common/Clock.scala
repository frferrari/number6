package com.fferrari.pricescraper.common

import java.time.Instant

object Clock {
  def now: Instant = java.time.Instant.now()
}
