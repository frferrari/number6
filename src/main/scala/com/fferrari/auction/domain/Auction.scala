package com.fferrari.auction.domain

import java.time.Instant
import java.util.UUID

import com.fferrari.auction.domain.Auction.MatchStatus
import com.fferrari.batchmanager.domain.BatchSpecification

final case class Auction(auctionID: Auction.ID,
                         auctionType: Auction.AuctionType,
                         batchSpecificationID: BatchSpecification.ID,
                         externalID: String,
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
                         bids: List[Bid],
                         integrationStatus: MatchStatus)

object Auction {
  type ID = UUID

  type AuctionType = Int
  val AuctionBidType = 0
  val AuctionFixedPriceType = 1

  type MatchStatus = Int
  val NotChecked = 0
  val CheckedRejected = 1
  val CheckedMatched = 2

  def generateID: ID = UUID.randomUUID()
}