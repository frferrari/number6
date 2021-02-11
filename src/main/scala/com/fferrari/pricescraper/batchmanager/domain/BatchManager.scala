package com.fferrari.pricescraper.batchmanager.domain

import java.time.Instant
import java.util.UUID

import com.fferrari.pricescraper.batchmanager.domain.BatchManagerCommand.{AddBatchSpecification, CreateBatch, CreateBatchManager, PauseBatchSpecification, PauseProvider, ProvideNextBatchSpecification, ReleaseBatchSpecification, ReleaseProvider, UpdateLastUrlVisited}
import com.fferrari.pricescraper.batchmanager.domain.BatchManagerEvent.{BatchCreated, BatchManagerCreated, BatchSpecificationAdded, BatchSpecificationPaused, BatchSpecificationReleased, LastUrlVisitedUpdated, NothingToProcess, ProceedToBatchSpecification, ProviderPaused, ProviderReleased}
import com.fferrari.pricescraper.common.Clock

import scala.util.{Failure, Success, Try}

sealed trait BatchManager {
  def processCommand(command: BatchManagerCommand): Try[BatchManagerEvent]

  def applyEvent(event: BatchManagerEvent): Try[BatchManager]
}

object BatchManager {
  type BatchManagerID = UUID

  final case object EmptyBatchManager extends BatchManager {
    override def processCommand(command: BatchManagerCommand): Try[BatchManagerEvent] = command match {
      case cmd: CreateBatchManager =>
        Success(cmd.toBatchManagerCreated)

      case _ =>
        Failure(new IllegalStateException(s"Unexpected command $command in state EmptyBatchManager"))
    }

    override def applyEvent(event: BatchManagerEvent): Try[BatchManager] = event match {
      case BatchManagerCreated(batchManagerID, timestamp) =>
        Success(ActiveBatchManager(batchManagerID, Nil, timestamp))
    }
  }

