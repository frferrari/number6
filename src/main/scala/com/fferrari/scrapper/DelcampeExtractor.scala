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

trait DelcampeExtractor {

  val SELLER_LOCATION_LABEL = "Location"
  val CLOSED_SELL_TAG = "div#closed-sell"
  // Closed auction
  val FIXED_TYPE_BIDS_CONTAINER = "div#tab-sales"
  val BID_TYPE_BIDS_CONTAINER = "div#tab-bids"
  // Ongoing auction
  val FIXED_TYPE_BUY_CONTAINER = "div#buy-box"
  val BID_TYPE_BUY_CONTAINER = "div#bid-box"

  type ValidationResult[A] = ValidatedNec[DomainValidation, A]

  def validateId(implicit htmlDoc: JsoupDocument): ValidationResult[String] = {
    (htmlDoc >?> attr("data-id")("div#confirm_question_modal"))
      .map(_.validNec)
      .getOrElse(IdNotFound.invalidNec)
  }

  def validateTitle(implicit htmlDoc: JsoupDocument): ValidationResult[String] =
    (htmlDoc >?> text("div.item-title h1 span"))
      .map(_.validNec)
      .getOrElse(TitleNotFound.invalidNec)

  def validateSellerNickname(implicit htmlDoc: JsoupDocument): ValidationResult[String] = {
    (htmlDoc >?> text("div#seller-info div.user-status a.member > span.nickname"))
      .map(_.validNec)
      .getOrElse(SellerNicknameNotFound.invalidNec)
  }

  def validateSellerLocation(implicit htmlDoc: JsoupDocument): ValidationResult[String] = {
    def sellerLocationLabelValidator(li: Element): Boolean =
      (li >?> text("strong")).exists(_.startsWith(SELLER_LOCATION_LABEL))

    def sellerLocationValueValidator(li: Element): Boolean =
      (li >?> text("div")).exists(_.split(",").nonEmpty)

    def sellerLocationLabelAndValueValidator(li: Element): Boolean =
      sellerLocationLabelValidator(li) & sellerLocationValueValidator(li)

    def sellerLocationNormalizer(location: String): String =
      location
        .split(",") // A Location can be of the following forms "Italy, Murano" or "Italy"
        .headOption
        .getOrElse("")

    (htmlDoc >> elementList("div#seller-info ul li"))
      .find(sellerLocationLabelAndValueValidator)
      .map(_ >> text("div"))
      .map(sellerLocationNormalizer(_).validNec)
      .getOrElse(SellerLocationNotFound.invalidNec)
  }

  def validateAuctionType(implicit htmlDoc: JsoupDocument): ValidationResult[AuctionType] = {
    def isBidType(classes: String): Boolean =
      classes.split(" ").contains("fa-shopping-cart")

    def isFixedType(classes: String): Boolean =
      classes.split(" ").contains("fa-gavel")

    def auctionTypeFromClasses(classes: String): ValidationResult[AuctionType] =
      if (isBidType(classes)) BidType.validNec
      else if (isFixedType(classes)) FixedPriceType.validNec
      else AuctionTypeNotFound.invalidNec

    println(htmlDoc >?> attr("class")("div.price-info div i"))

    (htmlDoc >?> attr("class")("div.price-info div i"))
      .map(auctionTypeFromClasses)
      .getOrElse(AuctionTypeNotFound.invalidNec)
  }

  def validateIsSold(implicit htmlDoc: JsoupDocument): ValidationResult[Boolean] =
    (htmlDoc >> elementList(s"${CLOSED_SELL_TAG} class.price-box")).nonEmpty.validNec

  def validateStartPrice(implicit htmlDoc: JsoupDocument): ValidationResult[Price] = {
    fetchClosedSellElement match {
      case Some(closedSell) =>
        // Closed auction
        (fetchPurchaseTableElement, fetchBidsTableElement) match {
          case (Some(_), None) =>
            // Closed auction, Fixed Price type of auction
            (closedSell >?> text("span.price"))
              .map(DelcampeTools.parseHtmlPrice)
              .getOrElse(StartPriceNotFound.invalidNec)
          case (None, Some(bidsTable)) =>
            // Closed auction, Bid type of auction
            (bidsTable >?> text("div.table-list-line:last-child li:nth-child(2) strong"))
              .map(DelcampeTools.parseHtmlPrice)
              .getOrElse(StartPriceNotFound.invalidNec)
          case _ =>
            StartPriceNotFound.invalidNec
        }
      case None =>
        // Ongoing auction
        (fetchBuyContainer, fetchBidContainer) match {
          case (Some(buyContainer), None) =>
            // Ongoing auction, Fixed Price type of auction
            (buyContainer >?> text("span.price"))
              .map(DelcampeTools.parseHtmlPrice)
              .getOrElse(StartPriceNotFound.invalidNec)
          case (None, Some(bidContainer)) =>
            // Ongoing auction, Bid type of auction
            (bidContainer >?> text("span.price"))
              .map(DelcampeTools.parseHtmlPrice)
              .getOrElse(StartPriceNotFound.invalidNec)
          case _ =>
            StartPriceNotFound.invalidNec
        }
    }
  }

