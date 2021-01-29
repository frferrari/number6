package com.fferrari.pricescraper

import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.scaladsl.AkkaManagement
import akka.util.Timeout
import com.fferrari.pricescraper.PriceScraper.Command
import com.fferrari.pricescraper.batchmanager.application.BatchManagerActor
import com.fferrari.pricescraper.batchmanager.domain.BatchManagerEntity
import com.fferrari.pricescraper.service.PriceScraperServiceImpl

import scala.concurrent.duration._

object PriceScraper {

  sealed trait Command

  def main(args: Array[String]): Unit = {
    ActorSystem(PriceScraper(), "number6")
  }

  def apply(): Behavior[Command] =
    Behaviors.setup { context =>
      new PriceScraper(context)
    }
}

class PriceScraper(context: ActorContext[Command]) extends AbstractBehavior[Command](context) {
  val system = context.system

  AkkaManagement(system).start()
  ClusterBootstrap(system).start()

  implicit val askTimeout: Timeout = 3.seconds

  // Start the batch manager actor
  val batchManager: ActorRef[BatchManagerEntity.Command] = context.spawn(BatchManagerActor.apply, BatchManagerActor.actorName)
  batchManager.ask(BatchManagerEntity.Create)(askTimeout, context.system.scheduler)

  val grpcInterface = system.settings.config.getString("shopping-cart-service.grpc.interface")
  val grpcPort = system.settings.config.getInt("shopping-cart-service.grpc.port")
  val grpcService = new PriceScraperServiceImpl(batchManager, context)
  PriceScraperServer.start(grpcInterface, grpcPort, system, grpcService)

  override def onMessage(msg: Command): Behavior[Command] =
    this
}

/*
object PriceScraper
  extends SpecificationJsonProtocol
    with SprayJsonSupport
    with App {

  sealed trait Command

  implicit val actorSystem: ActorSystem[Command] = ActorSystem(mainBehavior, "number6")
  implicit val executionContext: ExecutionContextExecutor = actorSystem.executionContext

  def mainBehavior: Behavior[Command] = Behaviors.setup { context =>
    implicit val askTimeout: Timeout = 3.seconds

    // Start the batch manage actor
    val batchManager: ActorRef[BatchManagerEntity.Command] =
      context.spawn(BatchManagerActor.apply, BatchManagerActor.actorName)
    batchManager.ask(BatchManagerEntity.Create)(askTimeout, context.system.scheduler)

    val routes: Route =
      path("add") {
        post {
          entity(as[Specification]) { batchSpecification =>
            onComplete {
              batchManager.ask(BatchManagerEntity.AddBatchSpecification(
                batchSpecification.name,
                batchSpecification.description,
                batchSpecification.url,
                batchSpecification.provider,
                batchSpecification.intervalSeconds, _)
              )
            } { _ => complete(StatusCodes.OK) }
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
              batchManager.ask(BatchManagerEntity.PauseBatchSpecification(java.util.UUID.fromString(batchSpecificationID), _))
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
*/