  final case class ActiveBatchManager(batchManagerID: BatchManagerID,
                                      batchSpecifications: List[BatchSpecification],
                                      timestamp: Instant) extends BatchManager {
    override def processCommand(command: BatchManagerCommand): Try[BatchManagerEvent] = command match {
      case cmd: CreateBatch =>
        Success(cmd.toBatchCreated)

      case cmd: AddBatchSpecification =>
        batchSpecifications
          .find(_.name == cmd.name)
          .map(_ => Success(cmd.toBatchSpecificationAdded))
          .getOrElse(Failure(new IllegalArgumentException(s"A batchSpecification with the name ${cmd.name} already exists")))

      case cmd: UpdateLastUrlVisited =>
        batchSpecifications
          .find(_.batchSpecificationID == cmd.batchSpecificationID)
          .map(_ => Success(cmd.toLastUrlVisitedUpdated))
          .getOrElse(Failure(new IllegalArgumentException(s"Unable to update the lastUrlVisited for an unknown batchSpecification ${cmd.batchSpecificationID}")))

      case cmd: PauseBatchSpecification =>
        batchSpecifications
          .find(_.batchSpecificationID == cmd.batchSpecificationID)
          .map(_ => Success(cmd.toBatchSpecificationPaused))
          .getOrElse(Failure(new IllegalArgumentException(s"Could not pause an unknown BatchSpecification ${cmd.batchSpecificationID}")))

      case cmd: ReleaseBatchSpecification =>
        batchSpecifications
          .find(_.batchSpecificationID == cmd.batchSpecificationID)
          .map(_ => Success(cmd.toBatchSpecificationReleased))
          .getOrElse(Failure(new IllegalArgumentException(s"Could not release an unknown BatchSpecification ${cmd.batchSpecificationID}")))

      case cmd: PauseProvider =>
        batchSpecifications
          .find(_.provider == cmd.provider)
          .map(_ => Success(cmd.toProviderPaused))
          .getOrElse(Failure(new IllegalArgumentException(s"Could not pause an unknown Provider ${cmd.provider}")))

      case cmd: ReleaseProvider =>
        batchSpecifications
          .find(_.provider == cmd.provider)
          .map(_ => Success(cmd.toProviderReleased))
          .getOrElse(Failure(new IllegalArgumentException(s"Could not release an unknown Provider ${cmd.provider}")))

      case cmd: ProvideNextBatchSpecification =>
        batchSpecifications
          .filter(_.provider == cmd.provider)
          .filter(_.needsUpdate())
          .sortBy(_.updatedAt)
          .headOption
          .map(bs => Success(cmd.toProceedToBatchSpecification(bs)))
          .getOrElse(Success(NothingToProcess(Clock.now)))

      case _ =>
        Failure(new IllegalStateException(s"Unexpected command $command in state ActiveBatchManager"))
    }

    override def applyEvent(event: BatchManagerEvent): Try[BatchManager] = event match {
      case _: BatchCreated =>
        Success(this)

      case BatchSpecificationAdded(batchSpecificationID, name, description, url, provider, intervalSeconds, timestamp) =>
        val batchSpecification = BatchSpecification(batchSpecificationID, name, description, url, provider, intervalSeconds, Clock.now.minusSeconds(intervalSeconds), false, None)
        Success(copy(batchSpecifications = batchSpecifications :+ batchSpecification))

      case LastUrlVisitedUpdated(batchSpecificationID, lastUrlVisited, timestamp) =>
        val idx = batchSpecifications.indexWhere(_.batchSpecificationID == batchSpecificationID)
        if (idx >= 0) {
          val newBatchSpecification = batchSpecifications(idx).copy(lastUrlVisited = Some(lastUrlVisited), updatedAt = Clock.now)
          Success(copy(batchSpecifications = batchSpecifications.updated(idx, newBatchSpecification)))
        } else
          Failure(new IllegalStateException(s"Could not update the lastUrlVisited for an unknown batchSpecification $batchSpecificationID"))

      case BatchSpecificationPaused(batchSpecificationID, timestamp) =>
        val idx = batchSpecifications.indexWhere(_.batchSpecificationID == batchSpecificationID)
        if (idx >= 0) {
          val newBatchSpecification = batchSpecifications(idx).copy(paused = true)
          Success(copy(batchSpecifications = batchSpecifications.updated(idx, newBatchSpecification)))
        } else
          Failure(new IllegalStateException(s"Could not pause an unknown batchSpecification $batchSpecificationID"))

      case BatchSpecificationReleased(batchSpecificationID, timestamp) =>
        val idx = batchSpecifications.indexWhere(_.batchSpecificationID == batchSpecificationID)
        if (idx >= 0) {
          val newBatchSpecification = batchSpecifications(idx).copy(paused = false)
          Success(copy(batchSpecifications = batchSpecifications.updated(idx, newBatchSpecification)))
        } else
          Failure(new IllegalStateException(s"Could not release an unknown bachSpecification $batchSpecificationID"))

      case ProviderPaused(provider, timestamp) =>
        val newBatchSpecifications =
          batchSpecifications
            .filter(bs => bs.provider == provider && !bs.paused)
            .foldLeft(batchSpecifications) { (acc, bs) =>
              val idx = acc.indexWhere(_.batchSpecificationID == bs.batchSpecificationID)
              acc.updated(idx, acc(idx).copy(paused = true))
            }
        Success(copy(batchSpecifications = newBatchSpecifications))

      case ProviderReleased(provider, timestamp) =>
        val newBatchSpecifications =
          batchSpecifications
            .filter(bs => bs.provider == provider && bs.paused)
            .foldLeft(batchSpecifications) { (acc, bs) =>
              val idx = acc.indexWhere(_.batchSpecificationID == bs.batchSpecificationID)
              acc.updated(idx, acc(idx).copy(paused = false))
            }
        Success(copy(batchSpecifications = newBatchSpecifications))

      case ProceedToBatchSpecification(batchSpecification, timestamp) =>
        Success(this)

      case _ =>
        Failure(new IllegalStateException(s"Unexpected event $event in state ActiveBatchManager"))
    }
  }

}
