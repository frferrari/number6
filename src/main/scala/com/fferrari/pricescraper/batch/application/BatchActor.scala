package com.fferrari.pricescraper.batch.application

import java.util.UUID

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.pattern.StatusReply
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect}
import com.fferrari.pricescraper.batch.domain.Batch.EmptyBatch
import com.fferrari.pricescraper.batch.domain.BatchCommand.{CreateBatch, MatchAuction}
import com.fferrari.pricescraper.batch.domain.BatchEvent.{AuctionMatched, BatchCreated}
import com.fferrari.pricescraper.batch.domain.{Batch, BatchCommand, BatchEvent}

import scala.util.{Failure, Success}

object BatchActor {
  def apply(batchID: UUID): Behavior[BatchCommand] =
    Behaviors.setup { implicit context =>
      context.log.info("Starting")

      EventSourcedBehavior.withEnforcedReplies[BatchCommand, BatchEvent, Batch](
        PersistenceId.ofUniqueId(s"batch-$batchID"),
        EmptyBatch,
        (state, cmd) => processCommand(state, cmd),
        (state, event) => applyEvent(state, event)
      ).withTagger(_ => Set("batch"))
    }

  def processCommand(state: Batch, command: BatchCommand)
                    (implicit context: ActorContext[BatchCommand]): ReplyEffect[BatchEvent, Batch] =
    (command, state.processCommand(command)) match {
      case (cmd: CreateBatch, Success(event: BatchCreated)) =>
        Effect
          .persist(event)
          .thenReply(cmd.replyTo)(_ => StatusReply.success(event.batchID))

      case (cmd: CreateBatch, Failure(f)) =>
        context.log.error(f.getMessage)

        Effect
          .unhandled
          .thenReply(cmd.replyTo)(_ => StatusReply.error(f))

      case (cmd: MatchAuction, Success(event: AuctionMatched)) =>
        Effect
          .persist(event)
          .thenReply(cmd.replyTo)(_ => StatusReply.Ack)

      case (cmd: MatchAuction, Failure(f)) =>
        context.log.error(f.getMessage)

        Effect
          .unhandled
          .thenReply(cmd.replyTo)(_ => StatusReply.error(f))

      case (cmd, event) =>
        context.log.error(s"Unexpected event $event produced for command $cmd")
        Effect
          .unhandled
          .thenNoReply()
    }

  def applyEvent(state: Batch, event: BatchEvent)
                (implicit context: ActorContext[BatchCommand]): Batch =
    state.applyEvent(event) match {
      case Success(batch) =>
        batch

      case Failure(f) =>
        context.log.error(f.getMessage)
        throw f
    }
}
