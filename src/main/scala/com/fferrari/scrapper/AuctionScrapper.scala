package com.fferrari.scrapper

import java.util.Date

import cats.data.Validated
import com.fferrari.PriceScrapperProtocol.WebsiteInfo
import com.fferrari.model.{Auction, Bid, Price}
import net.ruippeixotog.scalascraper.browser.JsoupBrowser
import net.ruippeixotog.scalascraper.browser.JsoupBrowser.JsoupDocument

sealed trait Website

final case object Delcampe extends Website

sealed trait AuctionType

final case object BidType extends AuctionType

final case object FixedPriceType extends AuctionType

abstract class AuctionScrapper {

  def fetchListingPage(websiteInfo: WebsiteInfo, itemsPerPage: Int, pageNumber: Int = 1)
                      (implicit jsoupBrowser: JsoupBrowser): JsoupDocument

  def fetchListingPageUrls(websiteInfo: WebsiteInfo)
                          (implicit htmlDoc: JsoupDocument): List[String]

  def fetchAuction[A <: Auction](auctionUrl: String)(implicit jsoupBrowser: JsoupBrowser): Either[String, A]

  def fetchId(implicit htmlDoc: JsoupDocument): Validated[DomainValidation, String]

  def fetchTitle(implicit htmlDoc: JsoupDocument): Validated[DomainValidation, String]

  def fetchSellerNickname(implicit htmlDoc: JsoupDocument): Validated[DomainValidation, String]

  def fetchSellerLocation(implicit htmlDoc: JsoupDocument): Validated[DomainValidation, String]

  def fetchAuctionType(implicit htmlDoc: JsoupDocument): Validated[DomainValidation, AuctionType]

  def fetchIsSold(implicit htmlDoc: JsoupDocument): Validated[DomainValidation, Boolean]

  def fetchStartPrice(implicit htmlDoc: JsoupDocument): Validated[DomainValidation, Price]

  def fetchFinalPrice(implicit htmlDoc: JsoupDocument): Validated[DomainValidation, Option[Price]]

  def fetchStartDate(implicit htmlDoc: JsoupDocument): Validated[DomainValidation, Date]

  def fetchEndDate(implicit htmlDoc: JsoupDocument): Validated[DomainValidation, Option[Date]]

  def fetchBids(implicit htmlDoc: JsoupDocument): Validated[DomainValidation, List[Bid]]

  def fetchLargeImageUrl(implicit htmlDoc: JsoupDocument): Validated[DomainValidation, String]

  def fetchBidCount(implicit htmlDoc: JsoupDocument): Validated[DomainValidation, Int]
}
