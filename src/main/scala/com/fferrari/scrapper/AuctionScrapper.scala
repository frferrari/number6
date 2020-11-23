package com.fferrari.scrapper

import cats.data.{NonEmptyChain, Validated}
import com.fferrari.PriceScrapperProtocol.WebsiteInfo
import com.fferrari.model.Auction
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

  def fetchAuction(auctionUrl: String)(implicit jsoupBrowser: JsoupBrowser): Validated[NonEmptyChain[DomainValidation], Auction]
}
