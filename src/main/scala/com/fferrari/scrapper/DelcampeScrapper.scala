package com.fferrari.scrapper

import java.util.Date

import cats.data.Validated._
import cats.data.{NonEmptyChain, Validated}
import cats.implicits._
import com.fferrari.PriceScrapperProtocol.WebsiteInfo
import com.fferrari.model.{Auction, Bid, Price}
import com.fferrari.scrapper.DelcampeTools.relativeToAbsoluteUrl
import net.ruippeixotog.scalascraper.browser.JsoupBrowser
import net.ruippeixotog.scalascraper.browser.JsoupBrowser.JsoupDocument
import net.ruippeixotog.scalascraper.dsl.DSL.{deepFunctorOps, _}
import net.ruippeixotog.scalascraper.scraper.ContentExtractors.{attr, element, elementList}

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

  override def fetchAuction(auctionUrl: String)
                           (implicit jsoupBrowser: JsoupBrowser): Validated[NonEmptyChain[DomainValidation], Auction] = {
    implicit val htmlDoc: jsoupBrowser.DocumentType = jsoupBrowser.get(auctionUrl)

    val htmlAuctionType: ValidationResult[AuctionType] = validateAuctionType
    val htmlId: ValidationResult[String] = validateId
    val htmlTitle: ValidationResult[String] = validateTitle
    val htmlIsSold: ValidationResult[Boolean] = validateIsSold
    val htmlSellerNickname: ValidationResult[String] = validateSellerNickname
    val htmlSellerLocation: ValidationResult[String] = validateSellerLocation
    val htmlStartPrice: ValidationResult[Price] = validateStartPrice
    val htmlFinalPrice: ValidationResult[Option[Price]] = validateFinalPrice
    val htmlStartDate: ValidationResult[Date] = validateStartDate
    val htmlEndDate: Validated[NonEmptyChain[DomainValidation], Option[Date]] = validateEndDate
    val htmlLargeImageUrl: ValidationResult[String] = validateLargeImageUrl
    val bids: ValidationResult[List[Bid]] = validateBids
    val bidCount = validateBidCount

    val auction = (htmlAuctionType, htmlId, htmlTitle, htmlIsSold,
      htmlSellerNickname, htmlSellerLocation,
      htmlStartPrice, htmlFinalPrice,
      htmlStartDate, htmlEndDate,
      htmlLargeImageUrl,
      bids).mapN(Auction.apply)

    auction.map { a =>
        println(s">>>>>> AuctionId ${a.id}")
        println(s"            title ${a.title}")
        println(s"            sellerNickname ${a.sellerNickname}")
        println(s"            sellerLocation ${a.sellerLocation}")
        println(s"            isSold ${a.isSold}")
        println(s"            largeImageUrl ${a.largeImageUrl}")
        println(s"            startDate ${a.startDate}")
        println(s"            endDate ${a.endDate}")
        println(s"            startPrice ${a.startPrice}")
        println(s"            finalPrice ${a.finalPrice}")
        println(s"            bidCount ${bidCount}")
        println(s"            >>>>>> bids")
        bids.foreach { bid => println(s"                       bid $bid") }
    }

    auction
  }
}
