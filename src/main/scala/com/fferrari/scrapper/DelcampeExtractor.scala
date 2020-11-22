package com.fferrari.scrapper

import java.util.Date

import cats.data._
import cats.implicits._
import com.fferrari.model.{Bid, Price}
import net.ruippeixotog.scalascraper.browser.JsoupBrowser.JsoupDocument
import net.ruippeixotog.scalascraper.dsl.DSL.Extract._
import net.ruippeixotog.scalascraper.dsl.DSL.{deepFunctorOps, validator, _}
import net.ruippeixotog.scalascraper.model.Element
import net.ruippeixotog.scalascraper.scraper.ContentExtractors.{attr, elementList, text}

sealed trait DelcampeExtractor {

  val SELLER_LOCATION_LABEL = "Location"

  type ValidationResult[A] = ValidatedNec[DomainValidation, A]

  def validateId(implicit htmlDoc: JsoupDocument): ValidationResult[String] =
    if ((htmlDoc >/~ validator(attrs("div#confirm_question_modal"))(_.exists(_ == "data-id"))).isRight)
      (htmlDoc >> attr("data-id")("div#confirm_question_modal")).validNec
    else
      IdNotFound.invalidNec

  def validateTitle(implicit htmlDoc: JsoupDocument): ValidationResult[String] =
    if ((htmlDoc >/~ validator(text("div.item-title h1 span"))(_.nonEmpty)).isRight)
      (htmlDoc >> text("div.item-title h1 span")).validNec
    else
      TitleNotFound.invalidNec

  def validateSellerNickname(implicit htmlDoc: JsoupDocument): ValidationResult[String] =
    if ((htmlDoc >/~ validator(text("div#seller-info div.user-status a.member > span.nickname"))(_.nonEmpty)).isRight)
      (htmlDoc >> text("div#seller-info div.user-status a.member > span.nickname")).validNec
    else
      SellerNicknameNotFound.invalidNec

  def validateSellerLocation(implicit htmlDoc: JsoupDocument): ValidationResult[String] = {
    def sellerLocationLabelValidator(li: Element): Boolean =
      (li >/~ validator(text("strong"))(_.startsWith(SELLER_LOCATION_LABEL))).isRight

    def sellerLocationValueValidator(li: Element): Boolean =
      (li >/~ validator(text("div"))(_.split(",").nonEmpty)).isRight

    def sellerLocationLabelAndValueValidator(li: Element): Boolean =
      sellerLocationLabelValidator(li) & sellerLocationValueValidator(li)

    def sellerLocationNormalizer(location: String): String =
      location
        .split(",") // A Location can be of the following forms "Italy, Murano" or "Italy"
        .head

    if ((htmlDoc >/~ validator(elementList("div#seller-info ul li"))(_.exists(sellerLocationLabelAndValueValidator))).isRight)
      (htmlDoc >> elementList("div#seller-info ul li"))
        .find(sellerLocationLabelValidator)
        .map(li => sellerLocationNormalizer(li >> text("div")))
        .getOrElse("")
        .validNec
    else
      SellerLocationNotFound.invalidNec
  }

  def validateAuctionType(implicit htmlDoc: JsoupDocument): ValidationResult[AuctionType] =
    if ((htmlDoc >/~ validator(attr("class")("div.price-info > div > i"))(c => List("fa-gavel", "fa-shipping-cart").contains(c))).isRight)
      (htmlDoc >> attr("class")("div.price-info > div > i") match {
        case c if c.contains("fa-gavel") =>
          BidType
        case _ =>
          FixedPriceType
      }).validNec
    else
      AuctionTypeNotFound.invalidNec

  def validateIsSold(implicit htmlDoc: JsoupDocument): ValidationResult[Boolean] =
    (htmlDoc >> elementList("div#closed-sell class.price-box")).nonEmpty.validNec

  def validateStartPrice(implicit htmlDoc: JsoupDocument): ValidatedNec[NonEmptyChain[DomainValidation], Price] = {
    (for {
      auctionType <- validateAuctionType.toEither
      isSold <- validateIsSold.toEither
      tag = startPriceExtractorTag(auctionType, isSold)
      _ <- htmlPriceValidator(tag).toEither
      htmlPrice = htmlDoc >> text(tag)
      priceNec = DelcampeTools.parseHtmlPrice(htmlPrice).toEither
      price <- priceNec
    } yield price).toValidatedNec
  }

