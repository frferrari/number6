package com.fferrari.model

import spray.json._

case class BatchSpecification(id: String,
                              name: String,
                              description: String,
                              provider: String,
                              url: String,
                              intervalSeconds: Long,
                              paused: Boolean = false,
                              updatedAt: Long = 0L,
                              lastUrlScrapped: Option[String] = None) {
  def needsUpdate(now: java.time.Instant = java.time.Instant.now()): Boolean =
    ((updatedAt + intervalSeconds) > now.getEpochSecond || updatedAt == 0) && !paused
}

object BatchSpecification {
  def apply(name: String,
            description: String,
            provider: String,
            url: String,
            intervalSeconds: Long) =
    new BatchSpecification(
      java.util.UUID.randomUUID().toString,
      name,
      description,
      provider,
      url,
      intervalSeconds,
      paused = false,
      updatedAt = 0L,
      lastUrlScrapped = None)
}

trait BatchSpecificationJsonProtocol extends DefaultJsonProtocol {
  implicit object BatchSpecificationJsonFormat extends RootJsonFormat[BatchSpecification] {
    def write(bs: BatchSpecification): JsArray = {
      bs.lastUrlScrapped match {
        case Some(lastUrlScrapped) =>
          JsArray(
            JsString(bs.id),
            JsString(bs.name),
            JsString(bs.description),
            JsString(bs.provider),
            JsString(bs.url),
            JsNumber(bs.intervalSeconds),
            JsBoolean(bs.paused),
            JsNumber(bs.updatedAt),
            JsString(lastUrlScrapped))
        case None =>
          JsArray(
            JsString(bs.id),
            JsString(bs.name),
            JsString(bs.description),
            JsString(bs.provider),
            JsString(bs.url),
            JsNumber(bs.intervalSeconds),
            JsBoolean(bs.paused),
            JsNumber(bs.updatedAt))
      }
    }

    def read(value: JsValue): BatchSpecification =
      value
        .asJsObject
        .getFields("id", "name", "description", "provider", "url", "intervalSeconds", "paused", "updatedAt", "lastUrlScrapped") match {
        case Seq(JsString(id), JsString(name), JsString(description), JsString(provider), JsString(url), intervalSeconds, JsBoolean(paused), updatedAt, JsString(lastUrlScrapped)) =>
          new BatchSpecification(id, name, description, provider, url, intervalSeconds.convertTo[Long], paused, updatedAt.convertTo[Long], Some(lastUrlScrapped))

        case Seq(JsString(id), JsString(name), JsString(description), JsString(provider), JsString(url), intervalSeconds, JsBoolean(paused), updatedAt) =>
          new BatchSpecification(id, name, description, provider, url, intervalSeconds.convertTo[Long], paused, updatedAt.convertTo[Long], None)

        case Seq(JsString(name), JsString(description), JsString(provider), JsString(url), intervalSeconds) =>
          BatchSpecification(name, description, provider, url, intervalSeconds.convertTo[Long])
      }
  }
}
