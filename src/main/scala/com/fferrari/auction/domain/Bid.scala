package com.fferrari.auction.domain

import java.time.Instant

import com.fferrari.model.Price

case class Bid(nickname: String,
               bidPrice: Price,
               quantity: Int,
               isAutomaticBid: Boolean,
               bidAt: Instant)
