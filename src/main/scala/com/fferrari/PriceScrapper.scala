package com.fferrari

import akka.actor.typed.scaladsl.AskPattern.{Askable, schedulerFromActorSystem}
import akka.actor.typed.scaladsl.{Behaviors, Routers}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, SupervisorStrategy}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.fferrari.actor.AuctionScrapperProtocol.PriceScrapperCommand
import com.fferrari.actor.{AuctionScrapperActor, BatchScheduler}
import com.fferrari.model.{BatchSpecification, BatchSpecificationJsonProtocol}

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.DurationInt
import scala.io.StdIn

object PriceScrapper
  extends BatchSpecificationJsonProtocol
    with SprayJsonSupport {

  sealed trait Command

  implicit val actorSystem: ActorSystem[Command] = ActorSystem(mainBehavior, "number6")
  implicit val executionContext: ExecutionContextExecutor = actorSystem.executionContext

  def mainBehavior: Behavior[Command] = Behaviors.setup { context =>
    // Start a pool of auction scrapper
    val pool = Routers.pool(poolSize = 5)(Behaviors.supervise(AuctionScrapperActor()).onFailure[Exception](SupervisorStrategy.restart))
    val auctionScrapperRouter: ActorRef[PriceScrapperCommand] = context.spawn(pool, AuctionScrapperActor.actorName)

    // Start a batch scheduler
    val batchScheduler = context.spawn(BatchScheduler(auctionScrapperRouter), BatchScheduler.actorName)

    val routes: Route =
      path("specification/add") {
        post {
          entity(as[BatchSpecification]) { batchSpecification =>
            onComplete {
              implicit val timeout: Timeout = 3.seconds
              batchScheduler.ask(ref => BatchScheduler.AddBatchSpecification(batchSpecification, ref))
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

  def main(args: Array[String]): Unit = {
    println("===> readline")
    StdIn.readLine()
    StdIn.readLine()
    StdIn.readLine()
    ()
  }
}
