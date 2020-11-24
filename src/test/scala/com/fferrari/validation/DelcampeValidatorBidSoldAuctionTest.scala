package com.fferrari.validation

import java.time.LocalDateTime

import cats.data.Validated.Valid
import com.fferrari.model.{Bid, BidType, Price}
import net.ruippeixotog.scalascraper.browser.JsoupBrowser
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class DelcampeValidatorBidSoldAuctionTest extends AnyFlatSpec with Matchers with DelcampeValidatorTestFixtures {

  val delcampeValidator: DelcampeValidator = new DelcampeValidator

  implicit val jsoupBrowser: JsoupBrowser = JsoupBrowser.typed()
  val htmlDoc: JsoupBrowser.JsoupDocument = jsoupBrowser.get(BID_TYPE_SOLD_URL)

  it should "extract the auction ID from a SOLD auction of BID type" in {
    delcampeValidator.validateId(htmlDoc) shouldBe Valid("1120841389")
  }
  it should "extract the auction TITLE from a SOLD auction of BID type" in {
    delcampeValidator.validateTitle(htmlDoc) shouldBe Valid("TIMBRES DE FRANCE N° 3269 A 3278 OBLITÉRÉS ( LOT:4933 )")
  }
  it should "extract the auction SELLER NICKNAME from a SOLD auction of BID type" in {
    delcampeValidator.validateSellerNickname(htmlDoc) shouldBe Valid("lulu321")
  }
  it should "extract the auction SELLER LOCATION from a SOLD auction of BID type" in {
    delcampeValidator.validateSellerLocation(htmlDoc) shouldBe Valid("France")
  }
  it should "extract the auction TYPE from a SOLD auction of BID type" in {
    delcampeValidator.validateAuctionType(htmlDoc) shouldBe Valid(BidType)
  }
  it should "extract the auction SOLD FLAG from a SOLD auction of BID type" in {
    delcampeValidator.validateIsSold(htmlDoc) shouldBe Valid(true)
  }
  it should "extract the auction START PRICE from a SOLD auction of BID type" in {
    delcampeValidator.validateStartPrice(htmlDoc) shouldBe Valid(Price(2.0, "EUR"))
  }
  it should "extract the auction FINAL PRICE from a SOLD auction of BID type" in {
    delcampeValidator.validateFinalPrice(htmlDoc) shouldBe Valid(Some(Price(2.30, "EUR")))
  }
  it should "extract the auction START DATE from a SOLD auction of BID type" in {
    delcampeValidator.validateStartDate(htmlDoc) shouldBe
      Valid(LocalDateTime.of(2020, 11, 3, 5, 53, 0))
  }
  it should "extract the auction END DATE from a SOLD auction of BID type" in {
    delcampeValidator.validateEndDate(htmlDoc) shouldBe
      Valid(Some(LocalDateTime.of(2020, 11, 10, 19, 00)))
  }
  it should "extract the auction LARGE IMAGE URL from a SOLD auction of BID type" in {
    delcampeValidator.validateLargeImageUrl(htmlDoc) shouldBe
      Valid("https://delcampe-static.net/img_large/auction/001/120/841/389_001.jpg?v=1")
  }
  it should "extract the auction BIDS from a SOLD auction of BID type" in {
    delcampeValidator.validateBids(htmlDoc) shouldBe
      Valid(
        List(
          Bid("chantal27", Price(2.30, "EUR"), 1, false, LocalDateTime.of(2020, 11, 10, 18, 51, 41)),
          Bid("bercat51", Price(2.20, "EUR"), 1, false, LocalDateTime.of(2020, 11, 10, 13, 39, 43)),
          Bid("basket2505", Price(2.10, "EUR"), 1, false, LocalDateTime.of(2020, 11, 10, 10, 56, 30)),
          Bid("bercat51", Price(2.00, "EUR"), 1, false, LocalDateTime.of(2020, 11, 4, 18, 20, 19))
        )
      )
  }
  it should "extract the auction BID COUNT from a SOLD auction of BID type" in {
    delcampeValidator.validateBidCount(htmlDoc) shouldBe Valid(4)
  }
}
