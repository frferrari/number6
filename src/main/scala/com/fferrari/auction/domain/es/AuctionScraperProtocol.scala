package com.fferrari.auction.domain.es

import akka.actor.typed.receptionist.Receptionist
import com.fferrari.batch.domain.Batch
import com.fferrari.batchscheduler.domain.BatchSpecification

object AuctionScraperProtocol {

  sealed trait AuctionScraperCommand
  final case object LookupBatchManager extends AuctionScraperCommand
  final case object ExtractUrls extends AuctionScraperCommand
  final case class ExtractListingPageUrls(batchSpecification: BatchSpecification, pageNumber: Int = 1) extends AuctionScraperCommand
  final case class ExtractAuctions(batchSpecification: BatchSpecification, batch: Batch, pageNumber: Int) extends AuctionScraperCommand
  final case class ListingResponse(listing: Receptionist.Listing) extends AuctionScraperCommand
}
