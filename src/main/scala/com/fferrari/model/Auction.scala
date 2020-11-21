package com.fferrari.model

import java.util.Date

sealed trait Auction {
  val id: String
  val title: String
  val isSold: Boolean
  val sellerNickname: String
  val sellerLocation: String
  val startPrice: Price
  val finalPrice: Option[Price]
  val startDate: Date
  val endDate: Option[Date]
  val largeImageUrl: String
}

final case class AuctionBid(id: String,
                            title: String,
                            isSold: Boolean,
                            sellerNickname: String,
                            sellerLocation: String,
                            startPrice: Price,
                            finalPrice: Option[Price],
                            startDate: Date,
                            endDate: Option[Date],
                            largeImageUrl: String,
                            bids: List[Bid]) extends Auction

final case class AuctionFixedPrice(id: String,
                                   title: String,
                                   isSold: Boolean,
                                   sellerNickname: String,
                                   sellerLocation: String,
                                   startPrice: Price,
                                   finalPrice: Option[Price],
                                   startDate: Date,
                                   endDate: Option[Date],
                                   largeImageUrl: String,
                                   bids: Bid) extends Auction
