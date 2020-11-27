package com.fferrari.validation

import java.time.LocalDateTime

import cats.data.Validated.Valid
import com.fferrari.model.{Bid, BidType, FixedPriceType, Price}
import net.ruippeixotog.scalascraper.browser.JsoupBrowser
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class DelcampeValidatorFixedPriceSoldCreateAuctionTest extends AnyFlatSpec with Matchers with DelcampeValidatorTestFixtures {

  val delcampeValidator: DelcampeValidator = new DelcampeValidator

  implicit val jsoupBrowser: JsoupBrowser = JsoupBrowser.typed()
  val htmlDoc: JsoupBrowser.JsoupDocument = jsoupBrowser.get(FIXED_PRICE_TYPE_SOLD_URL)

  it should "extract the auction ID from a SOLD auction of FIXED PRICE type" in {
    delcampeValidator.validateId(htmlDoc) shouldBe Valid("621701026")
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
    delcampeValidator.validateAuctionType(htmlDoc) shouldBe Valid(FixedPriceType)
  }
  it should "extract the auction SOLD FLAG from a SOLD auction of FIXED PRICE type" in {
    delcampeValidator.validateIsSold(htmlDoc) shouldBe Valid(true)
  }
  it should "extract the auction START PRICE from a SOLD auction of FIXED PRICE type" in {
    delcampeValidator.validateStartPrice(htmlDoc) shouldBe Valid(Price(0.20, "EUR"))
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
  it should "extract the auction BID COUNT from a SOLD auction of FIXED PRICE type" in {
    delcampeValidator.validateBidCount(htmlDoc) shouldBe Valid(1)
  }
}
