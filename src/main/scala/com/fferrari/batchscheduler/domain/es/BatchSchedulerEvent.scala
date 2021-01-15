package com.fferrari.batchscheduler.domain.es

import com.fferrari.batchscheduler.domain.BatchSpecification

sealed trait BatchSchedulerEvent

object BatchSchedulerEvent {
  final case class BatchSchedulerAdded(id: String,
                                       name: String,
                                       description: String,
                                       providerId: String,
                                       url: String,
                                       intervalSeconds: Long) extends BatchSchedulerEvent
  final case class BatchSchedulerProcessed(id: String,
                                           name: String,
                                           description: String,
                                           providerId: String,
                                           url: String,
                                           intervalSeconds: Long) extends BatchSchedulerEvent
  final case class LastUrlUpdated(batchSpecificationId: String, lastUrl: String) extends BatchSchedulerEvent
  final case class BatchSpecificationPaused(batchSpecificationId: String) extends BatchSchedulerEvent
}
