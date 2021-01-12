package com.fferrari.actor

import java.time.LocalDateTime

import akka.actor.typed.receptionist.Receptionist
import com.fferrari.model._

object AuctionScraperProtocol {

  sealed trait AuctionScraperCommand
  final case object LookupBatchManager extends AuctionScraperCommand
  final case object ExtractUrls extends AuctionScraperCommand
  final case class ExtractListingPageUrls(batchSpecification: BatchSpecification, pageNumber: Int = 1) extends AuctionScraperCommand
  final case class ExtractAuctions(batchSpecification: BatchSpecification, urlBatch: Batch, pageNumber: Int) extends AuctionScraperCommand
  final case class ListingResponse(listing: Receptionist.Listing) extends AuctionScraperCommand

  sealed trait CreateAuction extends AuctionScraperCommand {
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

  object CreateAuction {
    def apply(auctionType: AuctionType,
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
              bids: List[Bid]): CreateAuction =
      auctionType match {
        case BidType =>
          CreateAuctionBid(
            batchSpecification,
            externalId,
            url,
            title,
            isSold,
            sellerNickname,
            sellerLocation,
            startPrice,
            finalPrice,
            startDate,
            endDate,
            thumbnailUrl,
            largeImageUrl,
            bids)
        case FixedPriceType =>
          CreateAuctionFixedAuction(
            batchSpecification,
            externalId,
            url,
            title,
            isSold,
            sellerNickname,
            sellerLocation,
            startPrice,
            finalPrice,
            startDate,
            endDate,
            thumbnailUrl,
            largeImageUrl,
            bids.headOption)
      }
  }

  final case class CreateAuctionBid(batchSpecification: BatchSpecification,
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
                                    bids: List[Bid]) extends CreateAuction

  final case class CreateAuctionFixedAuction(batchSpecification: BatchSpecification,
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
                                             bid: Option[Bid]) extends CreateAuction

  final case class ScrapAuction(url: String) extends AuctionScraperCommand
}
