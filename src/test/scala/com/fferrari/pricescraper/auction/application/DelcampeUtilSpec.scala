package com.fferrari.pricescraper.auction.application

import java.time.{LocalDateTime, ZoneOffset}

import cats.data.Chain
import cats.data.Validated.{Invalid, Valid}
import com.fferrari.pricescraper.auction.domain.Price
import org.scalatest.OptionValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class DelcampeUtilSpec
  extends AnyFlatSpec
    with Matchers
    with OptionValues {
  it should "extract the correct date from a valid short date (PM time)" in {
    val result = DelcampeUtil.parseHtmlShortDate("Nov 15, 2020 at 7:17:06 PM")
    assert(result == Valid(LocalDateTime.of(2020, 11, 15, 19, 17, 6).toInstant(ZoneOffset.UTC)))
  }

  it should "extract the correct date from a valid short date (AM time)" in {
    val result = DelcampeUtil.parseHtmlShortDate("Nov 15, 2020 at 7:17:06 AM")
    assert(result == Valid(LocalDateTime.of(2020, 11, 15, 7, 17, 6).toInstant(ZoneOffset.UTC)))
  }

  it should "produce an InvalidShortDateFormat" in {
    val result = DelcampeUtil.parseHtmlShortDate("Noc 15, 2020 at 7:17:06 AM")
    assert(result == Invalid(Chain(InvalidShortDateFormat)))
  }

  it should "extract the correct date from a valid date (PM time)" in {
    val result = DelcampeUtil.parseHtmlDate("Ended on<br>Sunday, November 15, 2020 at 7:32 PM")
    assert(result == Valid(LocalDateTime.of(2020, 11, 15, 19, 32, 0).toInstant(ZoneOffset.UTC)))
  }

  it should "extract the correct date from a valid date (AM time)" in {
    val result = DelcampeUtil.parseHtmlDate("Ended on<br>Saturday, November 7, 2020 at 7:32 AM")
    assert(result == Valid(LocalDateTime.of(2020, 11, 7, 7, 32, 0).toInstant(ZoneOffset.UTC)))
  }

  it should "produce an InvalidDateFormat" in {
    val result = DelcampeUtil.parseHtmlDate("Ended on<br>Saturday, Nochember 7, 2020 at 7:32 AM")
    assert(result == Invalid(Chain(InvalidDateFormat)))
  }

  it should "produce an absolute URL from a relative http URL" in {
    val result = DelcampeUtil.relativeToAbsoluteUrl("http://www.abracadabra.com/en", "list/items")
    assert(result == "http://www.abracadabra.com/list/items")
  }

  it should "keep an absolute URL unmodified" in {
    val result = DelcampeUtil.relativeToAbsoluteUrl("http://www.example.com/en", "http://www.bubblegum.com/list/items")
    assert(result == "http://www.bubblegum.com/list/items")
  }

  it should "produce an absolute URL from a relative URL having the absolute URL finishing with /" in {
    val result = DelcampeUtil.relativeToAbsoluteUrl("http://www.example.com/en/", "list/items")
    assert(result == "http://www.example.com/list/items")
  }

  it should "produce an absolute URL from a relative URL having the relative URL starting with /" in {
    val result = DelcampeUtil.relativeToAbsoluteUrl("http://www.example.com", "/list/items")
    assert(result == "http://www.example.com/list/items")
  }

  it should "produce an absolute URL from a relative URL having the absolute URL finishing with / and the relative URL starting with /" in {
    val result = DelcampeUtil.relativeToAbsoluteUrl("http://www.example.com/en/", "/list/items")
    assert(result == "http://www.example.com/list/items")
  }

  it should "extract the value 1 from the string '1 bid'" in {
    assert(DelcampeUtil.bidCountFromText(Some("1 bid")).contains(1))
  }

  it should "extract the value 3 from the string '3 bids'" in {
    assert(DelcampeUtil.bidCountFromText(Some("3 bids")).contains(3))
  }

  it should "produce a None from an empty bid count string" in {
    assert(DelcampeUtil.bidCountFromText(Some("")).isEmpty)
  }

  it should "produce a None from an undefined bid count string" in {
    assert(DelcampeUtil.bidCountFromText(None).isEmpty)
  }

  it should "extract the correct price from a string containing a EURO currency and a price" in {
    val result = DelcampeUtil.parseHtmlPrice("€2.65")
    assert(result == Valid(Price(2.65, "EUR")))
  }

  it should "extract the correct price from a string containing a DOLLAR currency and a price" in {
    val result = DelcampeUtil.parseHtmlPrice("$1.80")
    assert(result == Valid(Price(1.8, "USD")))
  }

  it should "produce an InvalidPriceFormat from an invalid price string" in {
    val result = DelcampeUtil.parseHtmlPrice("1.80")
    assert(result == Invalid(Chain(InvalidPriceFormat)))
  }

  it should "produce EUR from a string containing the € sign" in {
    assert(DelcampeUtil.normalizeCurrency("€") == "EUR")
  }

  it should "produce USD from a string containing the $ sign" in {
    assert(DelcampeUtil.normalizeCurrency("$") == "USD")
  }

  it should "produce GBP from a string containing the £ sign" in {
    assert(DelcampeUtil.normalizeCurrency("£") == "GBP")
  }

  it should "produce CHF from a string containing the CHF string" in {
    assert(DelcampeUtil.normalizeCurrency("CHF") == "CHF")
  }

  it should "produce LOCATION from the string 'Location:'" in {
    assert(DelcampeUtil.extractSellerInfoLabel("Location:") == "LOCATION")
  }

  it should "extract the value 1 from the string '1 item'" in {
    assert(DelcampeUtil.parseHtmlQuantity("1 item") == Valid(1))
  }

  it should "extract the value 2 from the string '2 item'" in {
    assert(DelcampeUtil.parseHtmlQuantity("2 items") == Valid(2))
  }

  it should "produce an InvalidBidQuantityFormat from the string 'items'" in {
    assert(DelcampeUtil.parseHtmlQuantity("items") == Invalid(Chain(InvalidBidQuantityFormat)))
  }

  it should "produce an InvalidBidQuantityFormat from an empty string" in {
    assert(DelcampeUtil.parseHtmlQuantity("items") == Invalid(Chain(InvalidBidQuantityFormat)))
  }
}
