package com.fferrari.pricescraper.batch.domain

import java.time.Instant
import java.util.UUID

import akka.Done
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.pattern.StatusReply
import akka.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit

import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.wordspec.AnyWordSpecLike

import com.fferrari.pricescraper.batch.application
import com.fferrari.pricescraper.batch.domain.BatchCommand._
import com.fferrari.pricescraper.batch.domain.BatchEvent._
import com.fferrari.pricescraper.batch.domain.Batch.ActiveBatch
import com.fferrari.pricescraper.batchmanager.domain.BatchSpecification
import com.fferrari.pricescraper.auction.domain.{Auction, Bid, Price}
import com.fferrari.pricescraper.common.Clock

object BatchTestConfig {
  val config: Config =
    ConfigFactory
      .defaultApplication()
      .withOnlyPath("akka.actor")
}

class BatchSpec
  extends ScalaTestWithActorTestKit(EventSourcedBehaviorTestKit.config.withFallback(BatchTestConfig.config))
    with AnyWordSpecLike
    with BeforeAndAfterEach
    with BatchEntitySpecFixture {

  private val eventSourcedTestKit =
    EventSourcedBehaviorTestKit[BatchCommand, BatchEvent, Batch](
      system,
      application.BatchActor(java.util.UUID.randomUUID()))

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    eventSourcedTestKit.clear()
  }

  "Batch" should {
    "SUCCEED to Create when in [EmptyBatch] state" in {
      val result = eventSourcedTestKit.runCommand[StatusReply[Batch.BatchID]](CreateBatch(batchSpecification1, Nil, _))
      result.reply.isSuccess shouldBe true

      result.eventOfType[BatchCreated].batchSpecification.batchSpecificationID shouldBe batchSpecification1.batchSpecificationID
      result.eventOfType[BatchCreated].auctions shouldBe Nil

      result.stateOfType[ActiveBatch].batchSpecification.batchSpecificationID shouldBe batchSpecification1.batchSpecificationID
      result.stateOfType[ActiveBatch].auctions shouldBe Nil
    }

    "SUCCEED to MatchAuction when in [ActiveBatch] state" in {
      val matchID = UUID.randomUUID()
      val auctions = List(auction1, auction2)
      val result = eventSourcedTestKit.runCommand[StatusReply[Batch.BatchID]](CreateBatch(batchSpecification1, auctions, _))
      val batchID = result.eventOfType[BatchCreated].batchID
      val result1 = eventSourcedTestKit.runCommand[StatusReply[Done]](MatchAuction(batchID, auctionID2, matchID, _))
      result1.reply shouldBe StatusReply.Ack

      result1.eventOfType[AuctionMatched].auctionID shouldBe auctionID2
      result1.eventOfType[AuctionMatched].itemID shouldBe matchID

      result1.stateOfType[ActiveBatch].batchSpecification.batchSpecificationID shouldBe batchSpecification1.batchSpecificationID
      result1.stateOfType[ActiveBatch].auctions should contain theSameElementsAs List(auction1, auction2.copy(matchID = Some(matchID)))
    }
  }
}

trait BatchEntitySpecFixture {
  val now: Instant = Clock.now
  val batchSpecificationID1: BatchSpecification.BatchSpecificationID = UUID.randomUUID()
  val auctionID1: Auction.AuctionID = UUID.randomUUID()
  val auctionID2: Auction.AuctionID = UUID.randomUUID()

  val auction1: Auction = Auction(
    auctionID1,
    Auction.AuctionBidType,
    batchSpecificationID1,
    s"EXT-$auctionID1", None, "", "", isSold = true, "", "",
    Price(1.0, "EUR"), Some(Price(1.0, "EUR")),
    now.minusSeconds(3600), Some(now),
    "", "",
    List(Bid("user1", Price(1.0, "EUR"), 1, isAutomaticBid = false, now)), Auction.NotChecked)

  val auction2: Auction = Auction(
    auctionID2,
    Auction.AuctionBidType,
    batchSpecificationID1,
    s"EXT$auctionID2", None, "", "", isSold = true, "", "",
    Price(1.0, "EUR"), Some(Price(1.0, "EUR")),
    now.minusSeconds(3600), Some(now),
    "", "",
    List(Bid("user1", Price(1.0, "EUR"), 1, isAutomaticBid = false, now)), Auction.NotChecked)

  val batchSpecification1: BatchSpecification = BatchSpecification(
    UUID.randomUUID(),
    "bs1",
    "bsdesc1",
    "https://www.foo.com",
    "provider1",
    60,
    Clock.now,
    paused = false,
    None,
    None,
    None,
    None,
    None,
    None,
    None
  )
}