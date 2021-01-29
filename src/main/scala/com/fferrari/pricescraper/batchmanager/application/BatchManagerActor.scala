package com.fferrari.pricescraper.batchmanager.application

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.persistence.typed.PersistenceId
import com.fferrari.pricescraper.batchmanager.domain.BatchManagerEntity.Command
import com.fferrari.pricescraper.auction.application.{AuctionScraperActor, DelcampeValidator}
import com.fferrari.pricescraper.batchmanager.domain
import com.fferrari.pricescraper.batchmanager.domain.BatchManagerEntity

object BatchManagerActor {
  val actorName = "batch-manager"

  val BatchManagerServiceKey = ServiceKey[Command]("batchManagerService")

  case class Scrapers(delcampeScraperRouter: ActorRef[AuctionScraperActor.Command])

  def apply: Behavior[BatchManagerEntity.Command] =
    Behaviors.setup { implicit context =>
      context.log.info("Starting")

      // Register with the Receptionist
      context.system.receptionist ! Receptionist.Register(BatchManagerServiceKey, context.self)

      // Start the scrapers actors
      val scrapers = spawnScrappers(context, context.self.ref)

      // Start the scraper process
      scrapers.delcampeScraperRouter ! AuctionScraperActor.Start

      domain.BatchManagerEntity(PersistenceId.ofUniqueId(actorName), scrapers)
    }

  /**
   * Allows to spawn actors that will scrap auctions from the different web sites that will be our sources of prices.
   * We use routers to create actors so that we have a pool of actors for each provider (web sites)
   * @param context An actor context to allow to spawn actors
   * @return A class containing the actor ref of the different routers for the different providers
   */
  def spawnScrappers(context: ActorContext[Command], batchManagerRef: ActorRef[BatchManagerEntity.Command]): Scrapers = {
    val delcampeScraperRouter: ActorRef[AuctionScraperActor.Command] =
      context.spawn(AuctionScraperActor(() => new DelcampeValidator, batchManagerRef), AuctionScraperActor.actorName)

    Scrapers(delcampeScraperRouter)
  }
}
