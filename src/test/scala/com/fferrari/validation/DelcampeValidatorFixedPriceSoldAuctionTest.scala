package com.fferrari.validation

import java.time.LocalDateTime

import cats.data.Chain
import cats.data.Validated.{Invalid, Valid}
import com.fferrari.model.{Auction, Bid, Price}
import net.ruippeixotog.scalascraper.browser.JsoupBrowser
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class DelcampeValidatorFixedPriceSoldAuctionTest extends AnyFlatSpec with Matchers with DelcampeValidatorTestFixtures {

  val delcampeValidator: DelcampeValidator = new DelcampeValidator

  implicit val jsoupBrowser: JsoupBrowser = JsoupBrowser.typed()
  val htmlDoc: JsoupBrowser.JsoupDocument = jsoupBrowser.get(FIXED_PRICE_TYPE_SOLD_URL)

  it should "extract the auction ID from a SOLD auction of FIXED PRICE type" in {
    delcampeValidator.validateExternalId(htmlDoc) shouldBe Valid("621701026")
  }

  it should "extract the auction TITLE from a SOLD auction of FIXED PRICE type" in {
    delcampeValidator.validateTitle(htmlDoc) shouldBe Valid("Francia, 2002 - 46c Neufchateau - nr.2887 usato°")
  }

  it should "extract the auction SELLER NICKNAME from a SOLD auction of FIXED PRICE type" in {
    delcampeValidator.validateSellerNickname(htmlDoc) shouldBe Valid("bonnystamp")
  }

  it should "extract the auction SELLER LOCATION from a SOLD auction of FIXED PRICE type" in {
    delcampeValidator.validateSellerLocation(htmlDoc) shouldBe Valid("Italy")
  }

  it should "extract the auction TYPE from a SOLD auction of FIXED PRICE type" in {
    delcampeValidator.validateAuctionType(htmlDoc) shouldBe Valid(Auction.FIXED_PRICE_TYPE_AUCTION)
  }

  it should "extract the auction SOLD FLAG from a SOLD auction of FIXED PRICE type" in {
    delcampeValidator.validateIsSold(htmlDoc) shouldBe Valid(true)
  }

  it should "extract the auction START PRICE from a SOLD auction of FIXED PRICE type" in {
    delcampeValidator.validateStartPrice(htmlDoc) shouldBe Valid(Price(0.20, "EUR"))
  }
  it should "produce StartPriceNotFound from an ONGOING auction of type FIXED PRICE missing the START PRICE node" in {
    val htmlString = """<div><div id="bid-box"></div></div>"""
    delcampeValidator.validateStartPrice(jsoupBrowser.parseString(htmlString)) shouldBe
      Invalid(Chain(StartPriceNotFound))
  }
  it should "produce StartPriceNotFound for a SOLD auction missing the START PRICE node" in {
    val htmlString = """<div id="closed-sell"><div id="tab-sales"></div></div>"""
    delcampeValidator.validateStartPrice(jsoupBrowser.parseString(htmlString)) shouldBe
      Invalid(Chain(StartPriceNotFound))
  }
  it should "produce StartPriceNotFound for an ONGOING auction missing the START PRICE node" in {
    val htmlString = """<div id="buy-box"></div>"""
    delcampeValidator.validateStartPrice(jsoupBrowser.parseString(htmlString)) shouldBe
      Invalid(Chain(StartPriceNotFound))
  }
  it should "extract the price for an ONGOING auction" in {
    val htmlString = """<div id="buy-box"><span class="price">€2.80</span></div>"""
    delcampeValidator.validateStartPrice(jsoupBrowser.parseString(htmlString)) shouldBe
      Valid(Price(2.80, "EUR"))
  }

  it should "extract the auction FINAL PRICE from a SOLD auction of FIXED PRICE type" in {
    delcampeValidator.validateFinalPrice(htmlDoc) shouldBe Valid(Some(Price(0.20, "EUR")))
  }

  it should "extract the auction START DATE from a SOLD auction of FIXED PRICE type" in {
    delcampeValidator.validateStartDate(htmlDoc) shouldBe
      Valid(LocalDateTime.of(2018, 8, 21, 15, 41, 0))
  }

  it should "extract the auction END DATE from a SOLD auction of FIXED PRICE type" in {
    delcampeValidator.validateEndDate(htmlDoc) shouldBe
      Valid(Some(LocalDateTime.of(2020, 11, 17, 21, 24)))
  }

  it should "extract the auction LARGE IMAGE URL from a SOLD auction of FIXED PRICE type" in {
    delcampeValidator.validateLargeImageUrl(htmlDoc) shouldBe
      Valid("https://delcampe-static.net/img_large/auction/000/621/701/026_001.jpg?v=1")
  }

  it should "extract the auction BIDS from a SOLD auction of FIXED PRICE type" in {
    delcampeValidator.validateBids(htmlDoc) shouldBe
      Valid(
        List(
          Bid("private", Price(0.20, "EUR"), 1, false, LocalDateTime.of(2020, 11, 17, 21, 24, 24))
        )
      )
  }
  it should "produce (BidderNicknameNotFound, BidPriceNotFound, InvalidBidQuantityFormat, InvalidShortDateFormat) from a closed auction with missing values" in {
    val htmlString =
      """<div id="closed-sell">
        | <div id="tab-sales">
        |   <div class="table-view">
        |     <div class="table-body">
        |       <ul class="table-body-list">
        |       </ul>
        |     </div>
        |   </div>
        | </div>
        |</div>""".stripMargin
    delcampeValidator.validateBids(jsoupBrowser.parseString(htmlString)) shouldBe
      Invalid(Chain(BidderNicknameNotFound, BidPriceNotFound, InvalidBidQuantityFormat, InvalidShortDateFormat))
  }
  it should "produce BidsContainerNotFound from a closed auction with some invalid HTML string" in {
    val htmlString =
      """<div id="closed-sell">
        | <div id="tab-sales">
        |   <div class="table-view">
        |     <div class="table-body">
        |     </div>
        |   </div>
        | </div>
        |</div>""".stripMargin
    delcampeValidator.validateBids(jsoupBrowser.parseString(htmlString)) shouldBe
      Invalid(Chain(BidsContainerNotFound))
  }

  it should "extract the auction BID COUNT from a SOLD auction of FIXED PRICE type" in {
    delcampeValidator.validateBidCount(htmlDoc) shouldBe Valid(1)
  }
}
