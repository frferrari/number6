package com.fferrari

import java.util.Date

import com.fferrari.model.{Bid, Price}
import net.ruippeixotog.scalascraper.browser.JsoupBrowser.JsoupDocument
import net.ruippeixotog.scalascraper.dsl.DSL._
import net.ruippeixotog.scalascraper.model.Element
import net.ruippeixotog.scalascraper.scraper.ContentExtractors.{attr, element, elementList, text}

sealed trait Website

final case object Delcampe extends Website

sealed trait AuctionType

final case object BidType extends AuctionType

final case object FixedPriceType extends AuctionType

abstract class AuctionScrapper {

  def fetchId(htmlDoc: JsoupDocument): Option[String]

  def fetchTitle(htmlDoc: JsoupDocument): Option[String]

  def fetchSellerLocation(htmlDoc: JsoupDocument): Option[String]

  def fetchSellerNickname(htmlDoc: JsoupDocument): Option[String]

  def fetchAuctionType(htmlDoc: JsoupDocument): Option[AuctionType]

  def fetchIsSold(htmlDoc: JsoupDocument): Boolean

  def fetchStartPrice(htmlDoc: JsoupDocument): Option[Price]

  def fetchFinalPrice(htmlDoc: JsoupDocument): Option[Price]

  def fetchStartDate(htmlDoc: JsoupDocument): Option[Date]

  def fetchEndDate(htmlDoc: JsoupDocument): Option[Date]

  def fetchBids(htmlDoc: JsoupDocument): List[Bid]

  def fetchLargeImageUrl(htmlDoc: JsoupDocument): Option[String]

  def fetchBidCount(htmlDoc: JsoupDocument): Int
}

class DelcampeAuctionScrapper
  extends AuctionScrapper {

  override def fetchId(htmlDoc: JsoupDocument): Option[String] =
    htmlDoc >?> attr("data-id")("div#confirm_question_modal")

  override def fetchTitle(htmlDoc: JsoupDocument): Option[String] =
    htmlDoc >?> text("div.item-title h1 span")

  override def fetchSellerNickname(htmlDoc: JsoupDocument): Option[String] =
    htmlDoc >?> text("div#seller-info div.user-status a.member > span.nickname")

  override def fetchSellerLocation(htmlDoc: JsoupDocument): Option[String] =
    (htmlDoc >> elementList("div#seller-info ul li"))
      .find(findLocation)
      .map(el => el >?> text("div"))
      .flatMap(normalizeLocation)

  def findLocation(li: Element): Boolean =
    (li >/~ validator(text("strong"))(_.startsWith("Location"))).isRight

  def normalizeLocation(location: Option[String]): Option[String] =
    location
      .map(_.split(",")) // A Location can be of the following forms "Italy, Murano" or "Italy"
      .flatMap(_.headOption)

  override def fetchAuctionType(htmlDoc: JsoupDocument): Option[AuctionType] =
  // htmlDoc >?> attr("content")("""meta[itemprop="priceCurrency"]""") match {
    htmlDoc >?> attr("class")("div.price-info > div > i") match {
      case Some(c) if c.contains("fa-gavel") =>
        Some(BidType)
      case Some(c) if c.contains("fa-shopping-cart") =>
        Some(FixedPriceType)
      case _ =>
        None
    }

  override def fetchIsSold(htmlDoc: JsoupDocument): Boolean =
    (htmlDoc >?> element("div#closed-sell")).nonEmpty

  override def fetchStartPrice(htmlDoc: JsoupDocument): Option[Price] =
    if (fetchIsSold(htmlDoc)) {
      fetchBids(htmlDoc)
        .lastOption
        .map(_.bidPrice)
    } else {
      fetchAuctionType(htmlDoc) match {
        case Some(BidType) =>
          (htmlDoc >?> text("div#bid-box span.price"))
            .flatMap(DelcampeTools.parseHtmlPrice)
        case Some(FixedPriceType) =>
          (htmlDoc >?> text("div#buy-box span.price"))
            .flatMap(DelcampeTools.parseHtmlPrice)
        case _ =>
          None
      }
    }

  override def fetchFinalPrice(htmlDoc: JsoupDocument): Option[Price] =
    (htmlDoc >?> text("div#closed-sell span.price"))
      .flatMap(DelcampeTools.parseHtmlPrice)

  override def fetchStartDate(htmlDoc: JsoupDocument): Option[Date] =
    (htmlDoc >?> text("div#collapse-description div.description-info ul li:nth-child(1) div")).flatMap(DelcampeTools.parseHtmlDate)

  override def fetchEndDate(htmlDoc: JsoupDocument): Option[Date] =
    (htmlDoc >?> text("div#collapse-description div.description-info ul li:nth-child(2) div")).flatMap(DelcampeTools.parseHtmlDate)

  override def fetchBids(htmlDoc: JsoupDocument): List[Bid] = {
    if (!fetchIsSold(htmlDoc)) {
      List()
    } else {
      fetchAuctionType(htmlDoc) match {
        case Some(BidType) =>
          val htmlBidsTable = htmlDoc >> elementList("div.bids-container ul.table-body-list")
          val htmlBidsWithDetails = htmlBidsTable.map(bid => (bid, bid >> elementList("li")))

          htmlBidsWithDetails.collect {
            case (bid, htmlTableColumns) if htmlTableColumns.length >= 3 =>
              val htmlNickname: Option[String] = bid >?> text("span.nickname")
              val htmlCurrencyAndPrice = (htmlTableColumns(1) >?> text("strong")).flatMap(DelcampeTools.parseHtmlPrice)
              val htmlBidDate: Option[Date] = (htmlTableColumns(2) >?> text).flatMap(DelcampeTools.parseHtmlShortDate)
              val isAutomaticBid: Boolean = (htmlTableColumns(1) >?> text("span")).contains("automatic")

              (htmlNickname, htmlCurrencyAndPrice, htmlBidDate) match {
                case (Some(nickname), Some(price), Some(bidDate)) =>
                  Bid(nickname, price, 1, isAutomaticBid, bidDate)
              }
          }
        case Some(FixedPriceType) =>
          val auctionPrice: Option[Price] = fetchFinalPrice(htmlDoc)
          val htmlPurchaseTable = htmlDoc >> elementList("""div[.id="sales"] div.table-view ul.table-body-list""")
          val htmlPurchaseWithDetails = htmlPurchaseTable.map(purchase => (purchase, purchase >> elementList("li"), auctionPrice))

          htmlPurchaseWithDetails.collect {
            case (purchase, htmlTableColumns, Some(price)) if htmlTableColumns.length >= 3 =>
              val htmlNickname: String = purchase >> text("li.list-user span")
              val htmlPurchaseDate: String = htmlTableColumns(2) >> text
              val htmlPurchaseTime: String = htmlTableColumns(3) >> text
              val purchaseDate: Option[Date] = DelcampeTools.parseHtmlShortDate(s"$htmlPurchaseDate $htmlPurchaseTime")
              val purchaseQuantity = DelcampeTools.parseHtmlQuantity(htmlTableColumns(1) >> text)

              Bid(htmlNickname, price, purchaseQuantity.getOrElse(1), isAutomaticBid = false, purchaseDate.get) // TODO remove .get
          }
      }
    }
  }

  override def fetchLargeImageUrl(htmlDoc: JsoupDocument): Option[String] =
    htmlDoc >?> attr("src")("div.item-thumbnails img.img-lense")

  override def fetchBidCount(htmlDoc: JsoupDocument): Int =
    if (fetchIsSold(htmlDoc)) {
      fetchBids(htmlDoc).size
    } else {
      0
    }
}
