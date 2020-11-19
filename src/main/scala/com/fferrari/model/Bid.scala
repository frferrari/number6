package com.fferrari.model

import java.util.Date

case class Bid(nickname: String, bidPrice: Price, quantity: Int, isAutomaticBid: Boolean, bidAt: Date)