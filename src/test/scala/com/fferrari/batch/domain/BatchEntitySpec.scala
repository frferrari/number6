package com.fferrari.batch.domain

import java.util.UUID

import akka.Done
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.pattern.StatusReply
import akka.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit
import com.fferrari.auction.domain.{Auction, Bid, Price}
import com.fferrari.batch.application.BatchActor
import com.fferrari.common.Clock
import com.typesafe.config.{Config, ConfigFactory, ConfigValue}
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
    EventSourcedBehaviorTestKit[BatchEntity.Command, BatchEntity.Event, BatchEntity.Batch](
      system,
      BatchActor(java.util.UUID.randomUUID()))

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    eventSourcedTestKit.clear()
  }

  "Batch" must {
    "SUCCEED to Create when in [EmptyBatch] state" in {
      val batchID = UUID.randomUUID()
      val batchSpecificationID = UUID.randomUUID()
      val result = eventSourcedTestKit.runCommand[StatusReply[Done]](BatchEntity.Create(batchID, batchSpecificationID, Nil, _))
      result.reply shouldBe StatusReply.Ack
      result.stateOfType[BatchEntity.ActiveBatch].batchID shouldBe batchID
      result.stateOfType[BatchEntity.ActiveBatch].batchSpecificationID shouldBe batchSpecificationID
      result.stateOfType[BatchEntity.ActiveBatch].auctions shouldBe Nil
      result.eventOfType[BatchEntity.Created].batchID shouldBe batchID
      result.eventOfType[BatchEntity.Created].batchSpecificationID shouldBe batchSpecificationID
      result.eventOfType[BatchEntity.Created].auctions shouldBe Nil
    }

    "SUCCEED to MatchAuction when in [ActiveBatch] state" in {
      val batchID = UUID.randomUUID()
      val batchSpecificationID = UUID.randomUUID()
      val matchID = UUID.randomUUID()
      val auctions = List(auction1, auction2)
      eventSourcedTestKit.runCommand[StatusReply[Done]](BatchEntity.Create(batchID, batchSpecificationID, auctions, _))
      val result = eventSourcedTestKit.runCommand[StatusReply[Done]](BatchEntity.MatchAuction(auctionID2, matchID, _))
      result.reply shouldBe StatusReply.Ack
      result.stateOfType[BatchEntity.ActiveBatch].batchID shouldBe batchID
      result.stateOfType[BatchEntity.ActiveBatch].batchSpecificationID shouldBe batchSpecificationID
      result.stateOfType[BatchEntity.ActiveBatch].auctions should contain theSameElementsAs List(auction1, auction2.copy(matchID = Some(matchID)))
      result.eventOfType[BatchEntity.AuctionMatched].auctionID shouldBe auctionID2
      result.eventOfType[BatchEntity.AuctionMatched].matchID shouldBe matchID
    }
  }
}

trait BatchEntitySpecFixture {
  val now = Clock.now
  val batchSpecificationID1 = UUID.randomUUID()
  val auctionID1 = UUID.randomUUID()
  val auctionID2 = UUID.randomUUID()

  val auction1 = Auction(
    auctionID1,
    Auction.AuctionBidType,
    batchSpecificationID1,
    s"EXT-$auctionID1", None, "", "", isSold = true, "", "",
    Price(1.0, "EUR"), Some(Price(1.0, "EUR")),
    now.minusSeconds(3600), Some(now),
    "", "",
    List(Bid("user1", Price(1.0, "EUR"), 1, isAutomaticBid = false, now)), Auction.NotChecked)

  val auction2 = Auction(
    auctionID2,
    Auction.AuctionBidType,
    batchSpecificationID1,
    s"EXT$auctionID2", None, "", "", isSold = true, "", "",
    Price(1.0, "EUR"), Some(Price(1.0, "EUR")),
    now.minusSeconds(3600), Some(now),
    "", "",
    List(Bid("user1", Price(1.0, "EUR"), 1, isAutomaticBid = false, now)), Auction.NotChecked)
}