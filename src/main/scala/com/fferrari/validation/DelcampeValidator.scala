package com.fferrari.validation

import java.time.LocalDateTime

import cats.data._
import cats.implicits._
import com.fferrari.PriceScrapperProtocol.WebsiteInfo
import com.fferrari.model._
import com.fferrari.scrapper.DelcampeTools
import com.fferrari.scrapper.DelcampeTools.relativeToAbsoluteUrl
import net.ruippeixotog.scalascraper.browser.JsoupBrowser
import net.ruippeixotog.scalascraper.browser.JsoupBrowser.JsoupDocument
import net.ruippeixotog.scalascraper.dsl.DSL.Extract._
import net.ruippeixotog.scalascraper.dsl.DSL._
import net.ruippeixotog.scalascraper.model.Element
import net.ruippeixotog.scalascraper.scraper.ContentExtractors.{attr, elementList, text}

class DelcampeValidator extends AuctionValidator {

  val SELLER_LOCATION_LABEL = "Location"
  val CLOSED_SELL_TAG = "div#closed-sell"
  // Closed auction
  val FIXED_TYPE_BIDS_CONTAINER = "div#tab-sales"
  val BID_TYPE_BIDS_CONTAINER = "div#tab-bids"
  // Ongoing auction
  val FIXED_TYPE_PRICE_CONTAINER = "div#buy-box"
  val BID_TYPE_PRICE_CONTAINER = "div#bid-box"

  override def fetchListingPage(websiteInfo: WebsiteInfo, itemsPerPage: Int, pageNumber: Int = 1)
                               (implicit jsoupBrowser: JsoupBrowser): JsoupDocument =
    jsoupBrowser.get(s"${websiteInfo.url}&size=$itemsPerPage&page=$pageNumber")

