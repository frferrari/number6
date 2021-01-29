/*
package com.fferrari.pricescraper.dashboard.application

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.persistence.jdbc.query.scaladsl.JdbcReadJournal
import akka.persistence.query.Offset
import akka.projection.eventsourced.EventEnvelope
import akka.projection.eventsourced.scaladsl.EventSourcedProvider
import akka.projection.scaladsl.SourceProvider
import com.fferrari.batchmanager.domain.BatchManagerEntity

object DashboardActor {
  val actorName = "dashboard"

  def apply: Behavior[Command] = Behaviors.setup { context =>
    context.log.info("Starting")

    val sourceProvider: SourceProvider[Offset, EventEnvelope[BatchManagerEntity.Event]] =
      EventSourcedProvider
        .eventsByTag[BatchManagerEntity.Event](
          context.system,
          readJournalPluginId = JdbcReadJournal.Identifier,
          tag = BatchManagerEntity.allEventsTag
        )
  }
}
 */
