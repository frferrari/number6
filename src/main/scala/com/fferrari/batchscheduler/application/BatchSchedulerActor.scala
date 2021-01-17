package com.fferrari.batchscheduler.application

import akka.actor.typed.scaladsl.{ActorContext, Behaviors, Routers}
import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import akka.persistence.typed.PersistenceId
import com.fferrari.auction.application.{AuctionScraperActor, DelcampeValidator}
import com.fferrari.batchscheduler.domain.BatchSchedulerEntity
import com.fferrari.batchscheduler.domain.BatchSchedulerEntity.Command

object BatchSchedulerActor {

  val actorName = "batch-scheduler"

  case class Scrapers(delcampeScraperRouter: ActorRef[AuctionScraperActor.Command])

  def apply: Behavior[Command] = Behaviors.setup { implicit context =>
    Behaviors.withTimers { implicit timers =>
      context.log.info("Starting")

      // Start the scrapers actors
      val scrapers = spawnScrappers(context)

      BatchSchedulerEntity(PersistenceId.ofUniqueId(actorName), scrapers)
    }
  }

  /**
   * Allows to spawn actors that will scrap auctions from the different web sites that will be our sources of prices.
   * We use routers to create actors so that we have a pool of actors for each provider (web sites)
   * @param context An actor context to allow to spawn actors
   * @return A class containing the actor ref of the different routers for the different providers
   */
  def spawnScrappers(context: ActorContext[Command]): Scrapers = {
    // Start a pool of DELCAMPE auction scraper
    val pool =
      Routers
        .pool(poolSize = 5) {
          Behaviors
            .supervise(AuctionScraperActor(() => new DelcampeValidator)).onFailure[Exception](SupervisorStrategy.restart)
        }
    val delcampeScraperRouter: ActorRef[AuctionScraperActor.Command] = context.spawn(pool, AuctionScraperActor.actorName)

    Scrapers(delcampeScraperRouter)
  }
}
