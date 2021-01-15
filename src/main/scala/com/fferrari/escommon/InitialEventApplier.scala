package com.fferrari.escommon

trait InitialEventApplier[S, E <: EntityEvent[_]] {
  def apply(event: E): Option[S]
}
