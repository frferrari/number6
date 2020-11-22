package com.fferrari.scrapper

sealed trait DomainValidation {
  def errorMessage: String
}

case object IdNotFound extends DomainValidation {
  override def errorMessage: String = "The auction id could not be fetched"
}

case object TitleNotFound extends DomainValidation {
  override def errorMessage: String = "The auction title could not be fetched"
}

case object SellerNicknameNotFound extends DomainValidation {
  override def errorMessage: String = "The seller nickname could not be fetched"
}

case object SellerLocationNotFound extends DomainValidation {
  override def errorMessage: String = "The seller location could not be fetched"
}

case object AuctionTypeNotFound extends DomainValidation {
  override def errorMessage: String = "The auction type could not be fetched"
}

case object IsSoldFlagNotFound extends DomainValidation {
  override def errorMessage: String = "The auction sold flag could not be fetched"
}

case object StartPriceNotFound extends DomainValidation {
  override def errorMessage: String = "The start price could not be fetched"
}

case object FinalPriceNotFound extends DomainValidation {
  override def errorMessage: String = "The final price could not be fetched"
}

case object StartDateNotFound extends DomainValidation {
  override def errorMessage: String = "The start date could not be fetched"
}

case object EndDateNotFound extends DomainValidation {
  override def errorMessage: String = "The end date could not be fetched"
}

case object BidsNotFound extends DomainValidation {
  override def errorMessage: String = "The list of bids could not be fetched"
}

case object BidsContainerNotFound extends DomainValidation {
  override def errorMessage: String = "The html bids container could not be fetched"
}

case object MissingBidsForClosedAuction extends DomainValidation {
  override def errorMessage: String = "Could not fetch bids on a closed auction"
}

case object RequestForBidsForOngoingAuction extends DomainValidation {
  override def errorMessage: String = "Invalid request to fetch bids on an ongoing auction"
}

case object BidderNicknameNotFound extends DomainValidation {
  override def errorMessage: String = "The bidder nickname could not be fetched"
}

case object BidderIsAutomaticFlagNotFound extends DomainValidation {
  override def errorMessage: String = "The bidder automatic bid flag could not be fetched"
}

case object BidPriceNotFound extends DomainValidation {
  override def errorMessage: String = "The bid price could not be fetched"
}

case object BidDateNotFound extends DomainValidation {
  override def errorMessage: String = "The bid date could not be fetched"
}
case object InvalidBidQuantity extends DomainValidation {
  override def errorMessage: String = "The bid quantity could not be fetched"
}

case object InvalidDateFormat extends DomainValidation {
  override def errorMessage: String = "The date format is invalid"
}

case object InvalidShortDateFormat extends DomainValidation {
  override def errorMessage: String = "The short date format is invalid"
}

case object InvalidPriceFormat extends DomainValidation {
  override def errorMessage: String = "The price format is invalid"
}

case object LargeImageUrlNotFound extends DomainValidation {
  override def errorMessage: String = "The large image url cound not be fetched"
}
