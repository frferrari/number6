package com.fferrari.pricescraper.service

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.AskPattern.Askable
import com.fferrari.pricescraper.PriceScraper.Command
import com.fferrari.pricescraper.batchmanager.domain.BatchManagerEntity
import org.slf4j.LoggerFactory

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class PriceScraperServiceImpl(batchManager: ActorRef[BatchManagerEntity.Command],
                              context: ActorContext[Command]) extends PriceScraperService {
  private val logger = LoggerFactory.getLogger(getClass)

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
      .map(batchSpecificationId => BatchSpecification(batchSpecificationId.toString))(context.executionContext)
  }
}
