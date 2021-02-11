package com.fferrari.pricescraper.batchmanager.domain

import java.util.UUID

import akka.Done
import akka.actor.typed.ActorRef
import akka.pattern.StatusReply
import com.fferrari.pricescraper.auction.domain.{Auction, AuctionScraperCommand}
import com.fferrari.pricescraper.batchmanager.domain.BatchManagerEvent._
import com.fferrari.pricescraper.batchmanager.domain.BatchSpecification.BatchSpecificationID
import com.fferrari.pricescraper.common.Clock

sealed trait BatchManagerCommand
sealed trait BatchManagerCommandSimpleReply extends BatchManagerCommand {
  def replyTo: ActorRef[StatusReply[Done]]
}

object BatchManagerCommand {

  final case class CreateBatchManager(replyTo: ActorRef[StatusReply[Done]]) extends BatchManagerCommandSimpleReply {
    def toBatchManagerCreated: BatchManagerCreated =
      BatchManagerCreated(UUID.randomUUID(), Clock.now)
  }

  final case class AddBatchSpecification(name: String,
                                         description: String,
                                         url: String,
                                         provider: String,
                                         intervalSeconds: Long,
                                         replyTo: ActorRef[StatusReply[BatchSpecificationID]]) extends BatchManagerCommand {
    def toBatchSpecificationAdded: BatchSpecificationAdded =
      BatchSpecificationAdded(UUID.randomUUID(), name, description, url, provider, intervalSeconds, Clock.now)
  }

  final case class UpdateLastUrlVisited(batchSpecificationID: BatchSpecificationID,
                                        lastUrlVisited: String,
                                        replyTo: ActorRef[StatusReply[Done]]) extends BatchManagerCommandSimpleReply {
    def toLastUrlVisitedUpdated: LastUrlVisitedUpdated =
      LastUrlVisitedUpdated(batchSpecificationID, lastUrlVisited, Clock.now)
  }

  final case class PauseBatchSpecification(batchSpecificationID: BatchSpecificationID,
                                           replyTo: ActorRef[StatusReply[Done]]) extends BatchManagerCommandSimpleReply {
    def toBatchSpecificationPaused: BatchSpecificationPaused =
      BatchSpecificationPaused(batchSpecificationID, Clock.now)
  }

  final case class ReleaseBatchSpecification(batchSpecificationID: BatchSpecificationID,
                                             replyTo: ActorRef[StatusReply[Done]]) extends BatchManagerCommandSimpleReply {
    def toBatchSpecificationReleased: BatchSpecificationReleased =
      BatchSpecificationReleased(batchSpecificationID, Clock.now)
  }

  final case class PauseProvider(provider: String,
                                 replyTo: ActorRef[StatusReply[Done]]) extends BatchManagerCommandSimpleReply {
    def toProviderPaused: ProviderPaused =
      ProviderPaused(provider, Clock.now)
  }

  final case class ReleaseProvider(provider: String,
                                   replyTo: ActorRef[StatusReply[Done]]) extends BatchManagerCommandSimpleReply {
    def toProviderReleased: ProviderReleased =
      ProviderReleased(provider, Clock.now)
  }

  final case class CreateBatch(batchSpecification: BatchSpecification,
                               auctions: List[Auction],
                               replyTo: ActorRef[StatusReply[Done]]) extends BatchManagerCommandSimpleReply {
    def toBatchCreated: BatchCreated = BatchCreated(UUID.randomUUID(), batchSpecification, auctions, Clock.now)
  }

  final case class ProvideNextBatchSpecification(provider: String,
                                                 replyTo: ActorRef[StatusReply[AuctionScraperCommand]]) extends BatchManagerCommand {
    def toProceedToBatchSpecification(batchSpecification: BatchSpecification): ProceedToBatchSpecification =
      ProceedToBatchSpecification(batchSpecification, Clock.now)
  }
}
