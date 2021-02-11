package com.fferrari.pricescraper.auction.domain

import java.time.Instant
import java.util.UUID

import com.fferrari.pricescraper.auction.domain.Auction.MatchStatus
import com.fferrari.pricescraper.batchmanager.domain.BatchSpecification

final case class Auction(auctionID: Auction.AuctionID,
                         auctionType: Auction.AuctionType,
                         batchSpecificationID: BatchSpecification.BatchSpecificationID,
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
  type AuctionID = UUID

  type AuctionType = Int
  val AuctionBidType = 0
  val AuctionFixedPriceType = 1

  type MatchStatus = Int
  val NotChecked = 0
  val CheckedRejected = 1
  val CheckedMatched = 2

  def generateID: AuctionID = UUID.randomUUID()
}