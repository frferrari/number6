package com.fferrari.pricescraper.batch.application

/*
import com.fferrari.pricescraper.auction.domain.{Auction, Bid, Price}
import com.fferrari.pricescraper.batch.dto.{BatchDTO, BatchDTOJsonSupport}
import com.fferrari.pricescraper.batchmanager.domain.BatchSpecification
import reactivemongo.api.bson.collection.BSONCollection
import reactivemongo.api.bson.{BSONDocumentWriter, Macros}
import reactivemongo.api.commands.WriteResult

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait BatchListingRepository {
  val batchListingTable = "batch_listing"
  def create(batchDTO: BatchDTO): Future[WriteResult]
}

class BatchListingRepositoryImpl(mongoDB: reactivemongo.api.DB)
  extends BatchListingRepository
    with BatchDTOJsonSupport {

  override def create(batchDTO: BatchDTO): Future[WriteResult] = {
    implicit val batchSpecificationWriter = Macros.writer[BatchSpecification]
    implicit val priceWriter = Macros.writer[Price]
    implicit val bidWriter = Macros.writer[Bid]
    implicit val auctionWriter = Macros.writer[Auction]
    implicit val batchDTOWriter: BSONDocumentWriter[BatchDTO] = Macros.writer[BatchDTO]

    mongoDB
      .collection[BSONCollection]("batch")
      .insert
      .one(batchDTO)
  }
}
*/