package com.fferrari.actor

import akka.actor.typed.receptionist.Receptionist
import com.fferrari.model._

object AuctionScraperProtocol {

  sealed trait AuctionScraperCommand
  final case object LookupBatchManager extends AuctionScraperCommand
  final case object ExtractUrls extends AuctionScraperCommand
  final case class ExtractListingPageUrls(batchSpecification: BatchSpecification, pageNumber: Int = 1) extends AuctionScraperCommand
  final case class ExtractAuctions(batchSpecification: BatchSpecification, urlBatch: Batch, pageNumber: Int) extends AuctionScraperCommand
  final case class ListingResponse(listing: Receptionist.Listing) extends AuctionScraperCommand
}
