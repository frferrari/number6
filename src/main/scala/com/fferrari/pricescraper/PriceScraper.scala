package com.fferrari.pricescraper

import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.persistence.query.Offset
import akka.projection.{ProjectionBehavior, ProjectionId}
import akka.projection.eventsourced.EventEnvelope
import akka.projection.eventsourced.scaladsl.EventSourcedProvider
import akka.projection.jdbc.scaladsl.JdbcProjection
import akka.projection.scaladsl.SourceProvider
import akka.util.Timeout
import com.fferrari.pricescraper.PriceScraper.Command
import com.fferrari.pricescraper.batch.application.{BatchListingProjectionHandler, BatchListingRepositoryImpl}
import com.fferrari.pricescraper.batch.domain.BatchEntity
import com.fferrari.pricescraper.batchmanager.application.BatchManagerActor
import com.fferrari.pricescraper.batchmanager.domain.BatchManagerEntity
import com.fferrari.pricescraper.service.{PriceScraperServiceImpl, ScalikeJdbcSession, ScalikeJdbcSetup}
import reactivemongo.api.{AsyncDriver, MongoConnection}

import scala.concurrent.duration._

object PriceScraper {

  sealed trait Command

  def main(args: Array[String]): Unit = {
    // val mongoUri = "mongodb://localhost:27017/number6?authMode=scram-sha1"
    import scala.concurrent.ExecutionContext.Implicits.global
    val mongoUri = "mongodb://number6:number6@localhost:27017/scraping"
    val driver = AsyncDriver()

    for {
      parsedUri <- MongoConnection.fromString(mongoUri)
      mongoConnection <- driver.connect(parsedUri)
      db <- mongoConnection.database("scraping")
    } yield ActorSystem(PriceScraper(db), "number6")
  }

  def apply(mongoDB: reactivemongo.api.DB): Behavior[Command] = {
    Behaviors.setup { context =>
      val tag = BatchEntity.allEventsTag

      ScalikeJdbcSetup.init(context.system)

      val sourceProvider: SourceProvider[Offset, EventEnvelope[BatchEntity.Event]] =
        EventSourcedProvider
          .eventsByTag[BatchEntity.Event](
            context.system,
            readJournalPluginId = CassandraReadJournal.Identifier,
            tag = tag)

      val batchListingProjection = JdbcProjection.exactlyOnce(
        projectionId = ProjectionId("BatchListingProjection", tag),
        sourceProvider,
        handler = () => new BatchListingProjectionHandler(tag, context.system, new BatchListingRepositoryImpl(mongoDB)),
        sessionFactory = () => new ScalikeJdbcSession()
      )(context.system)

      context.spawn(ProjectionBehavior(batchListingProjection), batchListingProjection.projectionId.id)

      new PriceScraper(context)
    }
  }
}

class PriceScraper(context: ActorContext[Command]) extends AbstractBehavior[Command](context) {
  val system = context.system

  implicit val askTimeout: Timeout = 3.seconds

  // Start the batch manager actor
  val batchManager: ActorRef[BatchManagerEntity.Command] =
    context.spawn(BatchManagerActor.apply, BatchManagerActor.actorName)
  batchManager.ask(BatchManagerEntity.Create)(askTimeout, context.system.scheduler)

  val grpcInterface = system.settings.config.getString("price-scraper-service.grpc.interface")
  val grpcPort = system.settings.config.getInt("price-scraper-service.grpc.port")
  val grpcService = new PriceScraperServiceImpl(batchManager, context)
  PriceScraperServer.start(grpcInterface, grpcPort, system, grpcService)

  override def onMessage(msg: Command): Behavior[Command] =
    this
}
