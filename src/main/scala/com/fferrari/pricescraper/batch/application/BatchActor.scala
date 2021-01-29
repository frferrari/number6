package com.fferrari.pricescraper.batch.application

import java.util.UUID

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.persistence.typed.PersistenceId
import com.fferrari.pricescraper.batch.domain
import com.fferrari.pricescraper.batch.domain.BatchEntity

object BatchActor {
  def apply(batchID: UUID): Behavior[BatchEntity.Command] =
    Behaviors.setup { context =>
      context.log.info("Starting")

      domain.BatchEntity(PersistenceId.ofUniqueId(s"batch-$batchID"))
    }
}
