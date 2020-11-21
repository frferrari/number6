package com.fferrari.scrapper

import java.util.Date

import DelcampeTools.relativeToAbsoluteUrl
import com.fferrari.PriceScrapperProtocol.WebsiteInfo
import com.fferrari.model.{Auction, Bid, Price}
import net.ruippeixotog.scalascraper.browser.JsoupBrowser
import net.ruippeixotog.scalascraper.browser.JsoupBrowser.JsoupDocument
import net.ruippeixotog.scalascraper.dsl.DSL.{deepFunctorOps, validator}
import net.ruippeixotog.scalascraper.model.Element
import net.ruippeixotog.scalascraper.scraper.ContentExtractors.{attr, element, elementList, text}
import net.ruippeixotog.scalascraper.dsl.DSL._
import net.ruippeixotog.scalascraper.dsl.DSL.Extract._
import net.ruippeixotog.scalascraper.dsl.DSL.Parse._
import net.ruippeixotog.scalascraper.scraper.HtmlExtractor
import cats.data._
import cats.data.Validated._
import cats.implicits._

import scala.util.Try

class DelcampeScrapper extends AuctionScrapper {
  this: DelcampeExtractor =>

  override def fetchListingPage(websiteInfo: WebsiteInfo, itemsPerPage: Int, pageNumber: Int = 1)
                               (implicit jsoupBrowser: JsoupBrowser): JsoupDocument =
    jsoupBrowser.get(s"${websiteInfo.url}&size=$itemsPerPage&page=$pageNumber")

