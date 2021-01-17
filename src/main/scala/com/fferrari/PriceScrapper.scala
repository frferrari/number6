package com.fferrari

import akka.actor.typed.scaladsl.AskPattern.{Askable, schedulerFromActorSystem}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.fferrari.batchmanager.application.BatchManagerActor
import com.fferrari.batchmanager.domain.BatchManagerEntity
import com.fferrari.batchscheduler.application.BatchSchedulerActor
import com.fferrari.batchscheduler.domain.BatchSchedulerEntity
import com.fferrari.common.{Specification, SpecificationJsonProtocol}

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.DurationInt
import scala.io.StdIn

object PriceScrapper
  extends SpecificationJsonProtocol
    with SprayJsonSupport
    with App {

  sealed trait Command

  implicit val actorSystem: ActorSystem[Command] = ActorSystem(mainBehavior, "number6")
  implicit val executionContext: ExecutionContextExecutor = actorSystem.executionContext

  def mainBehavior: Behavior[Command] = Behaviors.setup { context =>
    // Start the batch manage actor
    val batchManager: ActorRef[BatchManagerEntity.Command] =
      context.spawn(BatchManagerActor.apply, BatchManagerActor.actorName)

    // Start the batch scheduler actor
    val batchScheduler: ActorRef[BatchSchedulerEntity.Command] =
      context.spawn(BatchSchedulerActor.apply, BatchSchedulerActor.actorName)

    val routes: Route =
      path("add") {
        post {
          entity(as[Specification]) { batchSpecification =>
            onComplete {
              implicit val timeout: Timeout = 3.seconds
              batchScheduler.ask(BatchSchedulerEntity.AddBatchSpecification(
                batchSpecification.name,
                batchSpecification.description,
                batchSpecification.url,
                batchSpecification.provider,
                batchSpecification.intervalSeconds, _)
              )
            } { _ =>complete(StatusCodes.OK) }
          }
        }
      } ~ path("info") {
        get {
          complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, "<h1>Say hello to akka-http</h1>"))
        }
      } ~ path("pause") {
        put {
          entity(as[String]) { batchSpecificationID =>
            onComplete {
              implicit val timeout: Timeout = 3.seconds
              batchScheduler.ask(BatchSchedulerEntity.PauseBatchSpecification(java.util.UUID.fromString(batchSpecificationID), _))
            } { _ => complete(StatusCodes.OK) }
          }
        }
      }

    val bindingFuture = Http().newServerAt("localhost", 8080).bind(routes)

    println(s"Server online at http://localhost:8080/\nPress RETURN to stop...")

    Behaviors.empty
  }

  println("===> readline")
  StdIn.readLine()
  StdIn.readLine()
  StdIn.readLine()

  //  bindingFuture
  //    .flatMap(_.unbind()) // trigger unbinding from the port
  //    .onComplete(_ => actorSystem.terminate()) // and shutdown when done
}