  def startPriceExtractorTag(auctionType: AuctionType, isSold: Boolean): String =
    auctionType match {
      case FixedPriceType if isSold =>
        "div#closed-sell span.price"
      case FixedPriceType if !isSold =>
        "div#buy-box span.price"
      case BidType if isSold =>
        "div#tab-bids div.table-list-line:nth-child(1) li:nth-child(2) strong"
      case _ =>
        "div#bid-box span.price"
    }

  def htmlPriceValidator(htmlPriceExtractor: String)(implicit htmlDoc: JsoupDocument): ValidationResult[JsoupDocument] =
    (htmlDoc >/~ validator(elementList(htmlPriceExtractor))(_.nonEmpty))
      .map(_.validNec)
      .getOrElse(StartPriceNotFound.invalidNec)


  def validateFinalPrice(implicit htmlDoc: JsoupDocument): ValidationResult[Option[Price]] = {
    val htmlClosedSell: Option[Element] = htmlDoc >?> element("div#closed-sell")
    val htmlPrice: Option[String] = htmlClosedSell.flatMap(_ >?> text("span.price"))

    (htmlClosedSell, htmlPrice) match {
      case (Some(closedSell), Some(price)) =>
        // (OK) The auction is marked as sold and we could extract the price
        DelcampeTools.parseHtmlPrice(price).map(Option.apply)
      case (Some(closedSell), None) =>
        // (KO) The auction is marked as sold but we couldn't extract the price
        FinalPriceNotFound.invalidNec
      case _ =>
        // (OK) The auction is not marked as sold, no final price as it is not finished yet
        None.validNec
    }
  }

  def validateStartDate(implicit htmlDoc: JsoupDocument): ValidationResult[Date] =
    (htmlDoc >?> text("div#collapse-description div.description-info ul li:nth-child(1) div"))
      .map(DelcampeTools.parseHtmlDate)
      .getOrElse(StartDateNotFound.invalidNec)

  def validateEndDate(implicit htmlDoc: JsoupDocument): Validated[NonEmptyChain[DomainValidation], Option[Date]] = {
    val htmlClosedSell: Option[Element] = htmlDoc >?> element("div#closed-sell")
    val htmlEndDate: Option[String] = htmlDoc >?> text("div#collapse-description div.description-info ul li:nth-child(2) div")

    (htmlClosedSell, htmlEndDate) match {
      case (Some(closedSell), Some(endDate)) =>
        DelcampeTools.parseHtmlDate(endDate).map(Option.apply)
      case (Some(closedSell), None) =>
        EndDateNotFound.invalidNec
      case _ =>
        None.validNec
    }
  }

  def validateLargeImageUrl(implicit htmlDoc: JsoupDocument): ValidationResult[String] =
    (htmlDoc >?> attr("src")("div.item-thumbnails img.img-lense"))
      .map(_.validNec)
      .getOrElse(LargeImageUrlNotFound.invalidNec)

  def validateBids(implicit htmlDoc: JsoupDocument): ValidationResult[List[Bid]] = {
    val htmlClosedSell: Option[Element] = htmlDoc >?> element("div#closed-sell")
    val htmlPurchaseTable: Option[Element] = htmlDoc >?> element("div#tab-sales")
    val htmlBidsTable: Option[Element] = htmlDoc >?> element("div#tab-bids")

    (htmlClosedSell, htmlPurchaseTable, htmlBidsTable) match {
      case (Some(true), Some(true), None) =>
        fetchFixedPriceTypeBids
      case (Some(true), None, Some(true)) =>
        fetchBidTypeBids
      case (Some(true), None, None) =>
        MissingBidsForClosedAuction.invalidNec
      case (None, _, _) =>
        RequestForBidsForOngoingAuction.invalidNec
    }
  }

