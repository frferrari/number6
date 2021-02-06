package com.fferrari.pricescraper.batch.dto

import java.time.Instant

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import com.fferrari.pricescraper.auction.domain.{Auction, Bid, Price}
import com.fferrari.pricescraper.batch.domain.BatchEntity
import com.fferrari.pricescraper.batchmanager.domain.BatchSpecification
import spray.json.DefaultJsonProtocol

case class BatchDTO(batchID: BatchEntity.ID,
                    timestamp: Instant,
                    batchSpecification: BatchSpecification,
                    auctions: List[Auction]) extends SprayJsonSupport with DefaultJsonProtocol

trait BatchDTOJsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
//  implicit val batchSpecificationFormat = jsonFormat15(BatchSpecification)
//  implicit val priceFormat = jsonFormat2(Price)
//  implicit val bidFormat = jsonFormat5(Bid)
//  implicit val auctionFormat = jsonFormat18(Auction)
}