  override def fetchListingPageUrls(websiteInfo: WebsiteInfo)
                                   (implicit htmlDoc: JsoupDocument): List[String] = {

    val containerOfUrls: ValidationResult[Element] =
      (htmlDoc >?> element(".item-listing .item-main-infos div.item-info"))
        .map(_.validNec)
        .getOrElse(ContainerOfUrlsNotFound.invalidNec)

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

  override def validateId(implicit htmlDoc: JsoupDocument): ValidationResult[String] = {
    (htmlDoc >?> attr("data-id")("div#confirm_question_modal"))
      .map(_.validNec)
      .getOrElse(IdNotFound.invalidNec)
  }

  override def validateTitle(implicit htmlDoc: JsoupDocument): ValidationResult[String] =
    (htmlDoc >?> text("div.item-title h1 span"))
      .map(_.validNec)
      .getOrElse(TitleNotFound.invalidNec)

  override def validateSellerNickname(implicit htmlDoc: JsoupDocument): ValidationResult[String] = {
    (htmlDoc >?> text("div#seller-info div.user-status a.member > span.nickname"))
      .map(_.validNec)
      .getOrElse(SellerNicknameNotFound.invalidNec)
  }

  override def validateSellerLocation(implicit htmlDoc: JsoupDocument): ValidationResult[String] = {
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

  override def validateAuctionType(implicit htmlDoc: JsoupDocument): ValidationResult[AuctionType] = {
    def isBidType(classes: String): Boolean =
      classes.split(" ").contains("fa-gavel")

    def isFixedType(classes: String): Boolean =
      classes.split(" ").contains("fa-shopping-cart")

    def auctionTypeFromClasses(classes: String): ValidationResult[AuctionType] =
      if (isBidType(classes)) BidType.validNec
      else if (isFixedType(classes)) FixedPriceType.validNec
      else AuctionTypeNotFound.invalidNec

    (htmlDoc >?> attr("class")("div.price-info div i"))
      .map(auctionTypeFromClasses)
      .getOrElse(AuctionTypeNotFound.invalidNec)
  }

  override def validateIsSold(implicit htmlDoc: JsoupDocument): ValidationResult[Boolean] =
    (htmlDoc >?> elementList(s"${CLOSED_SELL_TAG}.price-box")).nonEmpty.validNec

  override def validateStartPrice(implicit htmlDoc: JsoupDocument): ValidationResult[Price] = {
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
        (fetchFixedTypePriceContainer, fetchBidTypePriceContainer) match {
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

  override def validateFinalPrice(implicit htmlDoc: JsoupDocument): ValidationResult[Option[Price]] = {
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

  override def validateStartDate(implicit htmlDoc: JsoupDocument): ValidationResult[LocalDateTime] =
    (htmlDoc >?> text("div#collapse-description div.description-info ul li:nth-child(1) div"))
      .map(DelcampeTools.parseHtmlDate)
      .getOrElse(StartDateNotFound.invalidNec)

  override def validateEndDate(implicit htmlDoc: JsoupDocument): Validated[NonEmptyChain[AuctionDomainValidation], Option[LocalDateTime]] = {
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

  override def validateLargeImageUrl(implicit htmlDoc: JsoupDocument): ValidationResult[String] =
    (htmlDoc >?> attr("src")("div.item-thumbnails img.img-lense"))
      .map(_.validNec)
      .getOrElse(LargeImageUrlNotFound.invalidNec)

  override def validateBids(implicit htmlDoc: JsoupDocument): ValidationResult[List[Bid]] = {
    val htmlClosedSell: Option[Element] = fetchClosedSellElement
    val htmlPurchaseTable: Option[Element] = fetchPurchaseTableElement
    val htmlBidsTable: Option[Element] = fetchBidsTableElement

    (htmlClosedSell, htmlPurchaseTable, htmlBidsTable) match {
      case (Some(_), Some(_), _) =>
        fetchFixedPriceTypeBids
      case (Some(_), _, Some(_)) =>
        fetchBidTypeBids
      case (Some(_), None, None) =>
        MissingBidsForClosedAuction.invalidNec
      case (None, _, _) =>
        RequestForBidsForOngoingAuction.invalidNec
    }
  }

  override def validateBidCount(implicit htmlDoc: JsoupDocument): ValidationResult[Int] = {
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

  def fetchFixedPriceTypeBids(implicit htmlDoc: JsoupDocument): Validated[NonEmptyChain[AuctionDomainValidation], List[Bid]] = {
    (htmlDoc >?> element(s"${FIXED_TYPE_BIDS_CONTAINER} div.table-view div.table-body ul.table-body-list"))
      .map { purchase =>
        val htmlNickname: ValidationResult[String] = fetchFixedPriceTypePurchaseNickname(purchase)
        val htmlPrice: ValidationResult[Price] = fetchFixedPriceTypePurchasePrice
        val htmlBidQuantity: ValidationResult[Int] = fetchFixedPriceTypePurchaseQuantity(purchase)
        val htmlPurchaseDate: ValidationResult[LocalDateTime] = fetchFixedPriceTypePurchaseDate(purchase)

        (htmlNickname, htmlPrice, htmlBidQuantity, false.validNec, htmlPurchaseDate)
          .mapN(Bid)
          .map(List(_))
      }.getOrElse(BidsContainerNotFound.invalidNec)
  }

  def fetchFixedPriceTypePurchaseNickname(bid: Element): ValidationResult[String] =
    (bid >?> text("li:nth-child(1) span"))
      .map(_.validNec)
      .getOrElse(BidderNicknameNotFound.invalidNec)

  def fetchFixedPriceTypePurchasePrice(implicit htmlDoc: JsoupDocument): ValidationResult[Price] =
    fetchClosedSellElement
      .flatMap(_ >?> text("span.price"))
      .map(DelcampeTools.parseHtmlPrice)
      .getOrElse(BidPriceNotFound.invalidNec)

  def fetchFixedPriceTypePurchaseQuantity(bid: Element): ValidationResult[Int] =
    (bid >?> text("li:nth-child(2)"))
      .map(DelcampeTools.parseHtmlQuantity)
      .getOrElse(InvalidBidQuantity.invalidNec)

  def fetchFixedPriceTypePurchaseDate(bid: Element): ValidationResult[LocalDateTime] = {
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
          val htmlNickname: ValidationResult[String] = fetchBidTypeBidderNickname(bid)
          val isAutomaticBid: ValidationResult[Boolean] = fetchBidTypeIsAutomaticBid(bid)
          val htmlPrice: ValidationResult[Price] = fetchBidTypeBidPrice(bid)
          val htmlBidDate: ValidationResult[LocalDateTime] = fetchBidTypeBidDate(bid)

          (htmlNickname, htmlPrice, 1.validNec, isAutomaticBid, htmlBidDate).mapN(Bid)
        }.sequence
      case None =>
        BidsContainerNotFound.invalidNec
    }
  }

  def fetchBidTypeBidderNickname(bid: Element): ValidationResult[String] =
    (bid >?> text("li:nth-child(1) span.nickname"))
      .map(_.validNec)
      .getOrElse(BidderNicknameNotFound.invalidNec)

  def fetchBidTypeIsAutomaticBid(bid: Element): ValidationResult[Boolean] =
    (bid >?> text("li:nth-child(2) span"))
      .map(automatic => (automatic == "automatic").validNec)
      .getOrElse(false.validNec)

  def fetchBidTypeBidPrice(bid: Element): ValidationResult[Price] =
    (bid >?> text("li:nth-child(2) strong"))
      .map(DelcampeTools.parseHtmlPrice)
      .getOrElse(BidPriceNotFound.invalidNec)

  def fetchBidTypeBidDate(bid: Element): ValidationResult[LocalDateTime] =
    (bid >?> text("li:nth-child(3)"))
      .map(DelcampeTools.parseHtmlShortDate)
      .getOrElse(BidDateNotFound.invalidNec)

  def fetchClosedSellElement(implicit htmlDoc: JsoupDocument): Option[Element] =
    htmlDoc >?> element(CLOSED_SELL_TAG)

  def fetchPurchaseTableElement(implicit htmlDoc: JsoupDocument): Option[Element] =
    htmlDoc >?> element(FIXED_TYPE_BIDS_CONTAINER)

  def fetchBidsTableElement(implicit htmlDoc: JsoupDocument): Option[Element] =
    htmlDoc >?> element(BID_TYPE_BIDS_CONTAINER)

  def fetchBidTypePriceContainer(implicit htmlDoc: JsoupDocument): Option[Element] =
    htmlDoc >?> element(BID_TYPE_PRICE_CONTAINER)

  def fetchFixedTypePriceContainer(implicit htmlDoc: JsoupDocument): Option[Element] =
    htmlDoc >?> element(FIXED_TYPE_PRICE_CONTAINER)
}
