package com.fferrari.model

case class BatchAuctionLink(auctionUrl: String, thumbUrl: String)

case class Batch(batchId: String,
                 batchSpecification: BatchSpecification,
                 auctionUrls: List[BatchAuctionLink],
                 auctions: List[Auction])
