package com.fferrari.model

import java.time.LocalDateTime

import com.fferrari.model.Auction.AuctionType

final case class Auction(auctionType: AuctionType,
                         batchSpecification: BatchSpecification,
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
                         thumbnailUrl: String,
                         largeImageUrl: String,
                         bids: List[Bid])

object Auction {
  type AuctionType = Int
  val BID_TYPE_AUCTION = 1
  val FIXED_PRICE_TYPE_AUCTION = 2
}