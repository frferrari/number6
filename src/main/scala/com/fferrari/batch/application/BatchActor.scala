package com.fferrari.batch.application

import java.util.UUID

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.persistence.typed.PersistenceId
import com.fferrari.batch.domain.BatchEntity

object BatchActor {
  def apply(batchID: UUID): Behavior[BatchEntity.Command] =
    Behaviors.setup { context =>
      context.log.info("Starting")

      BatchEntity(PersistenceId.ofUniqueId(s"batch-$batchID"))
    }
}
