package com.fferrari.actor

import akka.Done
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.pattern.StatusReply
import akka.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit
import com.fferrari.model.BatchSpecification
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.wordspec.AnyWordSpecLike

object BatchSchedulerTestConfig {
  val config: Config =
    ConfigFactory
      .defaultApplication()
      .withOnlyPath("akka.actor")
}

class BatchSchedulerSpec extends ScalaTestWithActorTestKit(EventSourcedBehaviorTestKit.config.withFallback(BatchSchedulerTestConfig.config))
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
    "SUCCEED Adding two different specifications" in {
      val result1 = eventSourcedTestKit.runCommand[StatusReply[Done]](BatchScheduler.AddBatchSpecification(batchSpecification1, _))
      result1.reply shouldBe StatusReply.Ack
      result1.event shouldBe BatchScheduler.BatchSpecificationAdded(batchSpecification1)
      result1.stateOfType[BatchScheduler.State].batchSpecifications should contain theSameElementsAs (List(batchSpecification1))

      val result2 = eventSourcedTestKit.runCommand[StatusReply[Done]](BatchScheduler.AddBatchSpecification(batchSpecification2, _))
      result2.reply shouldBe StatusReply.Ack
      result2.event shouldBe BatchScheduler.BatchSpecificationAdded(batchSpecification2)
      result2.stateOfType[BatchScheduler.State].batchSpecifications should contain theSameElementsAs (List(batchSpecification1, batchSpecification2))
    }
    "FAIL Adding an existing specification" in {
      eventSourcedTestKit.runCommand[StatusReply[Done]](BatchScheduler.AddBatchSpecification(batchSpecification1, _))
      val result = eventSourcedTestKit.runCommand[StatusReply[Done]](BatchScheduler.AddBatchSpecification(batchSpecification1, _))
      result.reply.isError shouldBe true
      result.hasNoEvents shouldBe true
    }

    "SUCCEED Updating the lastUrl for an existing batch specification" in {
      val lastUrl = "http://lasturl.com"
      eventSourcedTestKit.runCommand[StatusReply[Done]](BatchScheduler.AddBatchSpecification(batchSpecification1, _))
      eventSourcedTestKit.runCommand[StatusReply[Done]](BatchScheduler.AddBatchSpecification(batchSpecification2, _))
      val result = eventSourcedTestKit.runCommand[StatusReply[Done]](BatchScheduler.UpdateLastUrl(batchSpecification2.id, lastUrl, _))
      result.reply shouldBe StatusReply.Ack
      result.event shouldBe BatchScheduler.LastUrlUpdated(batchSpecification2.id, lastUrl)
      result.stateOfType[BatchScheduler.State].batchSpecifications should contain theSameElementsAs (List(batchSpecification1, batchSpecification2.copy(lastUrl)))
    }
    "FAIL Updating the lastUrl for an unknown batch specification" in {
      val lastUrl = "http://lasturl.com"
      eventSourcedTestKit.runCommand[StatusReply[Done]](BatchScheduler.AddBatchSpecification(batchSpecification1, _))
      val result = eventSourcedTestKit.runCommand[StatusReply[Done]](BatchScheduler.UpdateLastUrl(batchSpecification2.id, lastUrl, _))
      result.reply.isError shouldBe true
      result.hasNoEvents shouldBe true
    }

    "SUCCEED Pausing an existing batch specification" in {
      eventSourcedTestKit.runCommand[StatusReply[Done]](BatchScheduler.AddBatchSpecification(batchSpecification1, _))
      eventSourcedTestKit.runCommand[StatusReply[Done]](BatchScheduler.AddBatchSpecification(batchSpecification2, _))
      val result = eventSourcedTestKit.runCommand[StatusReply[Done]](BatchScheduler.PauseBatchSpecification(batchSpecification2.id, _))
      result.reply shouldBe StatusReply.Ack
      result.event shouldBe BatchScheduler.BatchSpecificationPaused(batchSpecification2.id)
      result.stateOfType[BatchScheduler.State].batchSpecifications should contain theSameElementsAs (List(batchSpecification1, batchSpecification2.copy(paused = true)))
    }
    "FAIL Pausing an unknown batch specification" in {
      eventSourcedTestKit.runCommand[StatusReply[Done]](BatchScheduler.AddBatchSpecification(batchSpecification1, _))
      val result = eventSourcedTestKit.runCommand[StatusReply[Done]](BatchScheduler.PauseBatchSpecification(batchSpecification2.id, _))
      result.reply.isError shouldBe true
      result.hasNoEvents shouldBe true
    }
  }
}

trait BatchSchedulerSpecFixture {
  val batchSpecification1 = BatchSpecification("spec1", "specification 1", "provider1", "http://provider.com/url1", 60)
  val batchSpecification2 = BatchSpecification("spec2", "specification 2", "provider1", "http://provider.com/url2", 60)
}
