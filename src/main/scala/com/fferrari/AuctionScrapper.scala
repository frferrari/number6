package com.fferrari

import java.util.Date

import com.fferrari.AuctionScrapperActor.jsoupBrowser
import com.fferrari.DelcampeTools.relativeToAbsoluteUrl
import com.fferrari.PriceScrapperProtocol.WebsiteInfo
import com.fferrari.model.{Bid, Price}
import net.ruippeixotog.scalascraper.browser.JsoupBrowser
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

  def fetchListingPage(websiteInfo: WebsiteInfo, itemsPerPage: Int, pageNumber: Int = 1)
                      (implicit jsoupBrowser: JsoupBrowser): JsoupDocument

  def fetchAuctionUrls(websiteInfo: WebsiteInfo)
                      (implicit htmlDoc: JsoupDocument): List[String]

  def fetchId(implicit htmlDoc: JsoupDocument): Option[String]

  def fetchTitle(implicit htmlDoc: JsoupDocument): Option[String]

  def fetchSellerLocation(implicit htmlDoc: JsoupDocument): Option[String]

  def fetchSellerNickname(implicit htmlDoc: JsoupDocument): Option[String]

  def fetchAuctionType(implicit htmlDoc: JsoupDocument): Option[AuctionType]

  def fetchIsSold(implicit htmlDoc: JsoupDocument): Boolean

  def fetchStartPrice(implicit htmlDoc: JsoupDocument): Option[Price]

  def fetchFinalPrice(implicit htmlDoc: JsoupDocument): Option[Price]

  def fetchStartDate(implicit htmlDoc: JsoupDocument): Option[Date]

  def fetchEndDate(implicit htmlDoc: JsoupDocument): Option[Date]

  def fetchBids(implicit htmlDoc: JsoupDocument): List[Bid]

  def fetchLargeImageUrl(implicit htmlDoc: JsoupDocument): Option[String]

  def fetchBidCount(implicit htmlDoc: JsoupDocument): Int
}

class DelcampeAuctionScrapper extends AuctionScrapper {

  override def fetchListingPage(websiteInfo: WebsiteInfo, itemsPerPage: Int, pageNumber: Int = 1)
                               (implicit jsoupBrowser: JsoupBrowser): JsoupDocument =
    jsoupBrowser.get(s"${websiteInfo.url}&size=$itemsPerPage&page=$pageNumber")

  override def fetchAuctionUrls(websiteInfo: WebsiteInfo)
                               (implicit htmlDoc: JsoupDocument): List[String] = {

    // Extract all the auction urls
    val htmlAuctionUrls: List[String] =
      for {
        htmlItem <- htmlDoc >> elementList(".item-listing .item-main-infos")
        htmlItemInfo = htmlItem >> element("div.item-info")
        htmlAuctionUrl = relativeToAbsoluteUrl(websiteInfo.url, htmlItemInfo >> element("a") >> attr("href"))
      } yield htmlAuctionUrl

    // Keep only the auction urls that have not yet been processed (since the last run)
    websiteInfo.lastScrappedUrl match {
      case Some(url) if htmlAuctionUrls.contains(url) =>
        htmlAuctionUrls.takeWhile(_ == url)
      case _ =>
        htmlAuctionUrls
    }
  }

  override def fetchId(implicit htmlDoc: JsoupDocument): Option[String] =
    htmlDoc >?> attr("data-id")("div#confirm_question_modal")

  override def fetchTitle(implicit htmlDoc: JsoupDocument): Option[String] =
    htmlDoc >?> text("div.item-title h1 span")

  override def fetchSellerNickname(implicit htmlDoc: JsoupDocument): Option[String] =
    htmlDoc >?> text("div#seller-info div.user-status a.member > span.nickname")

  override def fetchSellerLocation(implicit htmlDoc: JsoupDocument): Option[String] =
    (htmlDoc >> elementList("div#seller-info ul li"))
      .find(locationFinder)
      .map(_ >?> text("div"))
      .flatMap(locationNormalizer)

  def locationFinder(li: Element): Boolean =
    (li >/~ validator(text("strong"))(_.startsWith("Location"))).isRight

  def locationNormalizer(location: Option[String]): Option[String] =
    location
      .map(_.split(",")) // A Location can be of the following forms "Italy, Murano" or "Italy"
      .flatMap(_.headOption)

  override def fetchAuctionType(implicit htmlDoc: JsoupDocument): Option[AuctionType] =
  // htmlDoc >?> attr("content")("""meta[itemprop="priceCurrency"]""") match {
    htmlDoc >?> attr("class")("div.price-info > div > i") match {
      case Some(c) if c.contains("fa-gavel") =>
        Some(BidType)
      case Some(c) if c.contains("fa-shopping-cart") =>
        Some(FixedPriceType)
      case _ =>
        None
    }

