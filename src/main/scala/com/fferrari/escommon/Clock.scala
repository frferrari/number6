package com.fferrari.escommon

import java.time.Instant

object Clock {
  def now: Instant = java.time.Instant.now()
}
