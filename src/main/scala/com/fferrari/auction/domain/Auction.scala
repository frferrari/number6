package com.fferrari.auction.domain

import java.time.{Instant, LocalDateTime}
import java.util.UUID

import com.fferrari.batchmanager.domain.BatchSpecification

final case class Auction(auctionID: Auction.ID,
                         auctionType: Auction.AuctionType,
                         batchSpecificationID: BatchSpecification.ID,
                         externalId: String,
                         matchID: Option[UUID],
                         url: String,
                         title: String,
                         isSold: Boolean,
                         sellerNickname: String,
                         sellerLocation: String,
                         startPrice: Price,
                         finalPrice: Option[Price],
                         startDate: Instant,
                         endDate: Option[Instant],
                         thumbnailUrl: String,
                         largeImageUrl: String,
                         bids: List[Bid])

object Auction {
  type ID = UUID

  type AuctionType = Int
  val BID_TYPE_AUCTION = 1
  val FIXED_PRICE_TYPE_AUCTION = 2

  def generateID: ID = UUID.randomUUID()
}