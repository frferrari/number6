package com.fferrari.validation

import java.time.LocalDateTime

import cats.data.{NonEmptyChain, Validated}
import cats.implicits.{catsSyntaxTuple15Semigroupal, catsSyntaxValidatedIdBinCompat0}
import com.fferrari.model.Auction.AuctionType
import com.fferrari.model._
import net.ruippeixotog.scalascraper.browser.JsoupBrowser
import net.ruippeixotog.scalascraper.browser.JsoupBrowser.JsoupDocument

import scala.util.Try

trait AuctionValidator {

  def itemsPerPage: Int

  def fetchListingPage(batchSpecification: BatchSpecification,
                       getPage: String => Try[JsoupDocument],
                       pageNumber: Int = 1)
                      (implicit jsoupBrowser: JsoupBrowser): ValidationResult[JsoupDocument]

  def fetchAuctionUrls(batchSpecification: BatchSpecification)
                      (implicit htmlDoc: JsoupDocument): Validated[NonEmptyChain[AuctionDomainValidation], Batch]

  def fetchAuction(batchAuctionLink: BatchAuctionLink, batchSpecification: BatchSpecification)
                  (implicit jsoupBrowser: JsoupBrowser): Validated[NonEmptyChain[AuctionDomainValidation], Auction] = {
    implicit val htmlDoc: jsoupBrowser.DocumentType = jsoupBrowser.get(batchAuctionLink.auctionUrl)

    (validateAuctionType,
      batchSpecification.validNec,
      validateExternalId,
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

  def nextBatchId: String

  def validateExternalId(implicit htmlDoc: JsoupDocument): ValidationResult[String]

  def validateTitle(implicit htmlDoc: JsoupDocument): ValidationResult[String]

  def validateSellerNickname(implicit htmlDoc: JsoupDocument): ValidationResult[String]

  def validateSellerLocation(implicit htmlDoc: JsoupDocument): ValidationResult[String]

  def validateAuctionType(implicit htmlDoc: JsoupDocument): ValidationResult[AuctionType]

  def validateIsSold(implicit htmlDoc: JsoupDocument): ValidationResult[Boolean]

  def validateStartPrice(implicit htmlDoc: JsoupDocument): ValidationResult[Price]

  def validateFinalPrice(implicit htmlDoc: JsoupDocument): ValidationResult[Option[Price]]

  def validateStartDate(implicit htmlDoc: JsoupDocument): ValidationResult[LocalDateTime]

  def validateEndDate(implicit htmlDoc: JsoupDocument): Validated[NonEmptyChain[AuctionDomainValidation], Option[LocalDateTime]]

  def validateLargeImageUrl(implicit htmlDoc: JsoupDocument): ValidationResult[String]

  def validateBids(implicit htmlDoc: JsoupDocument): ValidationResult[List[Bid]]

  def validateBidCount(implicit htmlDoc: JsoupDocument): ValidationResult[Int]
}
