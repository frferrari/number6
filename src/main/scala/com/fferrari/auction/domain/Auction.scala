package com.fferrari.auction.domain

import java.time.LocalDateTime
import java.util.UUID

case class Auction(id: Auction.ID,
                   auctionType: Auction.AuctionType,
                   externalId: String,
                   matchId: UUID, // todo fix later
                   url: String,
                   title: String,
                   isSold: Boolean,
                   sellerNickname: String,
                   sellerLocation: String,
                   startPrice: Price,
                   finalPrice: Option[Price],
                   startDate: LocalDateTime,
                   endDate: Option[LocalDateTime],
                   thumbnailUrl: String,
                   largeImageUrl: String,
                   bids: List[Bid])

object Auction {
  sealed trait AuctionType
  case object Bid extends AuctionType
  case object Fixed extends AuctionType

  type ID = UUID
}