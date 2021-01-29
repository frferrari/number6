package com.fferrari.pricescraper.auction.domain

import java.time.Instant

case class Bid(nickname: String,
               bidPrice: Price,
               quantity: Int,
               isAutomaticBid: Boolean,
               bidAt: Instant)
