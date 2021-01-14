package com.fferrari.validation

import java.time.LocalDateTime

import cats.data._
import cats.implicits._
import com.fferrari.model.Auction.AuctionType
import com.fferrari.model._
import com.fferrari.scraper.DelcampeUtil
import com.fferrari.scraper.DelcampeUtil.relativeToAbsoluteUrl
import net.ruippeixotog.scalascraper.browser.JsoupBrowser
import net.ruippeixotog.scalascraper.browser.JsoupBrowser.JsoupDocument
import net.ruippeixotog.scalascraper.dsl.DSL.Extract._
import net.ruippeixotog.scalascraper.dsl.DSL._
import net.ruippeixotog.scalascraper.model.Element
import net.ruippeixotog.scalascraper.scraper.ContentExtractors.{attr, elementList, text}

import scala.util.Try

class DelcampeValidator extends AuctionValidator {

  val SELLER_LOCATION_LABEL = "Location"
  val CLOSED_SELL_TAG = "div#closed-sell"
  // Closed auction
  val FIXED_TYPE_BIDS_CONTAINER = "div#tab-sales"
  val BID_TYPE_BIDS_CONTAINER = "div#tab-bids"
  // Ongoing auction
  val FIXED_TYPE_PRICE_CONTAINER = "div#buy-box"
  val BID_TYPE_PRICE_CONTAINER = "div#bid-box"

  val itemsPerPage: Int = 24

  override def fetchListingPage(batchSpecification: BatchSpecification,
                                getPage: String => Try[JsoupDocument],
                                pageNumber: Int = 1)
                               (implicit jsoupBrowser: JsoupBrowser): ValidationResult[JsoupDocument] = {
    def checkPageValidity(htmlDoc: JsoupDocument): ValidationResult[JsoupDocument] = {
      if ((htmlDoc >> texts("div.items.main div h2"))
        .toList
        .exists(_.contains("You have reached the limit of results to display")))
        MaximumNumberOfAllowedPagesReached.invalidNec
      else if ((htmlDoc >> elementList("div.items.main div.item-listing > div")).isEmpty)
        LastListingPageReached.invalidNec
      else
        htmlDoc.validNec
    }

    getPage(s"${batchSpecification.url}&order=sale_start_datetime&display_state=sold_items&size=$itemsPerPage&page=$pageNumber")
      .map(checkPageValidity)
      .getOrElse(ListingPageNotFound.invalidNec)
  }

  override def fetchAuctionUrls(batchSpecification: BatchSpecification)
                               (implicit htmlDoc: JsoupDocument): Validated[NonEmptyChain[AuctionDomainValidation], Batch] = {

    def fetchLinks(el: Element): ValidationResult[BatchAuctionLink] = {
      val auctionUrl: ValidationResult[String] =
        (el >?> attr("href")("div.item-info a.item-link"))
          .map(relativeToAbsoluteUrl(batchSpecification.url, _).validNec)
          .getOrElse(AuctionLinkNotFound.invalidNec)

      val thumbUrl: ValidationResult[String] =
        (el >?> attr("data-lazy")("img.image-thumb"))
          .map(_.validNec)
          .getOrElse(ThumbnailLinkNotFound.invalidNec)

      (auctionUrl, thumbUrl).mapN(BatchAuctionLink)
    }

    (htmlDoc >> elementList("div.items.main div.item-listing div.item-main-infos"))
      .map(fetchLinks)
      .sequence
      .map { batchAuctionLink =>
        batchSpecification.lastUrlScrapped match {
          case Some(lastScrappedUrl) if batchAuctionLink.map(_.auctionUrl).contains(lastScrappedUrl) =>
            // Keep only the auction urls that have not yet been processed (since the last run)
            batchAuctionLink.takeWhile(_.auctionUrl != lastScrappedUrl)
          case _ =>
            batchAuctionLink
        }
      }
      .map(Batch(nextBatchId, batchSpecification, _, Nil))
  }

  override def nextBatchId: String = {
    java.util.UUID.randomUUID().toString
  }

  override def validateExternalId(implicit htmlDoc: JsoupDocument): ValidationResult[String] = {
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
      if (isBidType(classes)) Auction.BID_TYPE_AUCTION.validNec
      else if (isFixedType(classes)) Auction.FIXED_PRICE_TYPE_AUCTION.validNec
      else AuctionTypeNotFound.invalidNec

    (htmlDoc >?> attr("class")("div.price-info div i"))
      .map(auctionTypeFromClasses)
      .getOrElse(AuctionTypeNotFound.invalidNec)
  }

