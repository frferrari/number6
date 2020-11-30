package com.fferrari

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior}
import com.fferrari.actor.AuctionScrapperProtocol.{PriceScrapperCommand, ScrapWebsite}
import com.fferrari.actor.{AuctionScrapperActor, AuctionScrapperProtocol}
import com.fferrari.model.Delcampe

object PriceScrapper {
  def apply(): Behavior[PriceScrapperCommand] = {
    Behaviors.receivePartial {
      case (context, AuctionScrapperProtocol.ScrapWebsite(Delcampe)) =>
        val child = context.spawn(AuctionScrapperActor(), "price-scrapper-delcampe")
        Behaviors.same
    }
  }
}

object PriceScrapperApp extends App {
  val auctionScrapper = ActorSystem(AuctionScrapperActor(), "AuctionScrapper")
  auctionScrapper ! ScrapWebsite(Delcampe)
}
