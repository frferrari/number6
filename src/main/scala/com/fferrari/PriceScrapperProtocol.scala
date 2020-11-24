package com.fferrari

import com.fferrari.model.Website

object PriceScrapperProtocol {

  sealed trait PriceScrapperCommand
  final case class ScrapWebsite(website: Website) extends PriceScrapperCommand
  final case object ExtractUrls extends PriceScrapperCommand
  final case class ExtractAuctionUrls(websiteInfo: WebsiteInfo, auctionUrls: Seq[String], pageNumber: Int = 1) extends PriceScrapperCommand
  final case class ExtractAuctions(auctionUrls: Seq[String]) extends PriceScrapperCommand
  final case class CreateAuction(id: String,
                                 url: String,
                                 imageUrl: String,
                                 sellingType: String,
                                 title: String,
                                 currency: String,
                                 priceSold: BigDecimal,
                                 bidCount: Option[Int])
  final case class ScrapAuction(url: String) extends PriceScrapperCommand

  case class WebsiteInfo(website: Website, url: String, lastScrappedUrl: Option[String])
}
