package com.fferrari.common

import java.time.Instant

object Clock {
  def now: Instant = java.time.Instant.now()
}
