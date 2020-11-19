package com.fferrari.model

import java.util.Date

sealed trait Auction {
  val seller: Member
  val title: String
  val startPrice: Price
  val startedAt: Date
  val endedAt: Option[Date]
}

final case class AuctionBid(seller: Member,
                            title: String,
                            startPrice: Price,
                            endPrice: Option[Price],
                            startedAt: Date,
                            endedAt: Option[Date],
                            bids: List[Bid]) extends Auction

final case class AuctionFixed(seller: Member,
                              title: String,
                              startPrice: Price,
                              startedAt: Date,
                              endedAt: Option[Date],
                              bids: Bid) extends Auction
