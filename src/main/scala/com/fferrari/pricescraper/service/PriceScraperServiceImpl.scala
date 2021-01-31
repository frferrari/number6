package com.fferrari.pricescraper.service

import java.util.concurrent.TimeoutException

import akka.Done
import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.grpc.GrpcServiceException
import akka.pattern.StatusReply
import akka.util.Timeout
import com.fferrari.pricescraper.PriceScraper.Command
import com.fferrari.pricescraper.batchmanager.domain.{BatchManagerEntity, BatchSpecification}
import com.fferrari.pricescraper._
import com.fferrari.pricescraper.proto.{AddResponse, PauseBatchSpecificationRequest}
import io.grpc.Status
import org.slf4j.LoggerFactory

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class PriceScraperServiceImpl(batchManager: ActorRef[BatchManagerEntity.Command],
                              context: ActorContext[Command]) extends proto.PriceScraperService {
  private val logger = LoggerFactory.getLogger(getClass)

  implicit val executionContext = context.executionContext
  implicit val scheduler = context.system.scheduler
  implicit val timeout = Timeout(3.seconds)

  override def addBatchSpecification(in: proto.AddBatchSpecificationRequest): Future[proto.AddResponse] = {
    logger.info("addBatchSpecification {}", in.name)
    batchManager
      .askWithStatus(toAddBatchSpecification(in))
      .map(batchSpecificationId => proto.AddResponse(batchSpecificationId.toString))
      .recoverWith {
        case _: TimeoutException =>
          Future.failed(
            new GrpcServiceException(Status.UNAVAILABLE.withDescription("AddBatchSpecification has timed out"))
          )
        case ex =>
          Future.failed(
            new GrpcServiceException(Status.INVALID_ARGUMENT.withDescription(ex.getMessage))
          )
      }
  }

  private def toAddBatchSpecification(in: proto.AddBatchSpecificationRequest)
                                     (ref: ActorRef[StatusReply[BatchSpecification.ID]]): BatchManagerEntity.AddBatchSpecification =
    BatchManagerEntity
      .AddBatchSpecification(
        in.name,
        in.description,
        in.url,
        in.provider,
        in.intervalSeconds, ref)

  override def pauseBatchSpecification(in: PauseBatchSpecificationRequest): Future[proto.PauseResponse] = {
    logger.info("pauseBatchSpecification {}", in.batchSpecificationId)
    batchManager
      .askWithStatus(toPauseBatchSpecification(in))
      .map(_ => proto.PauseResponse())
      .recoverWith {
        case _: TimeoutException =>
          Future.failed(
            new GrpcServiceException(Status.UNAVAILABLE.withDescription("PauseBatchSpecification has timed out"))
          )
        case ex =>
          Future.failed(
            new GrpcServiceException(Status.INVALID_ARGUMENT.withDescription(ex.getMessage))
          )
      }
  }

  private def toPauseBatchSpecification(in: PauseBatchSpecificationRequest)
                                       (ref: ActorRef[StatusReply[Done]]): BatchManagerEntity.PauseBatchSpecification =
    BatchManagerEntity.PauseBatchSpecification(java.util.UUID.fromString(in.batchSpecificationId), ref)
}
