package com.fferrari.pricescraper.service

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.AskPattern.Askable
import com.fferrari.pricescraper.PriceScraper.Command
import com.fferrari.pricescraper.batchmanager.domain.BatchManagerEntity
import org.slf4j.LoggerFactory

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import java.util.concurrent.TimeoutException

import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.grpc.GrpcServiceException
import io.grpc.Status

class PriceScraperServiceImpl(batchManager: ActorRef[BatchManagerEntity.Command],
                              context: ActorContext[Command]) extends PriceScraperService {
  private val logger = LoggerFactory.getLogger(getClass)

  // private val sharding = ClusterSharding(system)
  implicit val executionContext = context.executionContext

  override def addBatchSpecification(in: AddBatchSpecificationRequest): Future[BatchSpecification] = {
    logger.info("addBatchSpecification {}", in.name)
    batchManager
      .askWithStatus(
        BatchManagerEntity.AddBatchSpecification(
          in.name,
          in.description,
          in.url,
          in.provider,
          in.intervalSeconds, _)
      )(3.seconds, context.system.scheduler)
      .map(batchSpecificationId => BatchSpecification(batchSpecificationId.toString))
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
}
