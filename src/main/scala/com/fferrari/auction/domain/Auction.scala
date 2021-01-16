package com.fferrari.auction.domain

import java.time.LocalDateTime
import java.util.UUID

import com.fferrari.batchspecification.domain.BatchSpecificationEntity
import com.fferrari.model.{Bid, Price}

final case class Auction(auctionID: Auction.ID,
                         auctionType: Auction.AuctionType,
                         batchSpecificationID: BatchSpecificationEntity.ID,
                         externalId: String,
                         matchID: UUID,
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
  type ID = UUID

  type AuctionType = Int
  val BID_TYPE_AUCTION = 1
  val FIXED_PRICE_TYPE_AUCTION = 2
}