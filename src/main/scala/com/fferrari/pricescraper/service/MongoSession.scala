package com.fferrari.pricescraper.service
import scala.concurrent.Future
import reactivemongo.api.{AsyncDriver, MongoConnection}

object MongoSession {
  val mongoDriver = new reactivemongo.api.AsyncDriver

  val connection3: Future[MongoConnection] = mongoDriver.connect(List("localhost"))
}
