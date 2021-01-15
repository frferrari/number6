package com.fferrari.batchscheduler.domain.es

import akka.Done
import akka.actor.typed.ActorRef
import akka.pattern.StatusReply
import com.fferrari.batchscheduler.domain.es.BatchSchedulerEvent.{BatchSchedulerAdded, BatchSchedulerProcessed, BatchSpecificationPaused, LastUrlUpdated}

sealed trait BatchSchedulerCommand

object BatchSchedulerCommand {
  final case class AddBatchScheduler(id: String,
                                     name: String,
                                     description: String,
                                     providerId: String,
                                     url: String,
                                     intervalSeconds: Long,
                                     replyTo: ActorRef[StatusReply[Done]]) extends BatchSchedulerCommand {
    def toBatchSpecificationAdded: BatchSchedulerAdded = {
      BatchSchedulerAdded(id, name, description, providerId, url, intervalSeconds)
    }
  }

  final case object ProcessBatchScheduler extends BatchSchedulerCommand {
    def toBatchSpecificationProcessed(id: String,
                                      name: String,
                                      description: String,
                                      providerId: String,
                                      url: String,
                                      intervalSeconds: Long): BatchSchedulerProcessed = {
      BatchSchedulerProcessed(id, name, description, providerId, url, intervalSeconds)
    }
  }

  final case class UpdateLastUrl(batchSpecificationId: String, lastUrlScraped: String) extends BatchSchedulerCommand {
    def toLastUrlUpdated: LastUrlUpdated =
      LastUrlUpdated(batchSpecificationId, lastUrlScraped)
  }

  final case class PauseBatchSpecification(batchSpecificationId: String) extends BatchSchedulerCommand {
    def toBatchSpecificationPaused: BatchSpecificationPaused =
      BatchSpecificationPaused(batchSpecificationId)
  }

  // final case class PauseProvider(provider: String) extends Command
}
