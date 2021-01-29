package com.fferrari.pricescraper.batch.domain

import java.time.Instant
import java.util.UUID

import akka.Done
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.pattern.StatusReply
import akka.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit
import com.fferrari.pricescraper.auction.domain.{Auction, Bid, Price}
import com.fferrari.pricescraper.batch.application
import com.fferrari.pricescraper.batch.domain.BatchEntity._
import com.fferrari.pricescraper.common.Clock
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.wordspec.AnyWordSpecLike

object BatchEntityTestConfig {
  val config: Config =
    ConfigFactory
      .defaultApplication()
      .withOnlyPath("akka.actor")
}

class BatchEntitySpec extends ScalaTestWithActorTestKit(EventSourcedBehaviorTestKit.config.withFallback(BatchEntityTestConfig.config))
  with AnyWordSpecLike
  with BeforeAndAfterEach
  with BatchEntitySpecFixture {

  private val eventSourcedTestKit =
    EventSourcedBehaviorTestKit[Command, Event, Batch](
      system,
      application.BatchActor(java.util.UUID.randomUUID()))

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    eventSourcedTestKit.clear()
  }

  "Batch" should {
    "SUCCEED to Create when in [EmptyBatch] state" in {
      val batchID = UUID.randomUUID()
      val batchSpecificationID = UUID.randomUUID()
      val result = eventSourcedTestKit.runCommand[StatusReply[Done]](Create(batchID, batchSpecificationID, Nil, _))
      result.reply shouldBe StatusReply.Ack
      result.eventOfType[Created].batchID shouldBe batchID
      result.eventOfType[Created].batchSpecificationID shouldBe batchSpecificationID
      result.eventOfType[Created].auctions shouldBe Nil
      result.stateOfType[ActiveBatch].batchID shouldBe batchID
      result.stateOfType[ActiveBatch].batchSpecificationID shouldBe batchSpecificationID
      result.stateOfType[ActiveBatch].auctions shouldBe Nil
    }

    "SUCCEED to MatchAuction when in [ActiveBatch] state" in {
      val batchID = UUID.randomUUID()
      val batchSpecificationID = UUID.randomUUID()
      val matchID = UUID.randomUUID()
      val auctions = List(auction1, auction2)
      eventSourcedTestKit.runCommand[StatusReply[Done]](Create(batchID, batchSpecificationID, auctions, _))
      val result = eventSourcedTestKit.runCommand[StatusReply[Done]](MatchAuction(auctionID2, matchID, _))
      result.reply shouldBe StatusReply.Ack
      result.eventOfType[AuctionMatched].auctionID shouldBe auctionID2
      result.eventOfType[AuctionMatched].matchID shouldBe matchID
      result.stateOfType[ActiveBatch].batchID shouldBe batchID
      result.stateOfType[ActiveBatch].batchSpecificationID shouldBe batchSpecificationID
      result.stateOfType[ActiveBatch].auctions should contain theSameElementsAs List(auction1, auction2.copy(matchID = Some(matchID)))
    }

    "SUCCEED to Stop when in [ActiveBatch] state" in {
      val createResult = eventSourcedTestKit.runCommand[StatusReply[Done]](Create(UUID.randomUUID(), UUID.randomUUID(), Nil, _))
      val stopResult = eventSourcedTestKit.runCommand[StatusReply[Done]](Stop)
      stopResult.reply shouldBe StatusReply.Ack
    }
  }
}

trait BatchEntitySpecFixture {
  val now: Instant = Clock.now
  val batchSpecificationID1: ID = UUID.randomUUID()
  val auctionID1: ID = UUID.randomUUID()
  val auctionID2: ID = UUID.randomUUID()

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
}