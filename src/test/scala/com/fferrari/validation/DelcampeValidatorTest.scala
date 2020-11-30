package com.fferrari.validation

import java.time.LocalDateTime

import cats.data.Chain
import cats.data.Validated.{Invalid, Valid}
import com.fferrari.actor.AuctionScrapperProtocol.WebsiteInfo
import com.fferrari.model.{Bid, BidType, Delcampe, Price}
import net.ruippeixotog.scalascraper.browser.JsoupBrowser
import net.ruippeixotog.scalascraper.browser.JsoupBrowser.JsoupDocument
import net.ruippeixotog.scalascraper.dsl.DSL.Extract._
import net.ruippeixotog.scalascraper.dsl.DSL._
import net.ruippeixotog.scalascraper.model.Element
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.util.Try

class DelcampeValidatorTest extends AnyFlatSpec with Matchers with DelcampeValidatorTestFixtures {

  val delcampeValidator: DelcampeValidator = new DelcampeValidator

  implicit val jsoupBrowser: JsoupBrowser = JsoupBrowser.typed()
  val htmlDoc: JsoupBrowser.JsoupDocument = jsoupBrowser.get(BID_TYPE_SOLD_URL)

  it should "produce a list of all the auction links when websiteInfo.lastScrappedUrl is empty" in {
    val htmlString =
      """
        |<div class="items main">
        | <div class="item-listing">
        |   <div class="item-main-infos">
        |     <div class="item-info">
        |       <a class="item-link" href="/auction1"></a>
        |       <a class="item-link" href="/auction2"></a>
        |       <a class="item-link" href="/auction3"></a>
        |       <a class="item-link" href="/auction4"></a>
        |     </div>
        |   </div>
        | </div>
        |</div>""".stripMargin
        val websiteInfo = WebsiteInfo(Delcampe, "http://www.example.com", None)
    delcampeValidator.validateAuctionUrls(websiteInfo)(jsoupBrowser.parseString(htmlString)) shouldBe
      Valid(
        List(
          "http://www.example.com/auction1",
          "http://www.example.com/auction2",
          "http://www.example.com/auction3",
          "http://www.example.com/auction4"
        )
      )
  }
  it should "produce a list of the auction links appearing until the websiteInfo.lastScrappedUrl is found" in {
    val htmlString =
      """
        |<div class="items main">
        | <div class="item-listing">
        |   <div class="item-main-infos">
        |     <div class="item-info">
        |       <a class="item-link" href="/auction1"></a>
        |       <a class="item-link" href="/auction2"></a>
        |       <a class="item-link" href="/auction3"></a>
        |       <a class="item-link" href="/auction4"></a>
        |     </div>
        |   </div>
        | </div>
        |</div>""".stripMargin
    val websiteInfo = WebsiteInfo(Delcampe, "http://www.example.com", Some("http://www.example.com/auction3"))
    delcampeValidator.validateAuctionUrls(websiteInfo)(jsoupBrowser.parseString(htmlString)) shouldBe
      Valid(
        List(
          "http://www.example.com/auction1",
          "http://www.example.com/auction2"
        )
      )
  }
  it should "produce a AuctionLinkNotFound from some invalid HTML string" in {
    val htmlString =
      """<div class="items main">
        | <div class="item-listing">
        |   <div class="item-main-infos">
        |     <div class="item-info">
        |       <a class="item-link" href="/auction1"></a>
        |       <a class="item-link"></a>
        |      </div>
        |   </div>
        | </div>
        |</div>""".stripMargin
    val websiteInfo = WebsiteInfo(Delcampe, "http://www.example.com", None)
    delcampeValidator.validateAuctionUrls(websiteInfo)(jsoupBrowser.parseString(htmlString)) shouldBe
      Invalid(Chain(AuctionLinkNotFound))
  }

  it should "produce the listing page as an HTML document" in {
    val htmlString =
      """<div class="items main">
        | <div class="item-listing">
        |   <div></div>
        |   <div></div>
        | </div>
        |</div>""".stripMargin
    val expectedDocument = jsoupBrowser.parseString(htmlString)
    def getPage(url: String): Try[JsoupDocument] = Try(expectedDocument)
    delcampeValidator.validateListingPage(WebsiteInfo(Delcampe, "http://www.example.com", None), getPage, 20, 1) shouldBe
      Valid(expectedDocument)
  }
  it should "produce MaximumNumberOfAllowedPagesReached when the allowed limit of pages to read is reached" in {
    val htmlString = """<div class="items main"><div><h2>You have reached the limit of results to display</h2></div></div>"""
    def getPage(url: String): Try[JsoupDocument] = Try(jsoupBrowser.parseString(htmlString))
    delcampeValidator.validateListingPage(WebsiteInfo(Delcampe, "http://www.example.com", None), getPage, 20, 1) shouldBe
      Invalid(Chain(MaximumNumberOfAllowedPagesReached))
  }
  it should "produce LastListingPageReached when the last listing page is reached" in {
    val htmlString = """<div class="items main"><div><h2></h2></div></div>"""
    def getPage(url: String): Try[JsoupDocument] = Try(jsoupBrowser.parseString(htmlString))
    delcampeValidator.validateListingPage(WebsiteInfo(Delcampe, "http://www.example.com", None), getPage, 20, 1) shouldBe
      Invalid(Chain(LastListingPageReached))
  }

  it should "extract the auction TYPE from a SOLD auction of BID type" in {
    delcampeValidator.validateAuctionType(htmlDoc) shouldBe Valid(BidType)
  }
  it should "produce AuctionTypeNotFound from some invalid HTML string" in {
    val htmlString = """<div class="price-info"><div><i></i></div></div>"""
    delcampeValidator.validateAuctionType(jsoupBrowser.parseString(htmlString)) shouldBe
      Invalid(Chain(AuctionTypeNotFound))
  }
  it should "produce AuctionTypeNotFound when the AUCTION TYPE class attribute is invalid" in {
    val htmlString = """<div class="price-info"><div><i class="dummy"></i></div></div>"""
    delcampeValidator.validateAuctionType(jsoupBrowser.parseString(htmlString)) shouldBe
      Invalid(Chain(AuctionTypeNotFound))
  }

  it should "extract the auction SOLD FLAG from a SOLD auction of BID type" in {
    delcampeValidator.validateIsSold(htmlDoc) shouldBe Valid(true)
  }
  it should "produce false from some invalid HTML string" in {
    val htmlString = """<div id="closed-sell"><div>"""
    delcampeValidator.validateIsSold(jsoupBrowser.parseString(htmlString)) shouldBe
      Valid(false)
  }
}
