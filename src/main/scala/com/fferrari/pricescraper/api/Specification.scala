package com.fferrari.pricescraper.api

import spray.json.{DefaultJsonProtocol, JsArray, JsNumber, JsString, JsValue, RootJsonFormat}

case class Specification(name: String,
                          description: String,
                          url: String,
                          provider: String,
                          intervalSeconds: Long)

trait SpecificationJsonProtocol extends DefaultJsonProtocol {
  implicit object BatchSpecificationJsonFormat extends RootJsonFormat[Specification] {
    def write(spec: Specification): JsArray = {
      JsArray(
        JsString(spec.name),
        JsString(spec.description),
        JsString(spec.url),
        JsString(spec.provider),
        JsNumber(spec.intervalSeconds)
      )
    }

    def read(value: JsValue): Specification = {
      value
        .asJsObject
        .getFields("name", "description", "url", "provider", "intervalSeconds") match {
        case Seq(JsString(name), JsString(description), JsString(url), JsString(provider), intervalSeconds) =>
          Specification(name, description, url, provider, intervalSeconds.convertTo[Long])
      }
    }
  }
}
