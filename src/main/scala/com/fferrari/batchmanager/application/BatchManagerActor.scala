package com.fferrari.batchmanager.application

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.pattern.StatusReply
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect}
import com.fferrari.batch.application.BatchActor
import com.fferrari.batchmanager.domain.es.BatchManagerCommand.CreateBatch
import com.fferrari.batchmanager.domain.es.BatchManagerEvent.BatchCreated
import com.fferrari.batchmanager.domain.es.{BatchManagerCommand, BatchManagerEvent}
import com.fferrari.batchscheduler.application.BatchSchedulerActor

object BatchManagerActor {
  val actorName = "batch-manager"

  sealed trait State
  final case object EmptyState extends State

  val BatchManagerServiceKey = ServiceKey[BatchManagerCommand]("batchManagerService")

  def apply(batchSchedulerRef: ActorRef[BatchSchedulerActor.BatchSpecificationCommand]): Behavior[BatchManagerCommand] = Behaviors.setup { context =>
    context.log.info("Starting")

    // Register with the Receptionist
    context.system.receptionist ! Receptionist.Register(BatchManagerServiceKey, context.self)

    // Start the event sourcing behavior/handlers
    EventSourcedBehavior.withEnforcedReplies[BatchManagerCommand, BatchManagerEvent, State](
      persistenceId = PersistenceId.ofUniqueId(actorName),
      emptyState = EmptyState,
      commandHandler = commandHandler(context, batchSchedulerRef),
      eventHandler = eventHandler(context)
    )
  }

  def commandHandler(context: ActorContext[BatchManagerCommand], batchSchedulerRef: ActorRef[BatchSchedulerActor.BatchSpecificationCommand])
                    (state: State, command: BatchManagerCommand): ReplyEffect[BatchManagerEvent, State] = {
    command match {
      case cmd@CreateBatch(batchId, batchSpecificationId, lastUrlScraped, auctions, replyTo) =>
        Effect
          .persist(cmd.toBatchCreated)
          .thenRun(updateLastUrl(batchSpecificationId, lastUrlScraped, batchSchedulerRef))
          .thenReply(replyTo)(_ => StatusReply.Ack)
    }
  }

  def updateLastUrl(batchSpecificationId: String,
                    lastUrlScraped: String,
                    batchSchedulerRef: ActorRef[BatchSchedulerActor.BatchSpecificationCommand])
                   (state: State): Unit = {
    batchSchedulerRef ! BatchSchedulerActor.UpdateLastUrl(batchSpecificationId, lastUrlScraped)
  }

  def eventHandler(context: ActorContext[BatchManagerCommand])(state: State, event: BatchManagerEvent): State = {
    event match {
      case BatchCreated(batchId, batchSpecificationId, pageNumber, firstUrlScraped, auctions, createdAt) =>
        val batchActor = context.spawn(BatchActor(batchId), s"batch-${batchId}")
        batchActor ! BatchActor.CreateBatch(batchSpecification, auctions)
        EmptyState
    }
  }
}
