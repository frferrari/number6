package com.fferrari.pricescraper

import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.util.Timeout
import com.fferrari.pricescraper.PriceScraper.Command
import com.fferrari.pricescraper.batchmanager.application.BatchManagerActor
import com.fferrari.pricescraper.batchmanager.domain.BatchManagerEntity
import com.fferrari.pricescraper.service.PriceScraperServiceImpl

import scala.concurrent.duration._

object PriceScraper {

  sealed trait Command

  def main(args: Array[String]): Unit = {
    ActorSystem(PriceScraper(), "number6")
  }

  def apply(): Behavior[Command] =
    Behaviors.setup { context =>
      new PriceScraper(context)
    }
}

class PriceScraper(context: ActorContext[Command]) extends AbstractBehavior[Command](context) {
  val system = context.system

  implicit val askTimeout: Timeout = 3.seconds

  // Start the batch manager actor
  val batchManager: ActorRef[BatchManagerEntity.Command] = context.spawn(BatchManagerActor.apply, BatchManagerActor.actorName)
  batchManager.ask(BatchManagerEntity.Create)(askTimeout, context.system.scheduler)

  val grpcInterface = system.settings.config.getString("price-scraper-service.grpc.interface")
  val grpcPort = system.settings.config.getInt("price-scraper-service.grpc.port")
  val grpcService = new PriceScraperServiceImpl(batchManager, context)
  PriceScraperServer.start(grpcInterface, grpcPort, system, grpcService)

  override def onMessage(msg: Command): Behavior[Command] =
    this
}
