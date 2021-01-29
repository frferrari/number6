package com.fferrari.pricescraper.auction.application

import java.time.{Instant, LocalDateTime, ZoneOffset}

import cats.data.ValidatedNec
import cats.implicits.catsSyntaxValidatedIdBinCompat0
import com.fferrari.pricescraper.auction.domain.Price

import scala.concurrent.duration.{FiniteDuration, _}
import scala.util.matching.Regex
import scala.util.{Failure, Random, Success, Try}

object DelcampeUtil {
  val rnd: Random.type = scala.util.Random

  import java.time.format.DateTimeFormatter

  val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE MMMM d y h:m a")
  val shortDateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d y h:m:s a")
  val priceRegex: Regex = "([^0-9\\.]+)([0-9\\.]+)".r

  /**
   * Parses a string containing a currency and a price and extracts this 2 components as a tuple
   * @param htmlPrice A string containing a currency and a price of the following form: €2.75
   * @return
   */
  def parseHtmlPrice(htmlPrice: String): ValidatedNec[AuctionDomainValidation, Price] = {
    def parsePrice(htmlPrice: String): Price = {
      val priceRegex(currency, price) = htmlPrice
      Price(BigDecimal(price), normalizeCurrency(currency))
    }

    Try(parsePrice(htmlPrice)) match {
      case Success(price) => price.validNec
      case Failure(f) => InvalidPriceFormat.invalidNec
    }
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
   * @param htmlDate A Date of the follawing form "Ended on<br>Sunday, November 15, 2020 at 7:32 PM."
   * @return
   */
  def parseHtmlDate(htmlDate: String): ValidatedNec[InvalidDateFormat.type, Instant] = {
    // htmlDate is expected to be of the following form
    // "Ended on<br>Sunday, November 15, 2020 at 7:32 PM."

    val curatedDate: String = htmlDate
      .replace("Ended on", "")
      .replace("<br>", "")
      .replace(" at ", " ")
      .replace(".", "")
      .replace(",", "")

    Try(LocalDateTime.parse(curatedDate, dateFormat).toInstant(ZoneOffset.UTC))
      .map(_.validNec)
      .getOrElse(InvalidDateFormat.invalidNec)
  }

  /**
   * Converts the given string date to a java.util.Date
   * @param htmlShortDate A Date of the following form "Nov 15, 2020 at 7:17:06 PM"
   * @return
   */
  def parseHtmlShortDate(htmlShortDate: String): ValidatedNec[InvalidShortDateFormat.type, Instant] = {
    // htmlDate is expected to be of the following form
    // "Nov 15, 2020 at 7:17:06 PM"

    val curatedDate: String =
      htmlShortDate
        .replace(" at ", " ")
        .replace(",", "")

    Try(LocalDateTime.parse(curatedDate, shortDateFormat).toInstant(ZoneOffset.UTC))
      .map(_.validNec)
      .getOrElse(InvalidShortDateFormat.invalidNec)
  }

  /**
   * Extracts a quantity of items purchased from a given string
   * @param htmlQuantity The quantity of items purchased expressed like "1 item"
   * @return
   */
  def parseHtmlQuantity(htmlQuantity: String): ValidatedNec[AuctionDomainValidation, Int] = {
    val quantityRegex = "([0-9]+) item.*".r

    Try {
      val quantityRegex(quantity) = htmlQuantity
      quantity.toInt
    }.map(_.validNec).getOrElse(InvalidBidQuantityFormat.invalidNec)
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
        if (baseUrl.endsWith("/") || relativeUrl.startsWith("/")) {
          s"$baseUrl$relativeUrl"
        } else {
          s"$baseUrl/$relativeUrl"
        }
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
        .map {
          bc =>
            val bidCountRegex(bidCount) = bc.trim
            bidCount
        }
        .map(_.toInt)
    }.toOption.flatten
  }

  /**
   * Produces a random milliseconds duration between the given minDuration and minDuration+5000
   * @param minDurationMs The minimum duration in milliseconds to be returned
   * @return
   */
  def randomDurationMs(minDurationMs: Int = 100): FiniteDuration = (minDurationMs + rnd.nextInt(500)).milliseconds

  /**
   * Extracts the label associated to a seller information (Location of the seller, Nickname of the seller, ...)
   * @param htmlText The label as available in the html doc, ex: Location: or Seller:
   * @return The label is returned as uppercase with alphabetical characters only, ex: LOCATION or SELLER
   */
  def extractSellerInfoLabel(htmlText: String): String = {
    val labelRegex = "([A-Z]+).*".r

    val labelRegex(label) = htmlText.trim.toUpperCase
    label
  }
}
