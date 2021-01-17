package com.fferrari.batchmanager.application

import akka.actor.typed.Behavior
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.Behaviors
import akka.persistence.typed.PersistenceId
import com.fferrari.batchmanager.domain.BatchManagerEntity
import com.fferrari.batchmanager.domain.BatchManagerEntity.Command

object BatchManagerActor {
  val actorName = "batch-manager"

  val BatchManagerServiceKey = ServiceKey[Command]("batchManagerService")

  def apply: Behavior[BatchManagerEntity.Command] =
    Behaviors.setup { implicit context =>
      context.log.info("Starting")

      // Register with the Receptionist
      context.system.receptionist ! Receptionist.Register(BatchManagerServiceKey, context.self)

      BatchManagerEntity(PersistenceId.ofUniqueId(actorName))
    }
}
