package com.fferrari.pricescraper.batch.domain

import java.util.UUID

import akka.Done
import akka.actor.typed.ActorRef
import akka.pattern.StatusReply
import com.fferrari.pricescraper.auction.domain.Auction
import com.fferrari.pricescraper.auction.domain.Auction.AuctionID
import com.fferrari.pricescraper.batch.domain.Batch.BatchID
import com.fferrari.pricescraper.batch.domain.BatchEvent.{AuctionMatched, BatchCreated}
import com.fferrari.pricescraper.batchmanager.domain.BatchSpecification
import com.fferrari.pricescraper.common.Clock

sealed trait BatchCommand

object BatchCommand {
  final case class CreateBatch private[domain] (batchSpecification: BatchSpecification,
                                                auctions: List[Auction],
                                                replyTo: ActorRef[StatusReply[Batch.BatchID]]) extends BatchCommand {
    def toBatchCreated: BatchCreated = BatchCreated(UUID.randomUUID(), batchSpecification, auctions, Clock.now)
  }

  final case class MatchAuction private[domain] (batchID: BatchID,
                                                 auctionID: AuctionID,
                                                 itemID: UUID,
                                                 replyTo: ActorRef[StatusReply[Done]]) extends BatchCommand {
    def toAuctionMatched: AuctionMatched = AuctionMatched(batchID, auctionID, itemID, Clock.now)
  }
}