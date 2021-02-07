package com.fferrari.pricescraper.batch.application

import akka.Done
import akka.actor.typed.ActorSystem
import akka.projection.eventsourced.EventEnvelope
import akka.projection.jdbc.scaladsl.JdbcHandler
import com.fferrari.pricescraper.batch.domain.BatchEntity
import com.fferrari.pricescraper.batch.dto.BatchDTO
import com.fferrari.pricescraper.service.ScalikeJdbcSession
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}

class BatchListingProjectionHandler(tag: String,
                                    system: ActorSystem[_],
                                    repo: BatchListingRepository)
  extends JdbcHandler[EventEnvelope[BatchEntity.Event], ScalikeJdbcSession] {

  private val log = LoggerFactory.getLogger(getClass)
  private implicit val ec: ExecutionContext = system.executionContext

  override def process(session: ScalikeJdbcSession, eventEnvelope: EventEnvelope[BatchEntity.Event]): Unit =
    eventEnvelope.event match {
      case BatchEntity.Created(batchID, timestamp, batchSpecification, auctions) =>
        log.info("Persisting BatchEntity.Created in Projection ***************************")
        session.withConnection { conn =>
          repo.create(BatchDTO(batchID, timestamp, batchSpecification, auctions)).map(_ => Done)
        }

      case _ =>
        log.info(s"Unhandled event ${eventEnvelope.event} ***************************")
        ()
    }
}