  def validateFinalPrice(implicit htmlDoc: JsoupDocument): ValidationResult[Option[Price]] = {
    val htmlClosedSell: Option[Element] = fetchClosedSellElement
    val htmlPrice: Option[String] = htmlClosedSell.flatMap(_ >?> text("span.price"))

    (htmlClosedSell, htmlPrice) match {
      case (Some(_), Some(price)) =>
        // (OK) The auction is marked as sold and we could extract the price
        DelcampeTools.parseHtmlPrice(price).map(Option.apply)
      case (Some(_), None) =>
        // (KO) The auction is marked as sold but we couldn't extract the price
        FinalPriceNotFound.invalidNec
      case _ =>
        // (OK) The auction is not marked as sold, there's no final price to extract as it is not finished yet
        None.validNec
    }
  }

  def validateStartDate(implicit htmlDoc: JsoupDocument): ValidationResult[Date] =
    (htmlDoc >?> text("div#collapse-description div.description-info ul li:nth-child(1) div"))
      .map(DelcampeTools.parseHtmlDate)
      .getOrElse(StartDateNotFound.invalidNec)

  def validateEndDate(implicit htmlDoc: JsoupDocument): Validated[NonEmptyChain[DomainValidation], Option[Date]] = {
    val htmlClosedSell: Option[Element] = fetchClosedSellElement
    val htmlEndDate: Option[String] = htmlDoc >?> text("div#collapse-description div.description-info ul li:nth-child(2) div")

    (htmlClosedSell, htmlEndDate) match {
      case (Some(_), Some(endDate)) =>
        DelcampeTools.parseHtmlDate(endDate).map(Option.apply)
      case (Some(_), None) =>
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
    val htmlClosedSell: Option[Element] = fetchClosedSellElement
    val htmlPurchaseTable: Option[Element] = fetchPurchaseTableElement
    val htmlBidsTable: Option[Element] = fetchBidsTableElement

    (htmlClosedSell, htmlPurchaseTable, htmlBidsTable) match {
      case (Some(_), Some(_), None) =>
        fetchFixedPriceTypeBids
      case (Some(_), None, Some(_)) =>
        fetchBidTypeBids
      case (Some(_), None, None) =>
        MissingBidsForClosedAuction.invalidNec
      case (None, _, _) =>
        RequestForBidsForOngoingAuction.invalidNec
    }
  }

  def validateBidCount(implicit htmlDoc: JsoupDocument): ValidationResult[Int] = {
    val htmlClosedSell: Option[Element] = fetchClosedSellElement
    val htmlPurchaseTable: Option[Element] = fetchPurchaseTableElement
    val htmlBidsTable: Option[Element] = fetchBidsTableElement

    (htmlClosedSell, htmlPurchaseTable, htmlBidsTable) match {
      case (Some(_), Some(_), None) =>
        1.validNec
      case (Some(_), None, Some(bidsTable)) =>
        (bidsTable >?> elementList("div.bids-container div.bids div.table-list-line"))
          .map(_.size.validNec)
          .getOrElse(BidsContainerNotFound.invalidNec)
      case _ =>
        0.validNec
    }
  }

  def fetchFixedPriceTypeBids(implicit htmlDoc: JsoupDocument): Validated[NonEmptyChain[DomainValidation], List[Bid]] = {
    (htmlDoc >?> element(s"${FIXED_TYPE_BIDS_CONTAINER} div.table-view div.table-body ul.table-body-list"))
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
    fetchClosedSellElement
      .flatMap(_ >?> text("span.price"))
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
    val htmlBidsTable: Option[List[Element]] = htmlDoc >?> elementList(s"${BID_TYPE_BIDS_CONTAINER} div.bids-container ul.table-body-list")

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

  def fetchClosedSellElement(implicit htmlDoc: JsoupDocument): Option[Element] =
    htmlDoc >?> element(CLOSED_SELL_TAG)

  def fetchPurchaseTableElement(implicit htmlDoc: JsoupDocument): Option[Element] =
    htmlDoc >?> element(FIXED_TYPE_BIDS_CONTAINER)

  def fetchBidsTableElement(implicit htmlDoc: JsoupDocument): Option[Element] =
    htmlDoc >?> element(BID_TYPE_BIDS_CONTAINER)

  def fetchBidContainer(implicit htmlDoc: JsoupDocument): Option[Element] =
    htmlDoc >?> element(BID_TYPE_BUY_CONTAINER)

  def fetchBuyContainer(implicit htmlDoc: JsoupDocument): Option[Element] =
    htmlDoc >?> element(FIXED_TYPE_BUY_CONTAINER)
}
