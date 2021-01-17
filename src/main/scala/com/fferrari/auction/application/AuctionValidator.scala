package com.fferrari.auction.application

import java.time.{Instant, LocalDateTime}
import java.util.UUID

import cats.data.{NonEmptyChain, Validated}
import cats.implicits._
import com.fferrari.auction.domain.Auction.AuctionType
import com.fferrari.auction.domain.{Auction, AuctionLink, Batch, Bid, ListingPageAuctionLinks, Price}
import com.fferrari.batchmanager.domain.BatchSpecification
import net.ruippeixotog.scalascraper.browser.JsoupBrowser
import net.ruippeixotog.scalascraper.browser.JsoupBrowser.JsoupDocument

import scala.util.Try

trait AuctionValidator {

  def itemsPerPage: Int

  def fetchListingPage(url: String,
                       getPage: String => Try[JsoupDocument],
                       pageNumber: Int = 1)
                      (implicit jsoupBrowser: JsoupBrowser): ValidationResult[JsoupDocument]

  def fetchListingPageAuctionLinks(listingPageUrl: String, lastUrlVisited: Option[String])
                                  (implicit htmlDoc: JsoupDocument): Validated[NonEmptyChain[AuctionDomainValidation], ListingPageAuctionLinks]

  def fetchAuction(batchAuctionLink: AuctionLink, batchSpecificationID: BatchSpecification.ID)
                  (implicit jsoupBrowser: JsoupBrowser): Validated[NonEmptyChain[AuctionDomainValidation], Auction] = {
    implicit val htmlDoc: jsoupBrowser.DocumentType = jsoupBrowser.get(batchAuctionLink.auctionUrl)

    (Auction.generateID.validNec,
      validateAuctionType,
      batchSpecificationID.validNec,
      validateExternalId,
      None.validNec,
      batchAuctionLink.auctionUrl.validNec,
      validateTitle,
      validateIsSold,
      validateSellerNickname, validateSellerLocation,
      validateStartPrice, validateFinalPrice,
      validateStartDate, validateEndDate,
      batchAuctionLink.thumbUrl.validNec,
      validateLargeImageUrl,
      validateBids).mapN(Auction.apply)
  }

  def nextBatchId: UUID

  def validateExternalId(implicit htmlDoc: JsoupDocument): ValidationResult[String]

  def validateTitle(implicit htmlDoc: JsoupDocument): ValidationResult[String]

  def validateSellerNickname(implicit htmlDoc: JsoupDocument): ValidationResult[String]

  def validateSellerLocation(implicit htmlDoc: JsoupDocument): ValidationResult[String]

  def validateAuctionType(implicit htmlDoc: JsoupDocument): ValidationResult[AuctionType]

  def validateIsSold(implicit htmlDoc: JsoupDocument): ValidationResult[Boolean]

  def validateStartPrice(implicit htmlDoc: JsoupDocument): ValidationResult[Price]

  def validateFinalPrice(implicit htmlDoc: JsoupDocument): ValidationResult[Option[Price]]

  def validateStartDate(implicit htmlDoc: JsoupDocument): ValidationResult[Instant]

  def validateEndDate(implicit htmlDoc: JsoupDocument): Validated[NonEmptyChain[AuctionDomainValidation], Option[Instant]]

  def validateLargeImageUrl(implicit htmlDoc: JsoupDocument): ValidationResult[String]

  def validateBids(implicit htmlDoc: JsoupDocument): ValidationResult[List[Bid]]

  def validateBidCount(implicit htmlDoc: JsoupDocument): ValidationResult[Int]
}
