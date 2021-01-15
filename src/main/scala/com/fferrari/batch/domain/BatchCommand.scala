package com.fferrari.batch.domain

import java.util.UUID

import com.fferrari.auction.domain.Auction
import com.fferrari.batchspecification.domain.BatchSpecification
import com.fferrari.escommon.EntityCommand

sealed trait BatchCommand[R] extends EntityCommand[Batch.ID, Batch, R]

case class CreateBatch(entityID: Batch.ID,
                       batchSpecificationID: BatchSpecification.ID,
                       auctions: List[Auction]) extends BatchCommand[BatchReply] {
  override def initializedReply: Batch => BatchReply = _ => BatchAlreadyExists(entityID)

  override def uninitializedReply: BatchReply = BatchAccepted(entityID)
}

case class MatchAuction(entityID: Batch.ID, auctionID: Auction.ID, matchID: UUID) extends BatchCommand[MatchReply] {
  override def initializedReply: Batch => MatchReply =
    _.auctions
      .find(_.id == auctionID)
      .map(_ => MatchAccepted).getOrElse(AuctionNotFound(auctionID))

  override def uninitializedReply: MatchReply = BatchNotFound
}

sealed trait BatchReply
case class BatchAccepted(batchId: Batch.ID) extends BatchReply
case class BatchAlreadyExists(batchId: Batch.ID) extends BatchReply

sealed trait MatchReply
case object MatchAccepted extends MatchReply
case class AuctionNotFound(auctionId: Auction.ID) extends MatchReply
case object BatchNotFound extends MatchReply
