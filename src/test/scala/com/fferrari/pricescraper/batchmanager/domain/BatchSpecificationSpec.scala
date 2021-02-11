package com.fferrari.pricescraper.batchmanager.domain

import java.util.UUID

import com.fferrari.pricescraper.common.Clock
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.matchers.should.Matchers

class BatchSpecificationSpec
  extends AnyWordSpecLike
    with Matchers
    with BatchSpecificationSpecFixture {
  "BatchSpecification" should {
    "NOT need an update when it's NOT paused AND it's been updated less than intervalSeconds ago" in {
      batchSpecification.copy(
        paused = false,
        updatedAt = now,
        intervalSeconds = updateInterval
      ).needsUpdate(now) shouldBe false
    }

    "NEED an update when it's NOT paused AND it's been updated too long ago" in {
      batchSpecification.copy(
        paused = false,
        updatedAt = now.minusSeconds(updateInterval + 1),
        intervalSeconds = updateInterval
      ).needsUpdate(now) shouldBe true
    }

    "NOT need an update when it's paused AND it's been updated too long ago" in {
      batchSpecification.copy(
        paused = true,
        updatedAt = now.minusSeconds(updateInterval + 1),
        intervalSeconds = updateInterval
      ).needsUpdate(now) shouldBe false
    }

    "NOT need an update when it's paused AND it's been updated less than intervalSeconds ago" in {
      batchSpecification.copy(
        paused = true,
        updatedAt = now,
        intervalSeconds = updateInterval
      ).needsUpdate(now) shouldBe false
    }
  }
}

trait BatchSpecificationSpecFixture {
  val updateInterval = 60
  val now = Clock.now
  val batchSpecification = BatchSpecification(
    batchSpecificationID = UUID.randomUUID(),
    name = "bs1",
    description = "desc1",
    listingPageUrl = "www.example.com",
    provider = "provider1",
    intervalSeconds = updateInterval,
    updatedAt = Clock.now,
    paused = false,
    lastUrlVisited = None,
    familyId = None,
    countryId = None,
    topicId = None,
    startYear = None,
    endYear = None,
    conditionId = None
  )
}