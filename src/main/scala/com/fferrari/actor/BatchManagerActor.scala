package com.fferrari.actor

import akka.Done
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.pattern.StatusReply
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect}
import com.fferrari.actor.AuctionScraperProtocol.CreateAuction
import com.fferrari.model.BatchSpecification

object BatchManagerActor {
  val actorName = "batchManager"

  sealed trait Command
  final case class CreateBatch(batchSpecification: BatchSpecification, auctions: List[CreateAuction], replyTo: ActorRef[StatusReply[Done]]) extends Command

  sealed trait Event
  final case class BatchCreated(batchSpecification: BatchSpecification, auctions: List[CreateAuction]) extends Event

  sealed trait State
  final case object EmptyState extends State

  val BatchManagerServiceKey = ServiceKey[Command]("batchManagerService")

  def apply(): Behavior[Command] = Behaviors.setup { context =>
    context.log.info("Starting")

    // Register wit the Receptionist
    context.system.receptionist ! Receptionist.Register(BatchManagerServiceKey, context.self)

    // Start the event sourcing behavior/handlers
    EventSourcedBehavior.withEnforcedReplies[Command, Event, State](
      persistenceId = PersistenceId.ofUniqueId(actorName),
      emptyState = EmptyState,
      commandHandler = commandHandler(context),
      eventHandler = eventHandler(context)
    )
  }

  def commandHandler(context: ActorContext[Command])(state: State, command: Command): ReplyEffect[Event, State] = {
    command match {
      case CreateBatch(batchSpecification, auctions, replyTo) =>
        Effect
          .persist(BatchCreated(batchSpecification, auctions))
          .thenReply(replyTo)(_ => StatusReply.Ack)
    }
  }

  def eventHandler(context: ActorContext[Command])(state: State, event: Event): State = {
    event match {
      case BatchCreated(batchSpecification, auctions) =>
        val batchActor = context.spawn(BatchActor(batchSpecification.id), s"batch-${batchSpecification.id}")
        batchActor ! BatchActor.CreateBatch(batchSpecification, auctions)
        EmptyState
    }
  }
}
