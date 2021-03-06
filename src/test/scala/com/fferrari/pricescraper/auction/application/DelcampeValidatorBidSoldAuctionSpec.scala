package com.fferrari.pricescraper.auction.application

import java.time.{LocalDateTime, ZoneOffset}

import cats.data.Chain
import cats.data.Validated.{Invalid, Valid}
import com.fferrari.pricescraper.auction.domain.{Bid, Price}
import net.ruippeixotog.scalascraper.browser.JsoupBrowser
import net.ruippeixotog.scalascraper.dsl.DSL.Extract._
import net.ruippeixotog.scalascraper.dsl.DSL._
import net.ruippeixotog.scalascraper.model.Element
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class DelcampeValidatorBidSoldAuctionSpec
  extends AnyFlatSpec
    with Matchers
    with DelcampeValidatorSpecFixtures {

  val delcampeValidator: DelcampeValidator = new DelcampeValidator

  implicit val jsoupBrowser: JsoupBrowser = JsoupBrowser.typed()
  val htmlDoc: JsoupBrowser.JsoupDocument = jsoupBrowser.get(BID_TYPE_SOLD_URL)

  it should "extract the auction ID for a SOLD auction of BID type" in {
    delcampeValidator.validateExternalId(htmlDoc) shouldBe Valid("1120841389")
  }
  it should "produce a IdNotFound from some invalid HTML string" in {
    val htmlString = """<div id="confirm_question_modal"></div>"""
    delcampeValidator.validateExternalId(jsoupBrowser.parseString(htmlString)) shouldBe Invalid(Chain(IdNotFound))
  }

  it should "extract the auction TITLE for a SOLD auction of BID type" in {
    delcampeValidator.validateTitle(htmlDoc) shouldBe Valid("TIMBRES DE FRANCE N° 3269 A 3278 OBLITÉRÉS ( LOT:4933 )")
  }
  it should "produce a TitleNotFound from some invalid HTML string" in {
    val htmlString = """<div class="item-title"><h1></h1></div>"""
    delcampeValidator.validateTitle(jsoupBrowser.parseString(htmlString))
      Invalid(Chain(TitleNotFound))
  }

  it should "extract the auction SELLER NICKNAME for a SOLD auction of BID type" in {
    delcampeValidator.validateSellerNickname(htmlDoc) shouldBe Valid("lulu321")
  }
  it should "produce SellerNicknameNotFound from some invalid HTML string" in {
    val htmlString = """<div id="seller-info"><div class="user-status"><a class="member"><span></span></a></div></div>"""
    delcampeValidator.validateSellerNickname(jsoupBrowser.parseString(htmlString)) shouldBe
      Invalid(Chain(SellerNicknameNotFound))
  }

  it should "extract the auction SELLER LOCATION for a SOLD auction of BID type" in {
    delcampeValidator.validateSellerLocation(htmlDoc) shouldBe Valid("France")
  }
  it should "produce SellerLocationNotFound from some invalid HTML string" in {
    val htmlString =
      """<div id="seller-info">
        | <ul>
        |   <li>
        |     <strong>Location:</strong>
        |   </li>
        |   <li>
        |   </li>
        | </ul>
        |</div>""".stripMargin
    delcampeValidator.validateSellerLocation(jsoupBrowser.parseString(htmlString)) shouldBe
      Invalid(Chain(SellerLocationNotFound))
  }

  it should "extract the auction START PRICE for a SOLD auction of BID type" in {
    delcampeValidator.validateStartPrice(htmlDoc) shouldBe Valid(Price(2.0, "EUR"))
  }
  it should "produce StartPriceNotFound from some invalid HTML string" in {
    val htmlString = """<div id="closed-sell"></div>"""
    delcampeValidator.validateStartPrice(jsoupBrowser.parseString(htmlString)) shouldBe
      Invalid(Chain(StartPriceNotFound))
  }
  it should "produce StartPriceNotFound from an ONGOING auction of type BID missing the START PRICE node" in {
    val htmlString = """<div><div id="buy-box"></div></div>"""
    delcampeValidator.validateStartPrice(jsoupBrowser.parseString(htmlString)) shouldBe
      Invalid(Chain(StartPriceNotFound))
  }
  it should "produce StartPriceNotFound from an ONGOING auction missing the BID TYPE node" in {
    val htmlString = """<div><div></div></div>"""
    delcampeValidator.validateStartPrice(jsoupBrowser.parseString(htmlString)) shouldBe
      Invalid(Chain(StartPriceNotFound))
  }
  it should "produce StartPriceNotFound for a SOLD auction missing the START PRICE node" in {
    val htmlString = """<div id="closed-sell"><div id="tab-bids"></div></div>"""
    delcampeValidator.validateStartPrice(jsoupBrowser.parseString(htmlString)) shouldBe
      Invalid(Chain(StartPriceNotFound))
  }
  it should "produce StartPriceNotFound for an ONGOING auction missing the START PRICE node" in {
    val htmlString = """<div id="bid-box"></div>"""
    delcampeValidator.validateStartPrice(jsoupBrowser.parseString(htmlString)) shouldBe
      Invalid(Chain(StartPriceNotFound))
  }
  it should "extract the price for an ONGOING auction" in {
    val htmlString = """<div id="bid-box"><span class="price">€2.80</span></div>"""
    delcampeValidator.validateStartPrice(jsoupBrowser.parseString(htmlString)) shouldBe
      Valid(Price(2.80, "EUR"))
  }

  it should "extract the auction FINAL PRICE for a SOLD auction of BID type" in {
    delcampeValidator.validateFinalPrice(htmlDoc) shouldBe Valid(Some(Price(2.30, "EUR")))
  }
  it should "produce FinalPriceNotFound from some invalid HTML string" in {
    val htmlString = """<div id="closed-sell" class="price-box"></div>"""
    delcampeValidator.validateFinalPrice(jsoupBrowser.parseString(htmlString)) shouldBe
      Invalid(Chain(FinalPriceNotFound))
  }
  it should "produce None when extracting the Final Price from an ongoing auction" in {
    val htmlString = """<div></div>"""
    delcampeValidator.validateFinalPrice(jsoupBrowser.parseString(htmlString)) shouldBe
      Valid(None)
  }

  it should "extract the auction START DATE for a SOLD auction of BID type" in {
    delcampeValidator.validateStartDate(htmlDoc) shouldBe
      Valid(LocalDateTime.of(2020, 11, 3, 5, 53, 0).toInstant(ZoneOffset.UTC))
  }
  it should "produce StartDateNotFound from an invalid date in a valid HTML string" in {
    val htmlString = """<div id="collapse-description"><div class="description-info"><ul><li>Invalid Date</li></ul></div></div>"""
    delcampeValidator.validateStartDate(jsoupBrowser.parseString(htmlString)) shouldBe
      Invalid(Chain(StartDateNotFound))
  }
  it should "produce StartDateNotFound from an invalid HTML string" in {
    val htmlString = """<div id="collapse-description"><div class="description-info"><ul></ul></div></div>"""
    delcampeValidator.validateStartDate(jsoupBrowser.parseString(htmlString)) shouldBe
      Invalid(Chain(StartDateNotFound))
  }

  it should "extract the auction END DATE for a SOLD auction of BID type" in {
    delcampeValidator.validateEndDate(htmlDoc) shouldBe
      Valid(Some(LocalDateTime.of(2020, 11, 10, 19, 0).toInstant(ZoneOffset.UTC)))
  }
  it should "produce EndDateNotFound from an invalid date in a valid HTML string" in {
    val htmlString = """<div id="closed-sell" class="price-box"><div id="collapse-description"><div class="description-info"><ul></ul></div></div></div>"""
    delcampeValidator.validateEndDate(jsoupBrowser.parseString(htmlString)) shouldBe
      Invalid(Chain(EndDateNotFound))
  }
  it should "produce EndDateNotFound from an invalid HTML string" in {
    val htmlString = """<div></div>"""
    delcampeValidator.validateEndDate(jsoupBrowser.parseString(htmlString)) shouldBe
      Valid(None)
  }

  it should "extract the auction LARGE IMAGE URL for a SOLD auction of BID type" in {
    delcampeValidator.validateLargeImageUrl(htmlDoc) shouldBe
      Valid("https://delcampe-static.net/img_large/auction/001/120/841/389_001.jpg?v=1")
  }
  it should "produce a LargeImageUrlNotFound from an invalid HTML string" in {
    val htmlString = """<div></div>"""
    delcampeValidator.validateLargeImageUrl(jsoupBrowser.parseString(htmlString)) shouldBe
      Invalid(Chain(LargeImageUrlNotFound))
  }

  it should "extract the auction BIDS for a SOLD auction of BID type" in {
    delcampeValidator.validateBids(htmlDoc) shouldBe
      Valid(
        List(
          Bid("chantal27", Price(2.30, "EUR"), 1, false, LocalDateTime.of(2020, 11, 10, 18, 51, 41).toInstant(ZoneOffset.UTC)),
          Bid("bercat51", Price(2.20, "EUR"), 1, false, LocalDateTime.of(2020, 11, 10, 13, 39, 43).toInstant(ZoneOffset.UTC)),
          Bid("basket2505", Price(2.10, "EUR"), 1, false, LocalDateTime.of(2020, 11, 10, 10, 56, 30).toInstant(ZoneOffset.UTC)),
          Bid("bercat51", Price(2.00, "EUR"), 1, false, LocalDateTime.of(2020, 11, 4, 18, 20, 19).toInstant(ZoneOffset.UTC))
        )
      )
  }
  it should "produce a MissingBidsForClosedAuction from a closed auction and some invalid HTML string" in {
    val htmlString = """<div id="closed-sell"></div>"""
    delcampeValidator.validateBids(jsoupBrowser.parseString(htmlString)) shouldBe
      Invalid(Chain(MissingBidsForClosedAuction))
  }
  it should "produce a RequestForBidsForOngoingAuction from an ongoing auction" in {
    val htmlString = """<div></div>"""
    delcampeValidator.validateBids(jsoupBrowser.parseString(htmlString)) shouldBe
      Invalid(Chain(RequestForBidsForOngoingAuction))
  }
  it should "produce a BidsContainerNotFound from a closed auction with missing bids" in {
    val htmlString =
      """<div id="closed-sell">
        | <div id="tab-bids">
        |   <div class="bids-container">
        |     <div class="bids">
        |       <div class="table-list-line">
        |       </div>
        |     </div>
        |   </div>
        | </div>
        |</div>""".stripMargin
    delcampeValidator.validateBids(jsoupBrowser.parseString(htmlString)) shouldBe
      Invalid(Chain(BidsContainerNotFound))
  }

  it should "extract the auction BID COUNT for a SOLD auction of BID type" in {
    delcampeValidator.validateBidCount(htmlDoc) shouldBe Valid(4)
  }
  it should "produce a BidsContainerNotFound from a closed auction with some invalid HTML string" in {
    val htmlString =
      """<div id="closed-sell">
        | <div id="tab-bids">
        |   <div class="bids-container">
        |     <div class="bids">
        |       <div class="table-list-line">
        |       </div>
        |     </div>
        |   </div>
        | </div>
        |</div>""".stripMargin
    delcampeValidator.validateBidCount(jsoupBrowser.parseString(htmlString)) shouldBe
      Invalid(Chain(BidsContainerNotFound))
  }
  it should "produce a RequestForBidCountForOngoingAuction from an ongoing auction with some valid HTML string" in {
    val htmlString = """<div></div>"""
    delcampeValidator.validateBidCount(jsoupBrowser.parseString(htmlString)) shouldBe
      Invalid(Chain(RequestForBidCountForOngoingAuction))
  }

  it should "produce BidderNicknameNotFound when the HTML document is missing the nickname node" in {
    val htmlString = "<div><ul><li><span></span></li></ul></div>"
    val el: Element = jsoupBrowser.parseString(htmlString) >> element("div")
    delcampeValidator.fetchBidTypeBidderNickname(el) shouldBe Invalid(Chain(BidderNicknameNotFound))
  }

  it should "produce false when the HTML document is missing the 'automatic' node" in {
    val htmlString = "<div><ul><li></li><li><span></span></li></ul></div>"
    val el: Element = jsoupBrowser.parseString(htmlString) >> element("div")
    delcampeValidator.fetchBidTypeIsAutomaticBid(el) shouldBe Valid(false)
  }

  it should "produce BidPriceNotFound when the HTML document is missing the BID PRICE node" in {
    val htmlString = "<div><ul><li></li><li><span></span></li></ul></div>"
    val el: Element = jsoupBrowser.parseString(htmlString) >> element("div")
    delcampeValidator.fetchBidTypeBidPrice(el) shouldBe Invalid(Chain(BidPriceNotFound))
  }

  it should "produce BidDateNotFound when the HTML document is missing the BID DATE node" in {
    val htmlString = "<div><ul><li></li><li></li></ul></div>"
    val el: Element = jsoupBrowser.parseString(htmlString) >> element("div")
    delcampeValidator.fetchBidTypeBidDate(el) shouldBe Invalid(Chain(BidDateNotFound))
  }
}
