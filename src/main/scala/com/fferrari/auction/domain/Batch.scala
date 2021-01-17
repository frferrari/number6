package com.fferrari.auction.domain

import java.util.UUID

import com.fferrari.batchmanager.domain.BatchSpecification

case class Batch(batchID: Batch.ID,
                 batchSpecificationID: BatchSpecification.ID,
                 auctionUrls: List[AuctionLink],
                 auctions: List[Auction])

object Batch {
  type ID =UUID
}