  override def fetchIsSold(implicit htmlDoc: JsoupDocument): Boolean =
    (htmlDoc >?> element("div#closed-sell")).nonEmpty

  override def fetchStartPrice(implicit htmlDoc: JsoupDocument): Option[Price] = {
    val isSold = fetchIsSold

    fetchAuctionType match {
      case Some(FixedPriceType) if isSold =>
        (htmlDoc >?> text("div#closed-sell span.price"))
          .flatMap(DelcampeTools.parseHtmlPrice)
      case Some(FixedPriceType) if !isSold =>
        (htmlDoc >?> text("div#buy-box span.price"))
          .flatMap(DelcampeTools.parseHtmlPrice)
      case Some(BidType) if isSold =>
        fetchBids
          .lastOption
          .map(_.bidPrice)
      case Some(BidType) if !isSold =>
        (htmlDoc >?> text("div#bid-box span.price"))
          .flatMap(DelcampeTools.parseHtmlPrice)
      case _ =>
        None
    }
  }

  override def fetchFinalPrice(implicit htmlDoc: JsoupDocument): Option[Price] =
    (htmlDoc >?> text("div#closed-sell span.price"))
      .flatMap(DelcampeTools.parseHtmlPrice)

  override def fetchStartDate(implicit htmlDoc: JsoupDocument): Option[Date] =
    (htmlDoc >?> text("div#collapse-description div.description-info ul li:nth-child(1) div")).flatMap(DelcampeTools.parseHtmlDate)

  override def fetchEndDate(implicit htmlDoc: JsoupDocument): Option[Date] =
    (htmlDoc >?> text("div#collapse-description div.description-info ul li:nth-child(2) div")).flatMap(DelcampeTools.parseHtmlDate)

  override def fetchBids(implicit htmlDoc: JsoupDocument): List[Bid] = {
    if (!fetchIsSold) {
      List()
    } else {
      fetchAuctionType match {
        case Some(BidType) =>
          val htmlBidsTable = htmlDoc >> elementList("div#tab-bids div.bids-container ul.table-body-list")
          println(s"=====> htmlBidsTable=$htmlBidsTable")

          htmlBidsTable.flatMap { bid =>
              val htmlNickname: Option[String] = bid >?> text("li:nth-child(1) span.nickname")
              val isAutomaticBid: Boolean = (bid >?> text("li:nth-child(2) span")).contains("automatic")
              val htmlCurrencyAndPrice: Option[Price] = (bid >?> text("li:nth-child(2) strong")).flatMap(DelcampeTools.parseHtmlPrice)
              val htmlBidDate: Option[Date] = (bid >?> text("li:nth-child(3)")).flatMap(DelcampeTools.parseHtmlShortDate)

              (htmlNickname, htmlCurrencyAndPrice, htmlBidDate) match {
                case (Some(nickname), Some(price), Some(bidDate)) =>
                  Some(Bid(nickname, price, 1, isAutomaticBid, bidDate))
                case _ =>
                  None
              }
          }

        case Some(FixedPriceType) =>
          val auctionPrice: Option[Price] = fetchFinalPrice
          val htmlPurchaseTable = htmlDoc >> element("div#tab-sales div.table-view div.table-body ul.table-body-list")

          val htmlNickname: Option[String] = htmlPurchaseTable >?> text("li:nth-child(1) span")
          val htmlPurchaseQuantity: Option[Int] = (htmlPurchaseTable >?> text("li:nth-child(2)")).flatMap(DelcampeTools.parseHtmlQuantity)
          val htmlPurchaseDate: Option[Date] = {
            DelcampeTools.parseHtmlShortDate(
              List(3, 4)
                .map(nth => htmlPurchaseTable >?> text(s"li:nth-child($nth)"))
                .map(_.getOrElse(""))
                .mkString(" ")
            )
          }

          (htmlNickname, auctionPrice, htmlPurchaseQuantity, htmlPurchaseDate) match {
            case (Some(nickname), Some(price), Some(purchaseQuantity), Some(purchaseDate)) =>
              List(Bid(nickname, price, purchaseQuantity, isAutomaticBid = false, purchaseDate))
            case _ =>
              List()
          }
      }
    }
  }

  override def fetchLargeImageUrl(implicit htmlDoc: JsoupDocument): Option[String] =
    htmlDoc >?> attr("src")("div.item-thumbnails img.img-lense")

  override def fetchBidCount(implicit htmlDoc: JsoupDocument): Int =
    if (fetchIsSold) {
      fetchBids.size
    } else {
      0
    }
}
