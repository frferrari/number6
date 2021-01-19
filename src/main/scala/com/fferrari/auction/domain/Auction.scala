package com.fferrari.auction.domain

import java.time.Instant
import java.util.UUID

import com.fferrari.auction.domain.Auction.MatchStatus
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
                         bids: List[Bid],
                         integrationStatus: MatchStatus)

object Auction {
  type ID = UUID

  type AuctionType = Int
  val AuctionBidType = 1
  val AuctionFixedType = 2

  type MatchStatus = Int
  val NotChecked = 1
  val CheckedRejected = 2
  val CheckedHasToBeMatched = 3
  val CheckedMatched = 4

  def generateID: ID = UUID.randomUUID()
}