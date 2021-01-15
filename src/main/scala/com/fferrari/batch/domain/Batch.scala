package com.fferrari.batch.domain

import com.fferrari.auction.domain.Auction

case class BatchAuctionLink(auctionUrl: String, thumbUrl: String)

final case class Batch private[domain](batchId: String,
                                       batchSpecificationId: String,
                                       pageNumber: Int,
                                       auctionUrls: List[BatchAuctionLink],
                                       auctions: List[Auction],
                                       firstUrlScraped: String) {
}
