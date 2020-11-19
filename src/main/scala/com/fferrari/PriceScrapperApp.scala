package com.fferrari

import akka.actor.typed.{ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import com.fferrari.PriceScrapperProtocol.{PriceScrapperCommand, ScrapWebsite}

object PriceScrapper {
  def apply(): Behavior[PriceScrapperCommand] = {
    Behaviors.receivePartial {
      case (context, PriceScrapperProtocol.ScrapWebsite(Delcampe)) =>
        val child = context.spawn(AuctionScrapperActor(), "price-scrapper-delcampe")
        Behaviors.same
    }
  }
}

object PriceScrapperApp extends App {
  val auctionScrapper = ActorSystem(AuctionScrapperActor(), "AuctionScrapper")
  auctionScrapper ! ScrapWebsite(Delcampe)
}
