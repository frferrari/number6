package com.fferrari.escommon

trait EntityCommand[ID, S, R] {
  def entityID: ID
  def initializedReply: S => R
  def uninitializedReply: R
}
