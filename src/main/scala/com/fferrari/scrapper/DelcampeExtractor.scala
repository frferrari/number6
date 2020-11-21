package com.fferrari.scrapper

import java.util.Date

import com.fferrari.model.{Bid, Price}
import net.ruippeixotog.scalascraper.browser.JsoupBrowser.JsoupDocument
import net.ruippeixotog.scalascraper.dsl.DSL.Extract._
import net.ruippeixotog.scalascraper.dsl.DSL.{deepFunctorOps, validator, _}
import net.ruippeixotog.scalascraper.model.Element
import net.ruippeixotog.scalascraper.scraper.ContentExtractors.{attr, elementList, text}

sealed trait AuctionValidator {
  def idValidator(implicit htmlDoc: JsoupDocument): Boolean

  def titleValidator(implicit htmlDoc: JsoupDocument): Boolean

  def sellerNicknameValidator(implicit htmlDoc: JsoupDocument): Boolean

  def sellerLocationValidator(implicit htmlDoc: JsoupDocument): Boolean

  def auctionTypeValidator(implicit htmlDoc: JsoupDocument): Boolean

  def isSoldValidator(implicit htmlDoc: JsoupDocument): Boolean

  def startPriceValidator(implicit htmlDoc: JsoupDocument): Boolean

  def finalPriceValidator(implicit htmlDoc: JsoupDocument): Boolean

  def startDateValidator(implicit htmlDoc: JsoupDocument): Boolean

  def endDateValidator(implicit htmlDoc: JsoupDocument): Boolean

  def bidsValidator(implicit htmlDoc: JsoupDocument): Boolean
}

sealed trait AuctionExtractor {
  def idExtractor(implicit htmlDoc: JsoupDocument): String

  def titleExtractor(implicit htmlDoc: JsoupDocument): String

  def sellerNicknameExtractor(implicit htmlDoc: JsoupDocument): String

  def sellerLocationExtractor(implicit htmlDoc: JsoupDocument): String

  def auctionTypeExtractor(implicit htmlDoc: JsoupDocument): AuctionType

  def isSoldExtractor(implicit htmlDoc: JsoupDocument): Boolean

  def startPriceExtractor(implicit htmlDoc: JsoupDocument): Price

  def finalPriceExtractor(implicit htmlDoc: JsoupDocument): Option[Price]

  def startDateExtractor(implicit htmlDoc: JsoupDocument): Date

  def endDateExtractor(implicit htmlDoc: JsoupDocument): Option[Date]

  def bidsExtractor(implicit htmlDoc: JsoupDocument): List[Bid]
}

class DelcampeExtractor
  extends AuctionExtractor
    with AuctionValidator {

  val SELLER_LOCATION_LABEL = "Location"

  //
  // Validators
  //
  override def idValidator(implicit htmlDoc: JsoupDocument): Boolean =
    (htmlDoc >/~ validator(attrs("div#confirm_question_modal"))(_.exists(_ == "data-id"))).isRight

  override def titleValidator(implicit htmlDoc: JsoupDocument): Boolean =
    (htmlDoc >/~ validator(text("div.item-title h1 span"))(_.nonEmpty)).isRight

  override def sellerNicknameValidator(implicit htmlDoc: JsoupDocument): Boolean =
    (htmlDoc >/~ validator(text("div#seller-info div.user-status a.member > span.nickname"))(_.nonEmpty)).isRight

  override def sellerLocationValidator(implicit htmlDoc: JsoupDocument): Boolean = {
    (htmlDoc >/~ validator(elementList("div#seller-info ul li"))(_.exists(sellerLocationLabelAndValueValidator))).isRight
  }

  override def sellerLocationLabelValidator(li: Element): Boolean =
    (li >/~ validator(text("strong"))(_.startsWith(SELLER_LOCATION_LABEL))).isRight

  override def sellerLocationValueValidator(li: Element): Boolean =
    (li >/~ validator(text("div"))(_.split(",").nonEmpty)).isRight

  override def sellerLocationLabelAndValueValidator(li: Element): Boolean =
    sellerLocationLabelValidator(li) & sellerLocationValueValidator(li)

  override def sellerLocationNormalizer(location: String): String =
    location
      .split(",") // A Location can be of the following forms "Italy, Murano" or "Italy"
      .head

  override def auctionTypeValidator(implicit htmlDoc: JsoupDocument): Boolean =
    (htmlDoc >/~ validator(attr("class")("div.price-info > div > i"))(c => List("fa-gavel", "fa-shipping-cart").contains(c))).isRight

  override def isSoldValidator(implicit htmlDoc: JsoupDocument): Boolean =
    (htmlDoc >/~ validator(attr("class")("div#closed-sell"))(_ == "price-box")).isRight

  override def endDateValidator(implicit htmlDoc: JsoupDocument): Boolean =
    (htmlDoc >/~
      validator {
        text("div#collapse-description div.description-info ul li:nth-child(2) div")
      }(DelcampeTools.parseHtmlDate(_).isRight)).isRight

  //
  // Extractors
  //
  override def idExtractor(implicit htmlDoc: JsoupDocument): String =
    htmlDoc >> attr("data-id")("div#confirm_question_modal")

  override def titleExtractor(implicit htmlDoc: JsoupDocument): String =
    htmlDoc >> text("div.item-title h1 span")

  override def sellerNicknameExtractor(implicit htmlDoc: JsoupDocument): String =
    htmlDoc >> text("div#seller-info div.user-status a.member > span.nickname")

  override def sellerLocationExtractor(implicit htmlDoc: JsoupDocument): String =
    (htmlDoc >> elementList("div#seller-info ul li"))
      .find(sellerLocationLabelValidator)
      .map(li => sellerLocationNormalizer(li >> text("div")))
      .getOrElse("")

  override def auctionTypeExtractor(implicit htmlDoc: JsoupDocument): AuctionType =
    htmlDoc >> attr("class")("div.price-info > div > i") match {
      case c if c.contains("fa-gavel") =>
        BidType
      case _ =>
        FixedPriceType
    }

  override def isSoldExtractor(implicit htmlDoc: JsoupDocument): Boolean =
    htmlDoc >> attr("class")("div#closed-sell") == "price-box"

  override def endDateExtractor(implicit htmlDoc: JsoupDocument): Option[Date] =
    DelcampeTools.parseHtmlDate(htmlDoc >> text("div#collapse-description div.description-info ul li:nth-child(2) div")).toOption
}
