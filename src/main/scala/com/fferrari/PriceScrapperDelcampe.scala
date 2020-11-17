package com.fferrari

import java.util.Date

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{Behavior, scaladsl}
import com.fferrari.DelcampeTools.{dateStringToDate, relativeToAbsoluteUrl}
import com.fferrari.PriceScrapperProtocol.{CreateAuction, PriceScrapperCommand, ScrapUrls}
import net.ruippeixotog.scalascraper.browser.{Browser, JsoupBrowser}
import net.ruippeixotog.scalascraper.dsl.DSL.Extract._
import net.ruippeixotog.scalascraper.dsl.DSL._
import net.ruippeixotog.scalascraper.model.Element

import scala.util.Try

object PriceScrapperDelcampe {
  lazy val jsoupBrowser: Browser = JsoupBrowser()

  case class SellerDetails(nickname: Option[String], location: Option[String], isProfessional: Option[Boolean])
  case class Bid(nickname: String, price: BigDecimal, currency: String, isAutomatic: Boolean, bidAt: Date)
  case class AuctionDetails(dateClosed: Option[Date], sellerDetails: Option[SellerDetails], bids: List[Bid])

  def apply(): Behavior[PriceScrapperCommand] = Behaviors.setup { context =>
    // TODO fetch from DB
    val urls = Array(
      // Add &size=480&page=1
      "https://www.delcampe.net/en_US/collectibles/search?term=&categories%5B%5D=3268&search_mode=all&order=sale_start_datetime&display_state=sold_items&duration_selection=all&seller_localisation_choice=world"
    )

    context.self ! ScrapUrls(urls)

    Behaviors.receivePartial {
      case (context, ScrapUrls(urls, currentUrlIdx)) =>
        if (urls.length == 0) {
          context.log.error(s"PriceScrapperDelcampe was given an empty list of urls, stopping")
          Behaviors.stopped
        }

        val urlToScrap = if (currentUrlIdx > urls.length) urls(0) else urls(currentUrlIdx)

        context.log.info(s"PriceScrapperDelcampe Scrapping url: $urlToScrap")
        context.log.info("PriceScrapperDelcampe Pausing for 5 secs")

        extractPrices(urlToScrap, context)

        Thread.sleep(10000)

        context.self ! ScrapUrls(urls, (currentUrlIdx + 1) % urls.length)
        Behaviors.same
    }
  }

  def extractPrices(url: String, context: scaladsl.ActorContext[PriceScrapperCommand], pageNumber: Int = 1): Unit = {
    // https://github.com/ruippeixotog/scala-scraper
    val itemsPerPage: Int = 480
    val pagedUrl: String = s"$url&size=$itemsPerPage&page=$pageNumber"
    val htmlDoc: jsoupBrowser.DocumentType = jsoupBrowser.get(pagedUrl)

    val itemListing: List[Element] = htmlDoc >> elementList(".item-listing .item-main-infos")

    if (itemListing.nonEmpty) {
      context.log.info(s"Parsing url $pagedUrl")
      val commands: List[CreateAuction] = for {
        item <- itemListing
        itemInfo = item >> element("div.item-info")
        itemSellingType = itemInfo >> element("div.selling-type")
        auctionUrl = relativeToAbsoluteUrl(url, itemInfo >> element("a") >> attr("href"))
        auctionTitle = itemInfo >> element("a") >> attr("title")
        // itemSellingTypeText can be icon-fixed-price OR icon-bid
        itemSellingTypeText = itemSellingType >> element("span.selling-type-icon svg") >> attr("class")
        sellingType = if (itemSellingTypeText == "icon-fixed-price") "fixed" else "bid" // TODO enum
        priceSold = itemInfo >> text("strong.item-price")
        maybeCurrencyAndPrice = DelcampeTools.parsePriceString(priceSold)
        bidCount = DelcampeTools.bidCountFromText(itemInfo >?> text("li.orange"))
        imageInfo = item >> element("div.image-container a.img-view")
        imageUrl = imageInfo >> attr("href")
        itemId = imageInfo >> attr("data-item-id")
        saleClosed = item >> text("span")
        auctionDetails = fetchAuctionDetails(auctionUrl)
        _ = Thread.sleep(1000)
        currency <- maybeCurrencyAndPrice.map(_._1)
        price <- maybeCurrencyAndPrice.map(_._2)
      } yield CreateAuction(itemId, auctionUrl, imageUrl, sellingType, auctionTitle, currency, price, bidCount)

      commands.foreach(command => context.log.info(s"$command"))

      // extractPrices(url, context, pageNumber + 1)
    }
  }

  def fetchAuctionDetails(url: String): AuctionDetails = {
    val htmlDoc: jsoupBrowser.DocumentType = jsoupBrowser.get(url)

    val htmlDateClosed: String = htmlDoc >> element("ul.bottom-infos-actions li") >> text("p")

    AuctionDetails(
      dateStringToDate(htmlDateClosed),
      extractSellerDetails(htmlDoc),
      extractBids(htmlDoc)
    )
  }

  def extractSellerDetails(htmlDoc: jsoupBrowser.DocumentType): Option[SellerDetails] = {
    (htmlDoc >> elementList("div#seller-info li"))
      .foldLeft(Option.empty[SellerDetails]) {
        case (acc, htmlSellerInfo) =>
          (htmlSellerInfo >> "strong" >> text).trim match {
            case "Location:" =>
              val htmlLocation: String = htmlSellerInfo >> text("div")
              acc.map(_.copy(location = Some(htmlLocation)))

            case "Seller:" =>
              val htmlNickname: String = htmlSellerInfo >> text("span.nickname")
              val isProfessional: Option[Boolean] = Option((htmlSellerInfo >?> element("i.icon-pro")).nonEmpty)
              acc.map(_.copy(nickname = Some(htmlNickname), isProfessional = isProfessional))
          }
      }
  }

  /**
   * Extracts the list of bids from an auction page
   * @param htmlDoc A jsoupBrowser instance
   * @return
   */
  def extractBids(htmlDoc: jsoupBrowser.DocumentType): List[Bid] = {
    val htmlBidsTable = htmlDoc >> elementList("div.bids-container ul.table-body-list")
    val htmlBidsWithDetails = htmlBidsTable.map(bid => (bid, bid >> elementList("li")))

    htmlBidsWithDetails.collect {
      case (bid, htmlTableColumns) if htmlTableColumns.length >= 3 =>
        val htmlNickname: Option[String] = bid >?> text("span.nickname")
        val htmlCurrencyAndPrice: Option[(String, BigDecimal)] = (htmlTableColumns(1) >?> text("strong")).flatMap(DelcampeTools.parsePriceString)
        val htmlBidDate: Option[Date] = (htmlTableColumns(2) >?> text).flatMap(DelcampeTools.shortDateStringToDate)
        val isAutomaticBid: Boolean = (htmlTableColumns(1) >?> text("span")).contains("automatic")

        (htmlNickname, htmlCurrencyAndPrice, htmlBidDate) match {
          case (Some(nickname), Some((currency, price)), Some(bidDate)) =>
            Bid(nickname, price, currency, isAutomaticBid, bidDate)
        }
    }
  }
}
