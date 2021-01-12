package com.fferrari.actor

import java.time.LocalDateTime

import com.fferrari.model.{BatchSpecification, Bid, Price}

sealed trait BatchAuction {
  val batchSpecification: BatchSpecification
  val externalId: String
  val url: String
  val title: String
  val isSold: Boolean
  val sellerNickname: String
  val sellerLocation: String
  val startPrice: Price
  val finalPrice: Option[Price]
  val startDate: LocalDateTime
  val endDate: Option[LocalDateTime]
  val thumbnailUrl: String
  val largeImageUrl: String
}

final case class BatchAuctionBid(batchSpecification: BatchSpecification,
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
                                 bids: List[Bid]) extends BatchAuction

final case class BatchAuctionFixedAuction(batchSpecification: BatchSpecification,
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
                                          bid: Option[Bid]) extends BatchAuction
