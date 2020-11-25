package com.fferrari.model

import java.time.LocalDateTime

sealed trait Auction {
  val id: String
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

object Auction {
  def apply(auctionType: AuctionType,
            id: String,
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
            bids: List[Bid]): Auction =
    auctionType match {
      case BidType =>
        AuctionBid(id, url, title, isSold, sellerNickname, sellerLocation, startPrice, finalPrice, startDate, endDate, largeImageUrl, bids)
      case FixedPriceType =>
        AuctionFixedPrice(id, url, title, isSold, sellerNickname, sellerLocation, startPrice, finalPrice, startDate, endDate, largeImageUrl, bids.headOption)
    }
}

final case class AuctionBid(id: String,
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
                            bids: List[Bid]) extends Auction

final case class AuctionFixedPrice(id: String,
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
                                   bid: Option[Bid]) extends Auction
