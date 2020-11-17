package com.fferrari

import java.text.SimpleDateFormat
import java.util.Date

import scala.util.Try

object DelcampeTools {
  /**
   * Parses a string containing a currency and a price and extracts this 2 components as a tuple
   * @param priceString A string containing a currency and a price of the following form: €2.75
   */
  def parsePriceString(priceString: String): Option[(String, BigDecimal)] = {
    val priceRegex = "([^0-9\\.]+)([0-9\\.]+)".r

    Try {
      val priceRegex(currency, price) = priceString
      (normalizeCurrency((currency)), BigDecimal(price))
    }.toOption
  }

  /**
   * Normalizes the given currency to a list of known and handled currencies
   * @param currency A string containing currencies like €, $, £, CHF, SEK, ...
   * @return
   */
  def normalizeCurrency(currency: String): String = currency match {
    case "€" => "EUR"
    case "$" => "USD"
    case "£" => "GBP"
    case _ => currency.toUpperCase
  }

  /**
   * Converts the given string date to a java.util.Date
   * @param dateString A Date of the follawing form "Ended on<br>Sunday, November 15, 2020 at 7:32 PM."
   * @return
   */
  def dateStringToDate(dateString: String): Option[Date] = {
    // dateClosed is of the following form
    // "Ended on<br>Sunday, November 15, 2020 at 7:32 PM."

    Try {
      val curatedDate: String =
        dateString
          .replace("Ended on", "")
          .replace("<br>", "")
          .replace(" at ", " ")
          .replace(".", "")
          .replace(",", "")

      val dateFormat = new SimpleDateFormat("E MMM d yyyy hh:mm aaa")
      dateFormat.parse(curatedDate)
    }.toOption
  }

  /**
   * Converts the given string date to a java.util.Date
   * @param dateString A Date of the follawing form "Nov 15, 2020 at 7:17:06 PM"
   * @return
   */
  def shortDateStringToDate(dateString: String): Option[Date] = {
    // dateClosed is of the following form
    // "Nov 15, 2020 at 7:17:06 PM"

    Try {
      val curatedDate: String =
        dateString
          .replace(" at ", " ")
          .replace(",", "")

      val dateFormat = new SimpleDateFormat("MMM d yyyy hh:mm:ss aaa")
      dateFormat.parse(curatedDate)
    }.toOption
  }

  /**
   * Converts a relative URL to an absolute URL
   * The parent URL contains the root path to the website for the relativeUrl
   * @param parentUrl An url containing the root path (ex: http://www.example.com/en)
   * @param relativeUrl A relative url (ex: list/items.html)
   * @return
   */
  def relativeToAbsoluteUrl(parentUrl: String, relativeUrl: String): String = {
    relativeUrl startsWith "http" match {
      case true =>
        relativeUrl
      case false =>
        val baseUrlRegex = "(http[s]*://[^/]+).*".r
        val baseUrlRegex(baseUrl) = parentUrl
        s"$baseUrl/$relativeUrl"
    }
  }

  /**
   * Extract the count of bids from a string of the following form: "1 bid" or "3 bids"
   * @param htmlBid A string containing the count of bids
   * @return
   */
  def bidCountFromText(htmlBid: Option[String]): Option[Int] = {
    val bidCountRegex = "([0-9]+) bid.*".r

    Try {
      htmlBid
        .map { bc =>
          val bidCountRegex(bidCount) = bc.trim
          bidCount
        }
        .map(_.toInt)
    }.toOption.flatten
  }
}
