package com.fferrari.batchmanager.domain

import java.util.UUID

import akka.Done
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.pattern.StatusReply
import akka.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit
import com.fferrari.batchmanager.application.BatchManagerActor
import com.fferrari.batchmanager.domain.BatchManagerEntity._
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.wordspec.AnyWordSpecLike

object BatchManagerTestConfig {
  val config: Config =
    ConfigFactory
      .defaultApplication()
      .withOnlyPath("akka.actor")
}

class BatchManagerSpec extends ScalaTestWithActorTestKit(EventSourcedBehaviorTestKit.config.withFallback(BatchManagerTestConfig.config))
  with AnyWordSpecLike
  with BeforeAndAfterEach {

  private val eventSourcedTestKit =
    EventSourcedBehaviorTestKit[Command, Event, BatchManager](
      system,
      BatchManagerActor.apply)

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    eventSourcedTestKit.clear()
  }

  "BatchManager" should {
    "SUCCEED to Create when in [EmptyBatchManager] state" in {
      val result = eventSourcedTestKit.runCommand[StatusReply[Done]](Create)
      result.reply shouldBe StatusReply.Ack
      result.stateOfType[ActiveBatchManager].batchSpecifications shouldBe Nil
    }

    "SUCCEED to AddBatchSpecification when in [ActiveBatchManager] state" in {
      eventSourcedTestKit.runCommand[StatusReply[Done]](Create)

      val addResult = eventSourcedTestKit.runCommand[StatusReply[Done]](AddBatchSpecification("spec1", "desc1", "url1", "provider1", 60, _))
      addResult.reply shouldBe StatusReply.Ack

      val batchSpecifications = addResult.stateOfType[ActiveBatchManager].batchSpecifications

      val expectedBatchSpecification = BatchSpecification(batchSpecifications.head.batchSpecificationID, "spec1", "desc1", "url1", "provider1", 60, batchSpecifications.head.updatedAt, false, None)
      addResult.stateOfType[ActiveBatchManager].batchSpecifications shouldBe List(expectedBatchSpecification)
    }

    "SUCCEED to AddBatchSpecification twice when in [ActiveBatchManager] state" in {
      eventSourcedTestKit.runCommand[StatusReply[Done]](Create)

      val addResult1 = eventSourcedTestKit.runCommand[StatusReply[Done]](AddBatchSpecification("spec1", "desc1", "url1", "provider1", 60, _))
      addResult1.reply shouldBe StatusReply.Ack

      val addResult2 = eventSourcedTestKit.runCommand[StatusReply[Done]](AddBatchSpecification("spec2", "desc2", "url2", "provider1", 30, _))
      addResult2.reply shouldBe StatusReply.Ack
      addResult2.eventOfType[BatchSpecificationAdded].batchSpecificationID shouldBe addResult2.stateOfType[ActiveBatchManager].batchSpecifications.filter(_.name == "spec2").head.batchSpecificationID

      val batchSpecifications = addResult2.stateOfType[ActiveBatchManager].batchSpecifications

      batchSpecifications.size shouldBe 2

      val bs1: BatchSpecification = batchSpecifications.filter(_.name == "spec1").head
      val bs2: BatchSpecification = batchSpecifications.filter(_.name == "spec2").head

      val expectedBatchSpecifications = List(
        BatchSpecification(bs1.batchSpecificationID, "spec1", "desc1", "url1", "provider1", 60, bs1.updatedAt, false, None),
        BatchSpecification(bs2.batchSpecificationID, "spec2", "desc2", "url2", "provider1", 30, bs2.updatedAt, false, None)
      )
      batchSpecifications should contain theSameElementsAs expectedBatchSpecifications
    }

    "FAIL to AddBatchSpecification when in [ActiveBatchManager] state for an already existing batch specification name" in {
      eventSourcedTestKit.runCommand[StatusReply[Done]](Create)
      eventSourcedTestKit.runCommand[StatusReply[Done]](AddBatchSpecification("spec1", "desc1", "url1", "provider1", 60, _))
      val result = eventSourcedTestKit.runCommand[StatusReply[Done]](AddBatchSpecification("spec1", "desc2", "url2", "provider2", 30, _))
      result.reply.isError shouldBe true
    }

    "SUCCEED to UpdateLastUrlVisited when in [ActiveBatchManager] state" in {
      eventSourcedTestKit.runCommand[StatusReply[Done]](Create)

      val addResult1 = eventSourcedTestKit.runCommand[StatusReply[Done]](AddBatchSpecification("spec1", "desc1", "url1", "provider1", 60, _))
      addResult1.reply shouldBe StatusReply.Ack

      val addResult2 = eventSourcedTestKit.runCommand[StatusReply[Done]](AddBatchSpecification("spec2", "desc2", "url2", "provider1", 30, _))
      addResult2.reply shouldBe StatusReply.Ack

      val batchSpecifications = addResult2.stateOfType[ActiveBatchManager].batchSpecifications
      batchSpecifications.size shouldBe 2

      val bs2: BatchSpecification = batchSpecifications.filter(_.name == "spec2").head

      val updateResult = eventSourcedTestKit.runCommand[StatusReply[Done]](UpdateLastUrlVisited(bs2.batchSpecificationID, "last", _))
      updateResult.reply shouldBe StatusReply.Ack
      updateResult.stateOfType[ActiveBatchManager].batchSpecifications.filter(_.name == bs2.name).head.lastUrlVisited shouldBe Some("last")
    }

    "FAIL to UpdateLastUrlVisited when in [ActiveBatchManager] state for an unknown batch specification" in {
      eventSourcedTestKit.runCommand[StatusReply[Done]](Create)

      val addResult1 = eventSourcedTestKit.runCommand[StatusReply[Done]](AddBatchSpecification("spec1", "desc1", "url1", "provider1", 60, _))
      addResult1.reply shouldBe StatusReply.Ack

      val updateResult = eventSourcedTestKit.runCommand[StatusReply[Done]](UpdateLastUrlVisited(UUID.randomUUID(), "last", _))
      updateResult.reply.isError shouldBe true
    }
  }
}