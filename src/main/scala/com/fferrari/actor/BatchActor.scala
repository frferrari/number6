package com.fferrari.actor

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.pattern.StatusReply
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect}
import com.fferrari.actor.AuctionScraperProtocol.{CreateAuction, CreateAuctionBid, CreateAuctionFixedAuction}
import com.fferrari.model.BatchSpecification

object BatchActor {
  sealed trait Command
  final case class CreateBatch(batchSpecification: BatchSpecification, auctions: List[CreateAuction]) extends Command

  sealed trait Event
  final case class BatchCreated(batchSpecification: BatchSpecification, auctions: List[BatchAuction]) extends Event

  sealed trait State
  final case object EmptyState extends State
  final case class ActiveState(batchSpecification: BatchSpecification, auctions: List[BatchAuction]) extends State

  def apply(batchId: String): Behavior[Command] =
    Behaviors.setup { context =>
      EventSourcedBehavior.withEnforcedReplies[Command, Event, State](
        persistenceId = PersistenceId.ofUniqueId(s"batch-$batchId"),
        emptyState = EmptyState,
        commandHandler = commandHandler(context),
        eventHandler = eventHandler
      )
    }

  def commandHandler(context: ActorContext[Command])(state: State, command: Command): ReplyEffect[Event, State] = {
    command match {
      case CreateBatch(batchSpecification, auctions) =>
        val batchAuctions: List[BatchAuction] = auctions.map {
          case CreateAuctionBid(batchSpecification, externalId, url, title, isSold, sellerNickname, sellerLocation, startPrice, finalPrice, startDate, endDate, thumbnailUrl, largeImageUrl, bids) =>
            BatchAuctionBid(batchSpecification, externalId, url, title, isSold, sellerNickname, sellerLocation, startPrice, finalPrice, startDate, endDate, thumbnailUrl, largeImageUrl, bids)

          case CreateAuctionFixedAuction(batchSpecification, externalId, url, title, isSold, sellerNickname, sellerLocation, startPrice, finalPrice, startDate, endDate, thumbnailUrl, largeImageUrl, bid) =>
            BatchAuctionFixedAuction(batchSpecification, externalId, url, title, isSold, sellerNickname, sellerLocation, startPrice, finalPrice, startDate, endDate, thumbnailUrl, largeImageUrl, bid)
        }
        Effect
          .persist(BatchCreated(batchSpecification, batchAuctions))
          .thenNoReply()
    }
  }

  def eventHandler(state: State, event: Event): State = {
    event match {
      case BatchCreated(batchSpecification, auctions) =>
        ActiveState(batchSpecification, auctions)
    }
  }
}
