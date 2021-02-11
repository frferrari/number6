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
      case (cmd: CreateBatch, Success(events)) =>
        events.find { case _: BatchCreated => true } match {
          case Some(event: BatchCreated) =>
            Effect
              .persist(events)
              .thenReply(cmd.replyTo)(_ => StatusReply.success(event.batchID))

          case None =>
            Effect
              .none
              .thenReply(cmd.replyTo)(_ => StatusReply.error(s"Could not persist because of a missing BatchCreated ($cmd)"))
        }

      case (cmd: CreateBatch, Failure(f)) =>
        context.log.error(f.getMessage)

        Effect
          .unhandled
          .thenReply(cmd.replyTo)(_ => StatusReply.error(f))

      case (cmd: MatchAuction, Success(events)) =>
        events.find { case _: AuctionMatched => true } match {
          case Some(event: AuctionMatched) =>
            Effect
              .persist(events)
              .thenReply(cmd.replyTo)(_ => StatusReply.Ack)

          case None =>
            Effect
              .none
              .thenReply(cmd.replyTo)(_ => StatusReply.error(s"Could not persist because of a missing AuctionMatched ($cmd)"))
        }

      case (cmd: MatchAuction, Failure(f)) =>
        context.log.error(f.getMessage)

        Effect
          .unhandled
          .thenReply(cmd.replyTo)(_ => StatusReply.error(f))
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
