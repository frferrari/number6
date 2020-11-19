package com.fferrari

import java.util.Date

import org.scalatest.OptionValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class DelcampeToolsTest extends AnyFlatSpec with Matchers with OptionValues {
  "A valid short date (PM time)" should "be converted properly" in {
    val result: Option[Date] = DelcampeTools.parseHtmlShortDate("Nov 15, 2020 at 7:17:06 PM")

    assert(result.isDefined)
    assert(result.value == new Date(1605464226000L))
  }

  "A valid short date (AM time)" should "be converted properly" in {
    val result: Option[Date] = DelcampeTools.parseHtmlShortDate("Nov 15, 2020 at 7:17:06 AM")

    assert(result.isDefined)
    assert(result.value == new Date(1605421026000L))
  }

  "An invalid short date" should "produce a None" in {
    val result: Option[Date] = DelcampeTools.parseHtmlShortDate("Noc 15, 2020 at 7:17:06 AM")

    assert(result.isEmpty)
  }

  "A valid date (PM time)" should "be converted properly" in {
    val result: Option[Date] = DelcampeTools.parseHtmlDate("Ended on<br>Sunday, November 15, 2020 at 7:32 PM")

    assert(result.isDefined)
    assert(result.value == new Date(1605465120000L))
  }

  "A valid date (AM time)" should "be converted properly" in {
    val result: Option[Date] = DelcampeTools.parseHtmlDate("Ended on<br>Saturday, November 7, 2020 at 7:32 AM")

    assert(result.isDefined)
    assert(result.value == new Date(1604730720000L))
  }

  "An invalid date" should "produce a None" in {
    val result: Option[Date] = DelcampeTools.parseHtmlDate("Ended on<br>Saturday, Nochember 7, 2020 at 7:32 AM")

    assert(result.isEmpty)
  }

  "A relative http URL" should "be converted as an absolute URL" in {
    val result = DelcampeTools.relativeToAbsoluteUrl("http://www.abracadabra.com/en", "list/items")
    assert(result == "http://www.abracadabra.com/list/items")
  }

  "An absolute URL" should "not be modified" in {
    val result = DelcampeTools.relativeToAbsoluteUrl("http://www.example.com/en", "http://www.bubblegum.com/list/items")
    assert(result == "http://www.bubblegum.com/list/items")
  }

  "The string '1 bid'" should "produce the value 1" in {
    assert(DelcampeTools.bidCountFromText(Some("1 bid")).contains(1))
  }

  "The string '3 bids'" should "produce the value 3" in {
    assert(DelcampeTools.bidCountFromText(Some("3 bids")).contains(3))
  }

  "An empty string" should "produce a None" in {
    assert(DelcampeTools.bidCountFromText(Some("")).isEmpty)
  }

  "An undefined value" should "produce a None" in {
    assert(DelcampeTools.bidCountFromText(None).isEmpty)
  }

  "A string containing a EURO currency and a price" should "produce be properly parsed" in {
    val result = DelcampeTools.parseHtmlPrice("€2.65")
    assert(result.contains(("EUR", BigDecimal(2.65))))
  }

  "A string containing a DOLLAR currency and a price" should "produce be properly parsed" in {
    val result = DelcampeTools.parseHtmlPrice("$1.80")

    assert(result.contains(("USD", BigDecimal(1.80))))
  }

  "A string containing the € sign" should "produce the EUR string" in {
    assert(DelcampeTools.normalizeCurrency("€") == "EUR")
  }

  "A string containing the $ sign" should "produce the USD string" in {
    assert(DelcampeTools.normalizeCurrency("$") == "USD")
  }

  "A string containing the £ sign" should "produce the GBP string" in {
    assert(DelcampeTools.normalizeCurrency("£") == "GBP")
  }

  "A string containing the CHF string" should "produce the CHF string" in {
    assert(DelcampeTools.normalizeCurrency("CHF") == "CHF")
  }

  "A seller information label of 'Location:'" should "produce the string LOCATION" in {
    assert(DelcampeTools.extractSellerInfoLabel("Location:") == "LOCATION")
  }

  "A string containing a purchase quantity of 1" should "be parsed as the value 1" in {
    assert(DelcampeTools.parseHtmlQuantity("1 item").contains(1))
  }

  "A string containing a purchase quantity of 2" should "be parsed as the value 2" in {
    assert(DelcampeTools.parseHtmlQuantity("2 items").contains(2))
  }
}
