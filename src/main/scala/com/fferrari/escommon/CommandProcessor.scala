package com.fferrari.escommon

trait CommandProcessor[S, C[R] <: EntityCommand[_, _, R], E <: EntityEvent[_]] {
  def process(state: S, command: C[_]): List[E]
}
