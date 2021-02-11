package com.fferrari.pricescraper.batchmanager.domain

import java.util.UUID

import akka.Done
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.pattern.StatusReply
import akka.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit
import com.fferrari.pricescraper.batchmanager.application.BatchManagerActor
import com.fferrari.pricescraper.batchmanager.domain.BatchManager.ActiveBatchManager
import com.fferrari.pricescraper.batchmanager.domain.BatchManagerCommand._
import com.fferrari.pricescraper.batchmanager.domain.BatchManagerEvent._
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.wordspec.AnyWordSpecLike

object BatchManagerTestConfig {
  val config: Config =
    ConfigFactory
      .defaultApplication()
      .withOnlyPath("akka.actor")
}

class BatchManagerSpec
  extends ScalaTestWithActorTestKit(EventSourcedBehaviorTestKit.config.withFallback(BatchManagerTestConfig.config))
  with AnyWordSpecLike
  with BeforeAndAfterEach {

  private val eventSourcedTestKit =
    EventSourcedBehaviorTestKit[BatchManagerCommand, BatchManagerEvent, BatchManager](
      system,
      BatchManagerActor.apply)

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    eventSourcedTestKit.clear()
  }

  "BatchManager" should {
    "SUCCEED to Create when in [EmptyBatchManager] state" in {
      val result = eventSourcedTestKit.runCommand[StatusReply[Done]](CreateBatchManager)
      result.reply shouldBe StatusReply.Ack
      result.stateOfType[ActiveBatchManager].batchSpecifications shouldBe Nil
    }

    "SUCCEED to AddBatchSpecification when in [ActiveBatchManager] state" in {
      eventSourcedTestKit.runCommand[StatusReply[Done]](CreateBatchManager)

      val addResult = eventSourcedTestKit.runCommand[StatusReply[BatchSpecification.BatchSpecificationID]](AddBatchSpecification("spec1", "desc1", "url1", "provider1", 60, _))
      println(s"=====> ${addResult.event}")
      addResult.reply.isSuccess shouldBe true

      val batchSpecifications = addResult.stateOfType[ActiveBatchManager].batchSpecifications

      val expectedBatchSpecification = BatchSpecification(batchSpecifications.head.batchSpecificationID, "spec1", "desc1", "url1", "provider1", 60, batchSpecifications.head.updatedAt, paused = false, None)
      addResult.stateOfType[ActiveBatchManager].batchSpecifications shouldBe List(expectedBatchSpecification)
    }

    "SUCCEED to AddBatchSpecification twice when in [ActiveBatchManager] state" in {
      eventSourcedTestKit.runCommand[StatusReply[Done]](CreateBatchManager)

      val addResult1 = eventSourcedTestKit.runCommand[StatusReply[BatchSpecification.BatchSpecificationID]](AddBatchSpecification("spec1", "desc1", "url1", "provider1", 60, _))
      addResult1.reply.isSuccess shouldBe true

      val addResult2 = eventSourcedTestKit.runCommand[StatusReply[BatchSpecification.BatchSpecificationID]](AddBatchSpecification("spec2", "desc2", "url2", "provider1", 30, _))
      addResult2.reply.isSuccess shouldBe true
      addResult2.eventOfType[BatchSpecificationAdded].batchSpecificationID shouldBe addResult2.stateOfType[ActiveBatchManager].batchSpecifications.filter(_.name == "spec2").head.batchSpecificationID

      val batchSpecifications = addResult2.stateOfType[ActiveBatchManager].batchSpecifications

      batchSpecifications.size shouldBe 2

      val bs1: BatchSpecification = batchSpecifications.filter(_.name == "spec1").head
      val bs2: BatchSpecification = batchSpecifications.filter(_.name == "spec2").head

      val expectedBatchSpecifications = List(
        BatchSpecification(bs1.batchSpecificationID, "spec1", "desc1", "url1", "provider1", 60, bs1.updatedAt, paused = false, None),
        BatchSpecification(bs2.batchSpecificationID, "spec2", "desc2", "url2", "provider1", 30, bs2.updatedAt, paused = false, None)
      )
      batchSpecifications should contain theSameElementsAs expectedBatchSpecifications
    }

    "FAIL to AddBatchSpecification when in [ActiveBatchManager] state for an already existing batch specification name" in {
      eventSourcedTestKit.runCommand[StatusReply[Done]](CreateBatchManager)
      eventSourcedTestKit.runCommand[StatusReply[BatchSpecification.BatchSpecificationID]](AddBatchSpecification("spec1", "desc1", "url1", "provider1", 60, _))
      val result = eventSourcedTestKit.runCommand[StatusReply[BatchSpecification.BatchSpecificationID]](AddBatchSpecification("spec1", "desc2", "url2", "provider2", 30, _))
      result.reply.isError shouldBe true
    }

    "SUCCEED to UpdateLastUrlVisited when in [ActiveBatchManager] state" in {
      eventSourcedTestKit.runCommand[StatusReply[Done]](CreateBatchManager)

      val addResult1 = eventSourcedTestKit.runCommand[StatusReply[BatchSpecification.BatchSpecificationID]](AddBatchSpecification("spec1", "desc1", "url1", "provider1", 60, _))
      addResult1.reply.isSuccess shouldBe true

      val addResult2 = eventSourcedTestKit.runCommand[StatusReply[BatchSpecification.BatchSpecificationID]](AddBatchSpecification("spec2", "desc2", "url2", "provider1", 30, _))
      addResult2.reply.isSuccess shouldBe true

      val batchSpecifications = addResult2.stateOfType[ActiveBatchManager].batchSpecifications
      batchSpecifications.size shouldBe 2

      val bs2: BatchSpecification = batchSpecifications.filter(_.name == "spec2").head

      val updateResult = eventSourcedTestKit.runCommand[StatusReply[Done]](UpdateLastUrlVisited(bs2.batchSpecificationID, "last", _))
      updateResult.reply shouldBe StatusReply.Ack
      updateResult.stateOfType[ActiveBatchManager].batchSpecifications.filter(_.name == bs2.name).head.lastUrlVisited shouldBe Some("last")
    }

    "FAIL to UpdateLastUrlVisited when in [ActiveBatchManager] state for an unknown batch specification" in {
      eventSourcedTestKit.runCommand[StatusReply[Done]](CreateBatchManager)

      val addResult1 = eventSourcedTestKit.runCommand[StatusReply[BatchSpecification.BatchSpecificationID]](AddBatchSpecification("spec1", "desc1", "url1", "provider1", 60, _))
      addResult1.reply.isSuccess shouldBe true

      val updateResult = eventSourcedTestKit.runCommand[StatusReply[Done]](UpdateLastUrlVisited(UUID.randomUUID(), "last", _))
      updateResult.reply.isError shouldBe true
    }

    "SUCCEED to PauseBatchSpecification then ReleaseBatchSpecification when in [ActiveBatchManager] state" in {
      eventSourcedTestKit.runCommand[StatusReply[Done]](CreateBatchManager)

      val addResult1 = eventSourcedTestKit.runCommand[StatusReply[BatchSpecification.BatchSpecificationID]](AddBatchSpecification("spec1", "desc1", "url1", "provider1", 60, _))
      addResult1.reply.isSuccess shouldBe true

      val addResult2 = eventSourcedTestKit.runCommand[StatusReply[BatchSpecification.BatchSpecificationID]](AddBatchSpecification("spec2", "desc2", "url2", "provider1", 30, _))
      addResult2.reply.isSuccess shouldBe true

      val batchSpecifications = addResult2.stateOfType[ActiveBatchManager].batchSpecifications
      batchSpecifications.size shouldBe 2

      val bs2: BatchSpecification = batchSpecifications.filter(_.name == "spec2").head

      val pauseResult = eventSourcedTestKit.runCommand[StatusReply[Done]](PauseBatchSpecification(bs2.batchSpecificationID, _))
      pauseResult.reply shouldBe StatusReply.Ack
      pauseResult.stateOfType[ActiveBatchManager].batchSpecifications.filter(_.name == bs2.name).head.paused shouldBe true
      pauseResult.eventOfType[BatchSpecificationPaused].batchSpecificationID shouldBe bs2.batchSpecificationID

      val releaseResult = eventSourcedTestKit.runCommand[StatusReply[Done]](ReleaseBatchSpecification(bs2.batchSpecificationID, _))
      releaseResult.reply shouldBe StatusReply.Ack
      releaseResult.stateOfType[ActiveBatchManager].batchSpecifications.filter(_.name == bs2.name).head.paused shouldBe false
      releaseResult.eventOfType[BatchSpecificationReleased].batchSpecificationID shouldBe bs2.batchSpecificationID
    }

    "SUCCEED to PauseProvider then ReleaseProvider when in [ActiveBatchManager] state" in {
      eventSourcedTestKit.runCommand[StatusReply[Done]](CreateBatchManager)

      val (provider1, provider2) = ("provider1", "provider2")
      val (spec1, spec2, spec3) = ("spec1", "spec2", "spec3")

      val addResult1 = eventSourcedTestKit.runCommand[StatusReply[BatchSpecification.BatchSpecificationID]](AddBatchSpecification(spec1, "desc1", "url1", provider1, 60, _))
      addResult1.reply.isSuccess shouldBe true

      val addResult2 = eventSourcedTestKit.runCommand[StatusReply[BatchSpecification.BatchSpecificationID]](AddBatchSpecification(spec2, "desc2", "url2", provider1, 30, _))
      addResult2.reply.isSuccess shouldBe true

      val addResult3 = eventSourcedTestKit.runCommand[StatusReply[BatchSpecification.BatchSpecificationID]](AddBatchSpecification(spec3, "desc3", "url3", provider2, 30, _))
      addResult3.reply.isSuccess shouldBe true

      val batchSpecifications = addResult3.stateOfType[ActiveBatchManager].batchSpecifications
      batchSpecifications.size shouldBe 3

      val bs1: BatchSpecification = batchSpecifications.filter(_.name == spec1).head
      val bs2: BatchSpecification = batchSpecifications.filter(_.name == spec2).head
      val bs3: BatchSpecification = batchSpecifications.filter(_.name == spec3).head

      val providerToPause = provider1

      val pauseResult = eventSourcedTestKit.runCommand[StatusReply[Done]](PauseProvider(providerToPause, _))
      pauseResult.reply shouldBe StatusReply.Ack
      pauseResult.stateOfType[ActiveBatchManager].batchSpecifications.filter(_.name == bs1.name).head.paused shouldBe true
      pauseResult.stateOfType[ActiveBatchManager].batchSpecifications.filter(_.name == bs2.name).head.paused shouldBe true
      pauseResult.stateOfType[ActiveBatchManager].batchSpecifications.filter(_.name == bs3.name).head.paused shouldBe false
      pauseResult.eventOfType[ProviderPaused].provider shouldBe providerToPause

      val releaseResult = eventSourcedTestKit.runCommand[StatusReply[Done]](ReleaseProvider(providerToPause, _))
      releaseResult.reply shouldBe StatusReply.Ack
      releaseResult.stateOfType[ActiveBatchManager].batchSpecifications.filter(_.name == bs1.name).head.paused shouldBe false
      releaseResult.stateOfType[ActiveBatchManager].batchSpecifications.filter(_.name == bs2.name).head.paused shouldBe false
      releaseResult.stateOfType[ActiveBatchManager].batchSpecifications.filter(_.name == bs3.name).head.paused shouldBe false
      releaseResult.eventOfType[ProviderReleased].provider shouldBe providerToPause
    }
  }
}
