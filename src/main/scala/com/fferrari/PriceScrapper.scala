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
import com.fferrari.actor.{BatchManagerActor, BatchSchedulerActor}
import com.fferrari.model.{BatchSpecification, BatchSpecificationJsonProtocol}

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.DurationInt
import scala.io.StdIn

object PriceScrapper
  extends BatchSpecificationJsonProtocol
    with SprayJsonSupport
    with App {

  sealed trait Command

  implicit val actorSystem: ActorSystem[Command] = ActorSystem(mainBehavior, "number6")
  implicit val executionContext: ExecutionContextExecutor = actorSystem.executionContext

  def mainBehavior: Behavior[Command] = Behaviors.setup { context =>
    // Start the batch manage actor
    val batchManager: ActorRef[BatchManagerActor.Command] =
      context.spawn(BatchManagerActor(), BatchManagerActor.actorName)

    // Start the batch scheduler actor
    val batchScheduler: ActorRef[BatchSchedulerActor.Command] =
      context.spawn(BatchSchedulerActor(), BatchSchedulerActor.actorName)

    val routes: Route =
      path("specification/add") {
        post {
          entity(as[BatchSpecification]) { batchSpecification =>
            onComplete {
              implicit val timeout: Timeout = 3.seconds
              batchScheduler.ask(ref => BatchSchedulerActor.AddBatchSpecification(batchSpecification, ref))
            } { x =>
              println(s"onComplete ====> $x")
              complete(StatusCodes.OK)
            }
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

//  override def main(args: Array[String]): Unit = {
//    println("===> readline")
//    StdIn.readLine()
//    StdIn.readLine()
//    StdIn.readLine()
//    ()
//  }
}
