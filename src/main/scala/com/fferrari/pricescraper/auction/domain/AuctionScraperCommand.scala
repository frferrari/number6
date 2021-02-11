package com.fferrari.pricescraper.auction.domain

import com.fferrari.pricescraper.batchmanager.domain.BatchSpecification

sealed trait AuctionScraperCommand

object AuctionScraperCommand {
  final case object StartAuctionScraper extends AuctionScraperCommand
  final case object AskNextBatchSpecification extends AuctionScraperCommand
  final case class ProceedToBatchSpecification(batchSpecification: BatchSpecification) extends AuctionScraperCommand
  final case class ProcessBatchSpecification(batchSpecification: BatchSpecification) extends AuctionScraperCommand
  final case object ExtractListingPageUrls extends AuctionScraperCommand
  final case object ExtractAuctions extends AuctionScraperCommand
  final case object StopAuctionScraper extends AuctionScraperCommand
}
