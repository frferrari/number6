package com.fferrari.batch.application

import java.util.UUID

import com.fferrari.auction.domain.Auction
import com.fferrari.batch.domain.{Batch, BatchReply, MatchReply}
import com.fferrari.batchspecification.domain.BatchSpecification

trait BatchRepository[F[_]] {
  def createBatch(batchID: Batch.ID, batchSpecificationID: BatchSpecification.ID, auctions: List[Auction]): F[BatchReply]
  def matchAuction(batchID: Batch.ID, auctionID: Auction.ID, matchID: UUID): F[MatchReply]
  def unmatchAuction(batchID: Batch.ID, auctionID: Auction.ID): F[MatchReply]
}
