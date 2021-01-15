package com.fferrari.util

import java.time.Instant

object Clock {
  def now: Instant = java.time.Instant.now()
}
