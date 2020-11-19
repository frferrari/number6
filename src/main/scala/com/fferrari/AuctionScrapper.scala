package com.fferrari

import java.util.Date

import com.fferrari.model.{Bid, Price}
import net.ruippeixotog.scalascraper.browser.{Browser, JsoupBrowser}
import net.ruippeixotog.scalascraper.dsl.DSL._
import net.ruippeixotog.scalascraper.model.Element
import net.ruippeixotog.scalascraper.scraper.ContentExtractors.{attr, elementList, text}

sealed trait Website

final case object Delcampe extends Website

sealed trait AuctionType

final case object BidType extends AuctionType

final case object FixedPriceType extends AuctionType

trait AuctionScrapper {
  val jsoupBrowser: Browser = JsoupBrowser()

  def fetchId(htmlDoc: jsoupBrowser.DocumentType): Option[String]

  def fetchTitle(htmlDoc: jsoupBrowser.DocumentType): Option[String]

  def fetchSellerLocation(htmlDoc: jsoupBrowser.DocumentType): Option[String]

  def fetchSellerNickname(htmlDoc: jsoupBrowser.DocumentType): Option[String]

  def fetchAuctionType(htmlDoc: jsoupBrowser.DocumentType): Option[AuctionType]

  def fetchIsSold(htmlDoc: jsoupBrowser.DocumentType): Boolean

  def fetchStartPrice(htmlDoc: jsoupBrowser.DocumentType): Option[Price]

  def fetchFinalPrice(htmlDoc: jsoupBrowser.DocumentType): Option[Price]

  def fetchStartDate(htmlDoc: jsoupBrowser.DocumentType): Option[Date]

  def fetchEndDate(htmlDoc: jsoupBrowser.DocumentType): Option[Date]

  def fetchBids(htmlDoc: jsoupBrowser.DocumentType): List[Bid]

  def fetchLargeImageUrl(htmlDoc: jsoupBrowser.DocumentType): Option[String]

  def fetchBidCount(htmlDoc: jsoupBrowser.DocumentType): Int

  // def fetchAuction(htmlDoc: jsoupBrowser.DocumentType, url: String): ???
}

class DelcampeAuctionScrapper extends AuctionScrapper {

  override def fetchId(htmlDoc: jsoupBrowser.DocumentType): Option[String] =
    htmlDoc >?> attr("data-id")("div#confirm_question_modal")

  override def fetchTitle(htmlDoc: jsoupBrowser.DocumentType): Option[String] =
    htmlDoc >?> text("div.item-title h1 span")

  override def fetchSellerNickname(htmlDoc: jsoupBrowser.DocumentType): Option[String] =
    htmlDoc >?> text("div#seller-info div.user-status a.member > span.nickname")

  override def fetchSellerLocation(htmlDoc: jsoupBrowser.DocumentType): Option[String] =
    (htmlDoc >> elementList("div#seller-info ul li"))
      .find(findLocation)
      .flatMap(el => el >?> text("div"))

  def findLocation(li: Element): Boolean =
    (li >/~ validator(text("strong"))(_.startsWith("Location"))).isRight

  override def fetchAuctionType(htmlDoc: jsoupBrowser.DocumentType): Option[AuctionType] =
  // htmlDoc >?> attr("content")("""meta[itemprop="priceCurrency"]""") match {
    htmlDoc >?> attr("class")("div.price-info > div > i") match {
      case Some(c) if c.contains("fa-gavel") =>
        Some(BidType)
      case Some(c) if c.contains("fa-shopping-cart") =>
        Some(FixedPriceType)
      case _ =>
        None
    }

  override def fetchIsSold(htmlDoc: jsoupBrowser.DocumentType): Boolean =
    fetchBids(htmlDoc).nonEmpty

  override def fetchStartPrice(htmlDoc: jsoupBrowser.DocumentType): Option[Price] =
    if (fetchIsSold(htmlDoc)) {
      fetchBids(htmlDoc)
        .lastOption
        .map(_.bidPrice)
    } else {
      None
    }

  override def fetchFinalPrice(htmlDoc: jsoupBrowser.DocumentType): Option[Price] =
    if (fetchIsSold(htmlDoc)) {
      fetchBids(htmlDoc)
        .headOption
        .map(_.bidPrice)
    } else {
      None
    }

  override def fetchStartDate(htmlDoc: jsoupBrowser.DocumentType): Option[Date] =
    (htmlDoc >?> text("div#collapse-description div.description-info ul li:nth-child(1) div")).flatMap(DelcampeTools.parseHtmlDate)

  override def fetchEndDate(htmlDoc: jsoupBrowser.DocumentType): Option[Date] =
    (htmlDoc >?> text("div#collapse-description div.description-info ul li:nth-child(2) div")).flatMap(DelcampeTools.parseHtmlDate)

  override def fetchBids(htmlDoc: jsoupBrowser.DocumentType): List[Bid] = List()

  override def fetchLargeImageUrl(htmlDoc: jsoupBrowser.DocumentType): Option[String] =
    htmlDoc >?> attr("src")("div.item-thumbnails img.img-lense")

  override def fetchBidCount(htmlDoc: jsoupBrowser.DocumentType): Int =
    if (fetchIsSold(htmlDoc)) {
      fetchBids(htmlDoc).size
    } else {
      0
    }
}