  override def fetchListingPageUrls(websiteInfo: WebsiteInfo)
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
        htmlAuctionUrls.takeWhile(_ != url)
      case _ =>
        htmlAuctionUrls
    }
  }

  override def fetchAuction[A <: Auction](auctionUrl: String)
                                         (implicit jsoupBrowser: JsoupBrowser): Either[String, A] = {
    implicit val htmlDoc: jsoupBrowser.DocumentType = jsoupBrowser.get(auctionUrl)

    val htmlId = fetchId
    val htmlAuctionTitle = fetchTitle
    val htmlIsSold = fetchIsSold
    val htmlSellerNickname = fetchSellerNickname
    val htmlSellerLocation = fetchSellerLocation
    val htmlAuctionType = fetchAuctionType
    val htmlStartPrice = fetchStartPrice
    val htmlFinalPrice = fetchFinalPrice
    val htmlStartDate = fetchStartDate
    val htmlEndDate = fetchEndDate
    val htmlLargeImageUrl = fetchLargeImageUrl
    val bids = fetchBids
    val bidCount = fetchBidCount
    println(s">>>>>> AuctionId $htmlId")
    println(s"            htmlAuctionType $htmlAuctionType")
    println(s"            htmlAuctionTitle $htmlAuctionTitle")
    println(s"            htmlSellerNickname $htmlSellerNickname")
    println(s"            htmlSellerLocation $htmlSellerLocation")
    println(s"            htmlIsSold $htmlIsSold")
    println(s"            htmlLargeImageUrl $htmlLargeImageUrl")
    println(s"            htmlStartDate $htmlStartDate")
    println(s"            htmlEndDate $htmlEndDate")
    println(s"            bidCount $bidCount")
    println(s"            htmlStartPrice $htmlStartPrice")
    println(s"            htmlFinalPrice $htmlFinalPrice")
    println(s"            >>>>>> bids")
    bids.foreach { bid => println(s"                       bid $bid") }

    htmlAuctionType match {
      case BidType =>
        AuctionBid(
          htmlId,
          htmlAuctionTitle,
          htmlIsSold,
          htmlSellerNickname,
          htmlSellerLocation,
          htmlStartPrice,
          htmlFinalPrice,
          htmlStartDate,
          htmlEndDate,
          htmlLargeImageUrl,
          bids
        )
      case FixedPriceType =>
    }
  }

  override def fetchId(implicit htmlDoc: JsoupDocument): Validated[DomainValidation, String] =
    Either.cond(
      idValidator,
      idExtractor,
      IdNotFound
    ).toValidated

  override def fetchTitle(implicit htmlDoc: JsoupDocument): Validated[DomainValidation, String] =
    Either.cond(
      titleValidator,
      titleExtractor,
      TitleNotFound
    ).toValidated

  override def fetchSellerNickname(implicit htmlDoc: JsoupDocument): Validated[DomainValidation, String] =
    Either.cond(
      sellerNicknameValidator,
      sellerNicknameExtractor,
      SellerNicknameNotFound
    ).toValidated

  override def fetchSellerLocation(implicit htmlDoc: JsoupDocument): Validated[DomainValidation, String] =
    Either.cond(
      sellerLocationValidator,
      sellerLocationExtractor,
      SellerLocationNotFound
    ).toValidated

  override def fetchAuctionType(implicit htmlDoc: JsoupDocument): Validated[DomainValidation, AuctionType] =
    Either.cond(
      auctionTypeValidator,
      auctionTypeExtractor,
      AuctionTypeNotFound
    ).toValidated

  override def fetchIsSold(implicit htmlDoc: JsoupDocument): Validated[DomainValidation, Boolean] =
    Either.cond(
      isSoldValidator,
      isSoldExtractor,
      IsSoldFlagNotFound
    )

  override def fetchStartPrice(implicit htmlDoc: JsoupDocument): Either[Throwable, Price] = {
    val isSold: Boolean = fetchIsSold

    fetchAuctionType match {
      case Right(FixedPriceType) if isSold =>
        DelcampeTools.parseHtmlPrice(htmlDoc >> text("div#closed-sell span.price"))
      case Right(FixedPriceType) if !isSold =>
        DelcampeTools.parseHtmlPrice(htmlDoc >> text("div#buy-box span.price"))
      case Right(BidType) if isSold =>
        Right(fetchBids.last.bidPrice)
      case Right(BidType) if !isSold =>
        DelcampeTools.parseHtmlPrice(htmlDoc >> text("div#bid-box span.price"))
      case _ =>
        throw new IllegalArgumentException("Could not fetch the auction start price")
    }
  }

  override def fetchFinalPrice(implicit htmlDoc: JsoupDocument): Either[Throwable, Price] =
    DelcampeTools.parseHtmlPrice(htmlDoc >> text("div#closed-sell span.price"))

  override def fetchStartDate(implicit htmlDoc: JsoupDocument): Either[Throwable, Date] =
    DelcampeTools.parseHtmlDate(htmlDoc >> text("div#collapse-description div.description-info ul li:nth-child(1) div"))

  override def fetchEndDate(implicit htmlDoc: JsoupDocument): Validated[DomainValidation, Option[Date]] =
    Either.cond(
      endDateValidator,
      endDateExtractor,
      EndDateNotFound
    ).toValidated

  override def fetchBids(implicit htmlDoc: JsoupDocument): Either[Throwable, List[Bid]] =
    Try {
      if (!fetchIsSold) {
        List()
      } else {
        fetchAuctionType match {
          case Right(BidType) =>
            val htmlBidsTable: List[Element] = htmlDoc >> elementList("div#tab-bids div.bids-container ul.table-body-list")

            val t: Seq[Either[Throwable, Bid]] = htmlBidsTable.map { bid =>
              val htmlNickname: String = bid >> text("li:nth-child(1) span.nickname")
              val isAutomaticBid: Boolean = (bid >> text("li:nth-child(2) span")).contains("automatic")
              val htmlCurrencyAndPrice: Either[Throwable, Price] = DelcampeTools.parseHtmlPrice(bid >> text("li:nth-child(2) strong"))
              val htmlBidDate: Either[Throwable, Date] = DelcampeTools.parseHtmlShortDate(bid >> text("li:nth-child(3)"))

              for {
                bidPrice <- htmlCurrencyAndPrice
                bidDate <- htmlBidDate
              } yield Bid(htmlNickname, bidPrice, 1, isAutomaticBid, bidDate)
            }

            t

          case Right(FixedPriceType) =>
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
