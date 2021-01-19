package com.fferrari.batchscheduler.domain

import java.time.Instant
import java.util.UUID

import akka.Done
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, TimerScheduler}
import akka.pattern.StatusReply
import akka.persistence.typed.{PersistenceId, scaladsl}
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}
import com.fferrari.auction.application.AuctionScraperActor
import com.fferrari.batchmanager.domain.BatchSpecification
import com.fferrari.batchscheduler.application.BatchSchedulerActor.Scrapers
import com.fferrari.common.Clock
import com.fferrari.common.Entity.{EntityCommand, EntityEvent}
import net.sourceforge.htmlunit.corejs.javascript.WrappedException

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object BatchSchedulerEntity {
  val actorName = "batch-scheduler"

  sealed trait Command extends EntityCommand
  case class Create(replyTo: ActorRef[StatusReply[Done]]) extends Command
  case class AddBatchSpecification(name: String, description: String, url: String, provider: String, intervalSeconds: Long, replyTo: ActorRef[StatusReply[Reply]]) extends Command
  case object ProcessBatchSpecification extends Command
  case class UpdateLastUrlVisited(batchSpecificationID: BatchSpecification.ID, lastUrlVisited: String, replyTo: ActorRef[StatusReply[Done]]) extends Command
  case class PauseBatchSpecification(batchSpecificationID: BatchSpecification.ID, replyTo: ActorRef[StatusReply[Done]]) extends Command
  // case class PauseProvider(provider: String) extends Command

  sealed trait ProcessBatchSpecificationResult
  final case class Scraping(batchSpecificationID: BatchSpecification.ID) extends ProcessBatchSpecificationResult
  final case class Busy(request: BatchSpecification.ID, busy: BatchSpecification.ID) extends ProcessBatchSpecificationResult
  final case class Unknown(message: String) extends ProcessBatchSpecificationResult
  private final case class WrappedProcessBatchSpecificationResult(reply: ProcessBatchSpecificationResult) extends Command

  sealed trait Event extends EntityEvent
  case class Created(timestamp: Instant) extends Event
  final case class BatchSpecificationAdded(batchSpecificationID: BatchSpecification.ID, timestamp: Instant, name: String, description: String, url: String, provider: String, intervalSeconds: Long) extends Event
  final case class NextBatchSpecificationProcessed(batchSpecification: BatchSpecification.ID, timestamp: Instant) extends Event
  final case class LastUrlVisitedUpdated(batchSpecificationID: BatchSpecification.ID, timestamp: Instant, lastUrl: String) extends Event
  final case class BatchSpecificationPaused(batchSpecificationID: BatchSpecification.ID, timestamp: Instant) extends Event

  sealed trait Reply
  final case class BatchSpecificationAccepted(batchSpecificationID: ID) extends Reply


  type ID = UUID

  type ReplyEffect = akka.persistence.typed.scaladsl.ReplyEffect[Event, BatchScheduler]

  var batchSpecificationIdx: Int = 0 // TODO try to get rid of this var

  sealed trait BatchScheduler {
    def applyCommand(cmd: Command, scrapers: Scrapers)(implicit context: ActorContext[Command], timers: TimerScheduler[Command]): ReplyEffect

    def applyEvent(event: Event)(implicit context: ActorContext[Command]): BatchScheduler
  }

  case object EmptyBatchScheduler extends BatchScheduler {
    override def applyCommand(cmd: Command, scrapers: Scrapers)(implicit context: ActorContext[Command], timers: TimerScheduler[Command]): ReplyEffect = cmd match {
      case Create(replyTo) =>
        Effect
          .persist(Created(Clock.now))
          .thenReply(replyTo)(_ => StatusReply.Ack)

      case _ =>
        Effect
          .unhandled
          .thenNoReply()
    }

    override def applyEvent(event: Event)(implicit context: ActorContext[Command]): BatchScheduler = event match {
      case Created(timestamp) =>
        context.log.info("BatchScheduler is Created")
        context.self ! ProcessBatchSpecification
        ActiveBatchScheduler(Nil)

      case _ =>
        throw new IllegalStateException(s"Unexpected event $event in state [EmptyBatchScheduler]")
    }
  }

  case class ActiveBatchScheduler(batchSpecifications: List[BatchSpecification]) extends BatchScheduler {
    override def applyCommand(cmd: Command, scrapers: Scrapers)(implicit context: ActorContext[Command], timers: TimerScheduler[Command]): ReplyEffect = cmd match {
      case AddBatchSpecification(name, description, url, provider, intervalSeconds, replyTo) =>
        if (!batchSpecifications.exists(_.name == name)) {
          val batchSpecificationID = UUID.randomUUID()
          Effect
            .persist(BatchSpecificationAdded(batchSpecificationID, Clock.now, name, description, url, provider, intervalSeconds))
            .thenReply(replyTo)(_ => StatusReply.success(BatchSpecificationAccepted(batchSpecificationID)))
        } else
          Effect
            .reply(replyTo)(StatusReply.error(s"A batchSpecification with the name $name already exists"))

      case UpdateLastUrlVisited(batchSpecificationID, lastUrlVisited, replyTo) =>
        batchSpecifications.find(_.batchSpecificationID == batchSpecificationID) match {
          case Some(_) =>
            Effect
              .persist(LastUrlVisitedUpdated(batchSpecificationID, Clock.now, lastUrlVisited))
              .thenReply(replyTo)(_ => StatusReply.Ack)

          case None =>
            Effect.reply(replyTo)(StatusReply.error(s"Trying to update the last url visited for an unknown batch specification ID $batchSpecificationID (command)"))
        }

      case PauseBatchSpecification(batchSpecificationID, replyTo) =>
        batchSpecifications.find(_.batchSpecificationID == batchSpecificationID) match {
          case Some(_) =>
            Effect
              .persist(BatchSpecificationPaused(batchSpecificationID, Clock.now))
              .thenReply(replyTo)(_ => StatusReply.Ack)
          case None =>
            Effect.reply(replyTo)(StatusReply.error(s"Trying to pause an unknown bach specification ID $batchSpecificationID (command)"))
        }

      case ProcessBatchSpecification =>
        context.log.info(s"Received ProcessNextBatchSpecification command for idx $batchSpecificationIdx")
        batchSpecifications.lift(batchSpecificationIdx) match {
          case Some(batchSpecification) if batchSpecification.needsUpdate() =>
            context.pipeToSelf(processBatchSpecification(scrapers, batchSpecification, context)) {
              case Success(statusReply) => statusReply.getValue match {
                case AuctionScraperActor.StartProcessing(batchSpecificationID) =>
                  WrappedProcessBatchSpecificationResult(Scraping(batchSpecificationID))

                case AuctionScraperActor.Busy(requestBatchSpecificationID, busyBatchSpecificationID) =>
                  WrappedProcessBatchSpecificationResult(Busy(requestBatchSpecificationID, busyBatchSpecificationID))
              }

              case Failure(f) =>
                WrappedProcessBatchSpecificationResult(Unknown(f.getMessage))
            }
            Effect.none.thenNoReply()

          case _ =>
            nextBatchSpecificationIdx(batchSpecifications)
            timers.startSingleTimer(ProcessBatchSpecification, 10.seconds) // TODO change from 10.seconds to 60.seconds
            Effect.none.thenNoReply()
        }

      case WrappedProcessBatchSpecificationResult(Scraping(batchSpecificationID)) =>
        context.log.info(s"The scraper has started processing the batch specification $batchSpecificationID")
        nextBatchSpecificationIdx(batchSpecifications)
        timers.startSingleTimer(ProcessBatchSpecification, 10.seconds)
        Effect
          .persist(NextBatchSpecificationProcessed(batchSpecificationID, Clock.now)) // TODO remove this useless event
          .thenNoReply()

      case WrappedProcessBatchSpecificationResult(Busy(requestBatchSpecificationID, busyBatchSpecificationID)) =>
        context.log.info(s"Could not process batch specification $requestBatchSpecificationID because the scraper is busy on $busyBatchSpecificationID")
        nextBatchSpecificationIdx(batchSpecifications)
        timers.startSingleTimer(ProcessBatchSpecification, 10.seconds)
        Effect.none.thenNoReply()

      case WrappedProcessBatchSpecificationResult(Unknown(message)) =>
        context.log.error(s"Unexpected reply to processBatchProcessing request ($message)")
        nextBatchSpecificationIdx(batchSpecifications)
        Effect.none.thenNoReply()

      case _ =>
        Effect.unhandled.thenNoReply()
    }

    override def applyEvent(event: Event)(implicit context: ActorContext[Command]): BatchScheduler = event match {
      case _: Created =>
        throw new IllegalStateException(s"Unexpected event $event received in state [ActiveBatchScheduler]")

      case BatchSpecificationAdded(batchSpecificationID, timestamp, name, description, url, provider, intervalSeconds) =>
        val batchSpecification = BatchSpecification(batchSpecificationID, name, description, url, provider, intervalSeconds, Clock.now, false, None)
        context.log.info(s"BatchSpecificationAdded $batchSpecification")
        copy(batchSpecifications = batchSpecifications :+ batchSpecification)

      case LastUrlVisitedUpdated(batchSpecificationID, timestamp, lastUrlVisited) =>
        context.log.info(s"LastUrlVisitedUpdated to $lastUrlVisited for batch $batchSpecificationID")
        val idx = batchSpecifications.indexWhere(_.batchSpecificationID == batchSpecificationID)
        if (idx >= 0) {
          val newBatchSpecification = batchSpecifications(idx).copy(lastUrlVisited = Some(lastUrlVisited))
          copy(batchSpecifications = batchSpecifications.updated(idx, newBatchSpecification))
        } else throw new IllegalStateException(s"Trying to update the last url visited for an unknown batch specification ID $batchSpecificationID (event)")

      case BatchSpecificationPaused(batchSpecificationID, timestamp) =>
        context.log.info(s"BatchSpecificationPaused for batch $batchSpecificationID")
        val idx = batchSpecifications.indexWhere(_.batchSpecificationID == batchSpecificationID)
        if (idx >= 0) {
          val newBatchSpecification = batchSpecifications(idx).copy(paused = true)
          copy(batchSpecifications = batchSpecifications.updated(idx, newBatchSpecification))
        } else throw new IllegalStateException(s"Trying to pause an unknown bach specification ID $batchSpecificationID (event)")

      case NextBatchSpecificationProcessed(batchSpecificationID, timestamp) =>
        this
    }
  }

  def apply(persistenceId: PersistenceId, scrapers: Scrapers)
           (implicit context: ActorContext[Command], timers: TimerScheduler[Command]): Behavior[Command] = {
    EventSourcedBehavior.withEnforcedReplies[Command, Event, BatchScheduler](
      persistenceId,
      EmptyBatchScheduler,
      (state, cmd) => state.applyCommand(cmd, scrapers),
      (state, event) => state.applyEvent(event))
  }

  /**
   * Tell the appropriate scraper to extract the auctions for the given batch specification
   * @param scrapers An object containing the different available scrapers
   * @param batchSpecification A batch specification for which to extract the auctions
   */
  def processBatchSpecification(scrapers: Scrapers,
                                batchSpecification: BatchSpecification,
                                context: ActorContext[Command]): Future[StatusReply[AuctionScraperActor.Reply]] = {
    // TODO Send to the appropriate scraper depending on the provider
    scrapers.delcampeScraperRouter.ask(AuctionScraperActor.ProcessBatchSpecification(batchSpecification, _))(3.seconds, context.system.scheduler)
  }

  /**
   * Move to the next batch specification index to process
   * @param batchSpecifications The list of batch specifications
   */
  def nextBatchSpecificationIdx(batchSpecifications: List[BatchSpecification]): Unit =
    if (batchSpecificationIdx + 1 < batchSpecifications.size)
      batchSpecificationIdx = batchSpecificationIdx + 1
    else
      batchSpecificationIdx = 0
}
