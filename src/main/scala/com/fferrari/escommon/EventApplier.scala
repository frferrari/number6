package com.fferrari.escommon

trait EventApplier[S, E <: EntityEvent[_]] {
  def apply(state: S, event: E): S
}
