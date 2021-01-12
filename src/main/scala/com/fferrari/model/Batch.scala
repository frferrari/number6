package com.fferrari.model

import com.fferrari.actor.AuctionScraperProtocol.CreateAuction

case class BatchAuctionLink(auctionUrl: String, thumbUrl: String)

case class Batch(batchId: String,
                 batchSpecification: BatchSpecification,
                 auctionUrls: List[BatchAuctionLink],
                 auctions: List[CreateAuction])
