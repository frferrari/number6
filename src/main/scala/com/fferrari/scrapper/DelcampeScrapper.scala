package com.fferrari.scrapper

import cats.data.Validated._
import cats.implicits._
import com.fferrari.PriceScrapperProtocol.WebsiteInfo
import com.fferrari.model.Auction
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

  override def fetchAuction[A <: Auction](auctionUrl: String)
                                         (implicit jsoupBrowser: JsoupBrowser): Either[String, A] = {
    implicit val htmlDoc: jsoupBrowser.DocumentType = jsoupBrowser.get(auctionUrl)

    val htmlId = validateId
    val htmlAuctionTitle = validateTitle
    val htmlIsSold = validateIsSold
    val htmlSellerNickname = validateSellerNickname
    val htmlSellerLocation = validateSellerLocation
    val htmlAuctionType = validateAuctionType
    val htmlStartPrice = validateStartPrice
    val htmlFinalPrice = validateFinalPrice
    val htmlStartDate = validateStartDate
    val htmlEndDate = validateEndDate
    val htmlLargeImageUrl = validateLargeImageUrl
    val bids = validateBids
    val bidCount = validateBidCount
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


  override def fetchBidCount(implicit htmlDoc: JsoupDocument): Int =
    if (fetchIsSold) {
      fetchBids.size
    } else {
      0
    }
}
