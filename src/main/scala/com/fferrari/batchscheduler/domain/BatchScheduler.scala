package com.fferrari.batchscheduler.domain

import com.fferrari.batchscheduler.domain.es.BatchSchedulerCommand.{PauseBatchSpecification, UpdateLastUrl}
import com.fferrari.batchscheduler.domain.es.BatchSchedulerEvent.LastUrlUpdated
import com.fferrari.batchscheduler.domain.es.{BatchSchedulerCommand, BatchSchedulerEvent}

import scala.util.{Failure, Success, Try}

final case class BatchScheduler private[domain](specifications: List[BatchSpecification]) {
  def applyEvent(batchSchedulerEvent: BatchSchedulerEvent): Try[BatchScheduler] = {
    case event: LastUrlUpdated =>
      val idx = specifications.indexWhere(_.id == event.batchSpecificationId)
      specifications.lift(idx) match {
        case Some(batchSpecification) =>
          Success(copy(specifications = specifications.updated(idx, batchSpecification.copy(lastUrlScraped = Some(event.lastUrl)))))
        case None =>
          Failure(new NoSuchElementException(s"Trying to update the last url of an unknown batch specification (${event.batchSpecificationId}"))
      }
    case event: PauseBatchSpecification =>
      val idx = specifications.indexWhere(_.id == event.batchSpecificationId)
      specifications.lift(idx) match {
        case Some(batchSpecification) =>
          Success(copy(specifications = specifications.updated(idx, batchSpecification.copy(paused = true))))
        case None =>
          Failure(new NoSuchElementException(s"Trying to pause an unknown batch specification (${event.batchSpecificationId}"))
      }
  }

  def processCommand(batchSchedulerCommand: BatchSchedulerCommand): Try[List[BatchSchedulerEvent]] =
    batchSchedulerCommand match {
      case command: UpdateLastUrl => updateLastUrl(command)
      case command: PauseBatchSpecification => pauseBatchSpecification(command)
    }

  def updateLastUrl(command: UpdateLastUrl): Try[List[BatchSchedulerEvent]] =
    specifications
      .find(_.id == command.batchSpecificationId)
      .map(_ => Success(List(command.toLastUrlUpdated)))
      .getOrElse(Failure(new NoSuchElementException(s"Trying to update the last url of an unknown batch specification (${command.batchSpecificationId}")))

  def pauseBatchSpecification(command: PauseBatchSpecification): Try[List[BatchSchedulerEvent]] =
    specifications
      .find(_.id == command.batchSpecificationId)
      .map(_ => Success(List(command.toBatchSpecificationPaused)))
      .getOrElse(Failure(new NoSuchElementException(s"Trying to pause an unknown batch specification (${command.batchSpecificationId}")))
}
