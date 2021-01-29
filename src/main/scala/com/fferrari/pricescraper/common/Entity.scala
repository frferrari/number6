package com.fferrari.pricescraper.common

import java.time.Instant

object Entity {
  trait EntityCommand
  trait EntityEvent {
    def timestamp: Instant
  }
}
