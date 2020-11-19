package com.fferrari

import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import com.fferrari.PriceScrapperProtocol.{Delcampe, PriceScrapperCommand, ScrapWebsite}

object PriceScrapper {
  def apply(): Behavior[PriceScrapperCommand] = {
    Behaviors.receivePartial {
      case (context, PriceScrapperProtocol.ScrapWebsite(PriceScrapperProtocol.Delcampe)) =>
        val child = context.spawn(PriceScrapperDelcampe(), "price-scrapper-delcampe")
        Behaviors.same
    }
  }
}

object PriceScrapperApp extends App {
  val priceScrapper = ActorSystem(AuctionScrapper(), "PriceScrapper")
  priceScrapper ! ScrapWebsite(Delcampe)
}
