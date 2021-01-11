package com.fferrari.actor

import akka.Done
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.pattern.StatusReply
import akka.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit
import com.fferrari.model.BatchSpecification
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.wordspec.AnyWordSpecLike

object AkkaTestConfig {
  val config: Config =
    ConfigFactory
      .defaultApplication()
      .withOnlyPath("akka.actor")
}

class BatchSchedulerSpec extends ScalaTestWithActorTestKit(EventSourcedBehaviorTestKit.config.withFallback(AkkaTestConfig.config))
  with AnyWordSpecLike
  with BeforeAndAfterEach
  with BatchSchedulerSpecFixture {

  private val eventSourcedTestKit =
    EventSourcedBehaviorTestKit[BatchScheduler.Command, BatchScheduler.Event, BatchScheduler.State](
      system,
      BatchScheduler())

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    eventSourcedTestKit.clear()
  }

  "BatchScheduler" must {
    "SUCCEED Adding a specification" in {
      val result = eventSourcedTestKit.runCommand[StatusReply[Done]](BatchScheduler.AddBatchSpecification(batchSpecification1, _))
      result.reply shouldBe StatusReply.Ack
      result.event shouldBe BatchScheduler.BatchSpecificationAdded(batchSpecification1)
      result.stateOfType[BatchScheduler.State].batchSpecifications should contain theSameElementsAs (List(batchSpecification1))
    }
  }
}

trait BatchSchedulerSpecFixture {
  val batchSpecification1 = BatchSpecification("spec1", "specification 1", "provider1", "http://provider.com/url1", 60)
  val batchSpecification2 = BatchSpecification("spec2", "specification 2", "provider1", "http://provider.com/url2", 60)
}
