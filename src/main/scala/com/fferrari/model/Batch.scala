package com.fferrari.model

trait BatchAuctionLink {
  def auctionUrl: String
}

case class BatchAuctionAndThumbnailLink(auctionUrl: String, thumbUrl: String) extends BatchAuctionLink

case class Batch(batchId: String,
                 websiteInfo: WebsiteConfig,
                 auctionUrls: List[BatchAuctionLink])
