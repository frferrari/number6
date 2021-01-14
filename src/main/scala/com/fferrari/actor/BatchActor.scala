package com.fferrari.actor

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect}
import com.fferrari.model.{Auction, BatchSpecification}

object BatchActor {
  sealed trait Command
  final case class CreateBatch(batchSpecification: BatchSpecification, auctions: List[Auction]) extends Command

  sealed trait Event
  final case class BatchCreated(batchSpecification: BatchSpecification, auctions: List[Auction]) extends Event

  sealed trait State
  final case object EmptyState extends State
  final case class ActiveState(batchSpecification: BatchSpecification, auctions: List[Auction]) extends State

  def apply(batchId: String): Behavior[Command] =
    Behaviors.setup { context =>
      context.log.info("Starting")

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
        Effect
          .persist(BatchCreated(batchSpecification, auctions))
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
