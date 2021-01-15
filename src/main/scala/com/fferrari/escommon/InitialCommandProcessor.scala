package com.fferrari.escommon

trait InitialCommandProcessor[C[R] <: EntityCommand[_, _, R], E <: EntityEvent[_]] {
  def process(command: C[_]): List[E]
}
