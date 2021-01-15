package com.fferrari.batchmanager.domain.es

import akka.Done
import akka.actor.typed.ActorRef
import akka.pattern.StatusReply
import com.fferrari.auction.domain.Auction
import com.fferrari.batchmanager.domain.es.BatchManagerEvent.BatchCreated
import com.fferrari.util.Clock

sealed trait BatchManagerCommand

object BatchManagerCommand {
  final case class CreateBatch(batchId: String,
                               batchSpecificationId: String,
                               lastUrlScraped: String,
                               auctions: List[Auction],
                               replyTo: ActorRef[StatusReply[Done]]) extends BatchManagerCommand {
    def toBatchCreated: BatchCreated = {
      BatchCreated(batchId, batchSpecificationId, lastUrlScraped, auctions, Clock.now)
    }
  }
}
