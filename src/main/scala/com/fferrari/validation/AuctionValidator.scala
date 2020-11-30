package com.fferrari.validation

import java.time.LocalDateTime

import cats.data.{NonEmptyChain, Validated}
import cats.implicits.{catsSyntaxTuple13Semigroupal, catsSyntaxValidatedIdBinCompat0}
import com.fferrari.actor.AuctionScrapperProtocol.{CreateAuction, WebsiteInfo}
import com.fferrari.model.{AuctionType, Bid, Price}
import net.ruippeixotog.scalascraper.browser.JsoupBrowser
import net.ruippeixotog.scalascraper.browser.JsoupBrowser.JsoupDocument

import scala.util.Try

trait AuctionValidator {

  def validateListingPage(websiteInfo: WebsiteInfo,
                          getPage: String => Try[JsoupDocument],
                          itemsPerPage: Int,
                          pageNumber: Int = 1)
                         (implicit jsoupBrowser: JsoupBrowser): ValidationResult[JsoupDocument]

  def validateAuctionUrls(websiteInfo: WebsiteInfo)
                         (implicit htmlDoc: JsoupDocument): Validated[NonEmptyChain[AuctionDomainValidation], List[String]]

  def validateAuction(auctionUrl: String)
                     (implicit jsoupBrowser: JsoupBrowser): Validated[NonEmptyChain[AuctionDomainValidation], CreateAuction] = {
    implicit val htmlDoc: jsoupBrowser.DocumentType = jsoupBrowser.get(auctionUrl)

    (validateAuctionType,
      validateId, auctionUrl.validNec, validateTitle, validateIsSold,
      validateSellerNickname, validateSellerLocation,
      validateStartPrice, validateFinalPrice,
      validateStartDate, validateEndDate,
      validateLargeImageUrl,
      validateBids).mapN(CreateAuction.apply)
  }

  def validateId(implicit htmlDoc: JsoupDocument): ValidationResult[String]

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
