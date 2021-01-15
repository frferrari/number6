package com.fferrari.auction.domain

import java.time.LocalDateTime

case class Bid(nickname: String,
               bidPrice: Price,
               quantity: Int,
               isAutomaticBid: Boolean,
               bidAt: LocalDateTime)