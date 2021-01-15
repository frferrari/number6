package com.fferrari.batch.application

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect}
import com.fferrari.auction.domain.Auction
import com.fferrari.batchscheduler.domain.BatchSpecification

object BatchActor {
  sealed trait BatchCommand
  final case class CreateBatch(batchSpecification: BatchSpecification, auctions: List[Auction]) extends BatchCommand

  sealed trait BatchEvent
  final case class BatchCreated(batchSpecification: BatchSpecification, auctions: List[Auction]) extends BatchEvent

  sealed trait State
  final case object EmptyState extends State
  final case class ActiveState(batchSpecification: BatchSpecification, auctions: List[Auction]) extends State

  def apply(batchId: String): Behavior[BatchCommand] =
    Behaviors.setup { context =>
      context.log.info("Starting")

      EventSourcedBehavior.withEnforcedReplies[BatchCommand, BatchEvent, State](
        persistenceId = PersistenceId.ofUniqueId(s"batch-$batchId"),
        emptyState = EmptyState,
        commandHandler = commandHandler(context),
        eventHandler = eventHandler
      )
    }

  def commandHandler(context: ActorContext[BatchCommand])(state: State, command: BatchCommand): ReplyEffect[BatchEvent, State] = {
    command match {
      case CreateBatch(batchSpecification, auctions) =>
        Effect
          .persist(BatchCreated(batchSpecification, auctions))
          .thenNoReply()
    }
  }

  def eventHandler(state: State, event: BatchEvent): State = {
    event match {
      case BatchCreated(batchSpecification, auctions) =>
        ActiveState(batchSpecification, auctions)
    }
  }
}
