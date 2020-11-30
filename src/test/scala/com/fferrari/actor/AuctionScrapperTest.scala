package com.fferrari.actor
import akka.actor.testkit.typed.scaladsl.ActorTestKit

import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
class AuctionScrapperTest extends AnyWordSpec
  with BeforeAndAfterAll
  with Matchers {
  val testKit = ActorTestKit()

  override def afterAll(): Unit = testKit.shutdownTestKit()

  val pinger = testKit.spawn(AuctionScrapperActor(), "auctionScrapperTest")
  // val probe = testKit.createTestProbe[AuctionScrapperActor.Pong]()
  // pinger ! Echo.Ping("hello", probe.ref)
  // probe.expectMessage(Echo.Pong("hello"))
}
*/