  def fetchFixedPriceTypeBids(implicit htmlDoc: JsoupDocument): Validated[NonEmptyChain[DomainValidation], List[Bid]] = {
    val htmlPurchaseTable: Option[Element] = htmlDoc >?> element("div#tab-sales div.table-view div.table-body ul.table-body-list")

    htmlPurchaseTable
      .map { purchase =>
        val htmlNickname: ValidationResult[String] = fetchFixedPriceTypeNickname(purchase)
        val htmlPrice: ValidationResult[Price] = fetchFixedPriceTypePrice
        val htmlBidQuantity: ValidationResult[Int] = fetchFixedPriceTypeQuantity(purchase)
        val htmlPurchaseDate: ValidationResult[Date] = fetchFixedPriceTypeDate(purchase)

        (htmlNickname, htmlPrice, htmlBidQuantity, false.validNec, htmlPurchaseDate)
          .mapN(Bid)
          .map(List(_))
      }.getOrElse(BidsContainerNotFound.invalidNec)
  }

  def fetchFixedPriceTypeNickname(bid: Element): ValidationResult[String] =
    (bid >?> text("li:nth-child(1) span"))
      .map(_.validNec)
      .getOrElse(BidderNicknameNotFound.invalidNec)

  def fetchFixedPriceTypePrice(implicit htmlDoc: JsoupDocument): ValidationResult[Price] =
    (htmlDoc >?> text("div#closed-sell span.price"))
      .map(DelcampeTools.parseHtmlPrice)
      .getOrElse(BidPriceNotFound.invalidNec)

  def fetchFixedPriceTypeQuantity(bid: Element): ValidationResult[Int] =
    (bid >?> text("li:nth-child(2)"))
      .map(DelcampeTools.parseHtmlQuantity)
      .getOrElse(InvalidBidQuantity.invalidNec)

  def fetchFixedPriceTypeDate(bid: Element): ValidationResult[Date] = {
    val htmlPurchaseDate: Option[String] = bid >?> text("li:nth-child(3)")
    val htmlPurchaseTime: Option[String] = bid >?> text("li:nth-child(4)")

    (htmlPurchaseDate, htmlPurchaseTime) match {
      case (Some(purchaseDate), Some(purchaseTime)) =>
        DelcampeTools.parseHtmlShortDate(s"$purchaseDate $purchaseTime")
      case _ =>
        InvalidShortDateFormat.invalidNec
    }
  }

  def fetchBidTypeBids(implicit htmlDoc: JsoupDocument): ValidationResult[List[Bid]] = {
    val htmlBidsTable: Option[List[Element]] = htmlDoc >?> elementList("div#tab-bids div.bids-container ul.table-body-list")

    htmlBidsTable match {
      case Some(bids) =>
        bids.map { bid =>
          val htmlNickname: ValidationResult[String] = fetchBidTypeNickname(bid)
          val isAutomaticBid: ValidationResult[Boolean] = fetchBidTypeIsAutomatic(bid)
          val htmlPrice: ValidationResult[Price] = fetchBidTypePrice(bid)
          val htmlBidDate: ValidationResult[Date] = fetchBidTypeDate(bid)

          (htmlNickname, htmlPrice, 1.validNec, isAutomaticBid, htmlBidDate).mapN(Bid)
        }.sequence
      case None =>
        BidsContainerNotFound.invalidNec
    }
  }

  def fetchBidTypeNickname(bid: Element): ValidationResult[String] =
    (bid >?> text("li:nth-child(1) span.nickname"))
      .map(_.validNec)
      .getOrElse(BidderNicknameNotFound.invalidNec)

  def fetchBidTypeIsAutomatic(bid: Element): ValidationResult[Boolean] =
    (bid >?> text("li:nth-child(2) span"))
      .map(automatic => (automatic == "automatic").validNec)
      .getOrElse(false.validNec)

  def fetchBidTypePrice(bid: Element): ValidationResult[Price] =
    (bid >?> text("li:nth-child(2) strong"))
      .map(DelcampeTools.parseHtmlPrice)
      .getOrElse(BidPriceNotFound.invalidNec)

  def fetchBidTypeDate(bid: Element): ValidationResult[Date] =
    (bid >?> text("li:nth-child(3)"))
      .map(DelcampeTools.parseHtmlShortDate)
      .getOrElse(BidDateNotFound.invalidNec)
}
