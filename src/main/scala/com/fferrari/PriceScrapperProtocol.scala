package com.fferrari

object PriceScrapperProtocol {
  sealed trait Website
  final case object Delcampe extends Website

  sealed trait PriceScrapperCommand
  final case class ScrapWebsite(website: Website) extends PriceScrapperCommand
  final case class ScrapUrls(urls: Array[String], currentUrlIdx: Int = 0) extends PriceScrapperCommand
  final case class CreateAuction(id: String,
                                 url: String,
                                 imageUrl: String,
                                 sellingType: String,
                                 title: String,
                                 currency: String,
                                 priceSold: BigDecimal,
                                 bidCount: Option[Int])
}