  override def validateIsSold(implicit htmlDoc: JsoupDocument): ValidationResult[Boolean] = {
    (htmlDoc >?> elementList(s"${CLOSED_SELL_TAG}.price-box")).exists(_.nonEmpty).validNec
  }

  override def validateStartPrice(implicit htmlDoc: JsoupDocument): ValidationResult[Price] = {
    fetchClosedSellElement match {
      case Some(closedSell) =>
        // Closed auction
        (fetchPurchaseTableElement, fetchBidsTableElement) match {
          case (Some(_), None) =>
            // Closed auction, Fixed Price type of auction
            (closedSell >?> text("span.price"))
              .map(DelcampeUtil.parseHtmlPrice)
              .getOrElse(StartPriceNotFound.invalidNec)
          case (None, Some(bidsTable)) =>
            // Closed auction, Bid type of auction
            (bidsTable >?> text("div.table-list-line:last-child li:nth-child(2) strong"))
              .map(DelcampeUtil.parseHtmlPrice)
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
              .map(DelcampeUtil.parseHtmlPrice)
              .getOrElse(StartPriceNotFound.invalidNec)
          case (None, Some(bidContainer)) =>
            // Ongoing auction, Bid type of auction
            (bidContainer >?> text("span.price"))
              .map(DelcampeUtil.parseHtmlPrice)
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
        DelcampeUtil.parseHtmlPrice(price).map(Option.apply)
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
      .map(DelcampeUtil.parseHtmlDate)
      .getOrElse(StartDateNotFound.invalidNec)

  override def validateEndDate(implicit htmlDoc: JsoupDocument): Validated[NonEmptyChain[AuctionDomainValidation], Option[LocalDateTime]] = {
    val htmlClosedSell: Option[Element] = fetchClosedSellElement
    val htmlEndDate: Option[String] = htmlDoc >?> text("div#collapse-description div.description-info ul li:nth-child(2) div")

    (htmlClosedSell, htmlEndDate) match {
      case (Some(_), Some(endDate)) =>
        DelcampeUtil.parseHtmlDate(endDate).map(Option.apply)
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
        bidsTable >> elementList("div.bids-container div.bids div.table-list-line ul li.list-user") match {
          case listLines@h :: t => listLines.size.validNec
          case _ => BidsContainerNotFound.invalidNec
        }
      case _ =>
        RequestForBidCountForOngoingAuction.invalidNec
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
      .map(DelcampeUtil.parseHtmlPrice)
      .getOrElse(BidPriceNotFound.invalidNec)

  def fetchFixedPriceTypePurchaseQuantity(bid: Element): ValidationResult[Int] =
    (bid >?> text("li:nth-child(2)"))
      .map(DelcampeUtil.parseHtmlQuantity)
      .getOrElse(InvalidBidQuantityFormat.invalidNec)

  def fetchFixedPriceTypePurchaseDate(bid: Element): ValidationResult[LocalDateTime] = {
    val htmlPurchaseDate: Option[String] = bid >?> text("li:nth-child(3)")
    val htmlPurchaseTime: Option[String] = bid >?> text("li:nth-child(4)")

    (htmlPurchaseDate, htmlPurchaseTime) match {
      case (Some(purchaseDate), Some(purchaseTime)) =>
        DelcampeUtil.parseHtmlShortDate(s"$purchaseDate $purchaseTime")
      case _ =>
        InvalidShortDateFormat.invalidNec
    }
  }

  def fetchBidTypeBids(implicit htmlDoc: JsoupDocument): ValidationResult[List[Bid]] = {
    htmlDoc >> elementList(s"${BID_TYPE_BIDS_CONTAINER} div.bids-container ul.table-body-list") match {
      case bids@h :: t =>
        bids.map { bid =>
          val htmlNickname: ValidationResult[String] = fetchBidTypeBidderNickname(bid)
          val isAutomaticBid: ValidationResult[Boolean] = fetchBidTypeIsAutomaticBid(bid)
          val htmlPrice: ValidationResult[Price] = fetchBidTypeBidPrice(bid)
          val htmlBidDate: ValidationResult[LocalDateTime] = fetchBidTypeBidDate(bid)

          (htmlNickname, htmlPrice, 1.validNec, isAutomaticBid, htmlBidDate).mapN(Bid)
        }.sequence
      case _ =>
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
      .map(DelcampeUtil.parseHtmlPrice)
      .getOrElse(BidPriceNotFound.invalidNec)

  def fetchBidTypeBidDate(bid: Element): ValidationResult[LocalDateTime] =
    (bid >?> text("li:nth-child(3)"))
      .map(DelcampeUtil.parseHtmlShortDate)
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
