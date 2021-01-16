package com.fferrari.batchspecification.domain

import java.util.UUID

object BatchSpecificationEntity {
  sealed trait BatchSpecificationEvent

  sealed trait BatchSpecification {
    def applyEvent(event: BatchSpecificationEvent)
  }

  type ID = UUID
}
