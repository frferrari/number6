package com.fferrari.escommon

import java.time.Instant

trait EntityEvent[ID] {
  def entityID: ID
  def timestamp: Instant
}
