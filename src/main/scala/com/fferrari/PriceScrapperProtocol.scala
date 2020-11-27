package com.fferrari

import com.fferrari.model.{AuctionType, Bid, BidType, FixedPriceType, Price, Website}

import java.time.LocalDateTime

object PriceScrapperProtocol {

  sealed trait PriceScrapperCommand
  final case class ScrapWebsite(website: Website) extends PriceScrapperCommand
  final case object ExtractUrls extends PriceScrapperCommand
  final case object ExtractAuctionUrls extends PriceScrapperCommand
  final case class ExtractAuctions(auctionUrls: Seq[String]) extends PriceScrapperCommand

  sealed trait CreateAuction extends PriceScrapperCommand {
    val externalId: String
    val url: String
    val title: String
    val isSold: Boolean
    val sellerNickname: String
    val sellerLocation: String
    val startPrice: Price
    val finalPrice: Option[Price]
    val startDate: LocalDateTime
    val endDate: Option[LocalDateTime]
    val largeImageUrl: String
  }

  object CreateAuction {
    def apply(auctionType: AuctionType,
              externalId: String,
              url: String,
              title: String,
              isSold: Boolean,
              sellerNickname: String,
              sellerLocation: String,
              startPrice: Price,
              finalPrice: Option[Price],
              startDate: LocalDateTime,
              endDate: Option[LocalDateTime],
              largeImageUrl: String,
              bids: List[Bid]): CreateAuction =
      auctionType match {
        case BidType =>
          CreateAuctionBid(
            externalId,
            url,
            title,
            isSold,
            sellerNickname,
            sellerLocation,
            startPrice,
            finalPrice,
            startDate,
            endDate,
            largeImageUrl,
            bids)
        case FixedPriceType =>
          CreateAuctionFixedPrice(
            externalId,
            url,
            title,
            isSold,
            sellerNickname,
            sellerLocation,
            startPrice,
            finalPrice,
            startDate,
            endDate,
            largeImageUrl,
            bids.headOption)
      }
  }

  final case class CreateAuctionBid(externalId: String,
                                    url: String,
                                    title: String,
                                    isSold: Boolean,
                                    sellerNickname: String,
                                    sellerLocation: String,
                                    startPrice: Price,
                                    finalPrice: Option[Price],
                                    startDate: LocalDateTime,
                                    endDate: Option[LocalDateTime],
                                    largeImageUrl: String,
                                    bids: List[Bid]) extends CreateAuction

  final case class CreateAuctionFixedPrice(externalId: String,
                                           url: String,
                                           title: String,
                                           isSold: Boolean,
                                           sellerNickname: String,
                                           sellerLocation: String,
                                           startPrice: Price,
                                           finalPrice: Option[Price],
                                           startDate: LocalDateTime,
                                           endDate: Option[LocalDateTime],
                                           largeImageUrl: String,
                                           bid: Option[Bid]) extends CreateAuction

  final case class ScrapAuction(url: String) extends PriceScrapperCommand

  case class WebsiteInfo(website: Website, url: String, lastScrappedUrl: Option[String])
}
