package com.fferrari.escommon

import akka.actor.typed.ActorRef
import akka.persistence.typed.scaladsl.{Effect, EffectBuilder, EventSourcedBehavior, ReplyEffect}
import com.fferrari.escommon.PersistentEntity.CommandExpectingReply

abstract class PersistentEntity[ID, InnerState, C[R] <: EntityCommand[ID, InnerState, R], E <: EntityEvent[ID]](implicit initialProcessor: InitialCommandProcessor[C, E],
                                                                                                                processor: CommandProcessor[InnerState, C, E],
                                                                                                                initialApplier: InitialEventApplier[InnerState, E],
                                                                                                                applier: EventApplier[InnerState, E]) {
  sealed trait OuterState
  case class Initialized(state: InnerState) extends OuterState
  case class Uninitialized(id: ID) extends OuterState

  type Command = CommandExpectingReply[_, InnerState, C]

  private val commandHandler: (OuterState, Command) => ReplyEffect[E, OuterState] =
    (entityState, command) => {
      entityState match {
        case _: Uninitialized =>
          val events = initialProcessor.process(command.command)
          command.uninitializedReplyAfter(if (events.nonEmpty) Effect.persist(events) else Effect.none)
        case Initialized(innerState) =>
          val events = processor.process(innerState, command.command)
          command.initializedReplyAfter(if (events.nonEmpty) Effect.persist(events) else Effect.none, innerState)
      }
    }

  private val eventHandler: (OuterState, E) => OuterState = { (entityState, event) =>
    entityState match {
      case uninitialized@Uninitialized(_) =>
        initialApplier.apply(event).map(Initialized).getOrElse[OuterState](uninitialized)
      case Initialized(state) => Initialized(applier.apply(state, event))
    }
  }

  protected def configureEntityBehavior(
                                         id: ID,
                                         behavior: EventSourcedBehavior[Command, E, OuterState]
                                       ): EventSourcedBehavior[Command, E, OuterState]

  private def createEventSourcedBehavior(entityID: ID) =
    EventSourcedBehavior(
      entityTypeKey,
      entityID.toString,
      Uninitialized(entityID),
      commandHandler,
      eventHandler,
    )
}

trait ExpectingReply[R]

object PersistentEntity {
  case class CommandExpectingReply[R, InnerState, C[Reply] <: EntityCommand[_, InnerState, Reply]]
  (command: C[R])
  (val replyTo: ActorRef[R]) extends ExpectingReply[R] {
    def initializedReplyAfter[E, S](effect: EffectBuilder[E, S], state: InnerState): ReplyEffect[E, S] =
      effect.thenReply(this)(_ => command.initializedReply(state))

    def uninitializedReplyAfter[E, S](effect: EffectBuilder[E, S]): ReplyEffect[E, S] =
      effect.thenReply(this)(_ => command.uninitializedReply)
  }
}
