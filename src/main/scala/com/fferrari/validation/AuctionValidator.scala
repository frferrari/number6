package com.fferrari.validation

import java.time.LocalDateTime

import cats.data.{NonEmptyChain, Validated}
import cats.implicits.{catsSyntaxTuple15Semigroupal, catsSyntaxValidatedIdBinCompat0}
import com.fferrari.actor.AuctionScrapperProtocol.CreateAuction
import com.fferrari.model._
import net.ruippeixotog.scalascraper.browser.JsoupBrowser
import net.ruippeixotog.scalascraper.browser.JsoupBrowser.JsoupDocument

import scala.util.Try

trait AuctionValidator {

  def fetchListingPage(websiteInfo: WebsiteConfig,
                       getPage: String => Try[JsoupDocument],
                       itemsPerPage: Int,
                       pageNumber: Int = 1)
                      (implicit jsoupBrowser: JsoupBrowser): ValidationResult[JsoupDocument]

  def fetchAuctionUrls(websiteInfo: WebsiteConfig)
                      (implicit htmlDoc: JsoupDocument): Validated[NonEmptyChain[AuctionDomainValidation], Batch]

  def fetchAuction(batchAuctionLink: BatchAuctionLink, batchId: String)
                  (implicit jsoupBrowser: JsoupBrowser): Validated[NonEmptyChain[AuctionDomainValidation], CreateAuction] = {
    implicit val htmlDoc: jsoupBrowser.DocumentType = jsoupBrowser.get(batchAuctionLink.auctionUrl)

    (validateAuctionType,
      batchId.validNec,
      validateExternalId,
      batchAuctionLink.auctionUrl.validNec,
      validateTitle,
      validateIsSold,
      validateSellerNickname, validateSellerLocation,
      validateStartPrice, validateFinalPrice,
      validateStartDate, validateEndDate,
      batchAuctionLink.thumbUrl.validNec,
      validateLargeImageUrl,
      validateBids).mapN(CreateAuction.apply)
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
