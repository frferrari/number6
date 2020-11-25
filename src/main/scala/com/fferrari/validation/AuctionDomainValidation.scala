package com.fferrari.validation

sealed trait AuctionDomainValidation {
  def errorMessage: String
}

case object IdNotFound extends AuctionDomainValidation {
  override def errorMessage: String = "The auction id could not be fetched"
}

case object TitleNotFound extends AuctionDomainValidation {
  override def errorMessage: String = "The auction title could not be fetched"
}

case object SellerNicknameNotFound extends AuctionDomainValidation {
  override def errorMessage: String = "The seller nickname could not be fetched"
}

case object SellerLocationNotFound extends AuctionDomainValidation {
  override def errorMessage: String = "The seller location could not be fetched"
}

case object AuctionTypeNotFound extends AuctionDomainValidation {
  override def errorMessage: String = "The auction type could not be fetched"
}

case object IsSoldFlagNotFound extends AuctionDomainValidation {
  override def errorMessage: String = "The auction sold flag could not be fetched"
}

case object StartPriceNotFound extends AuctionDomainValidation {
  override def errorMessage: String = "The start price could not be fetched"
}

case object FinalPriceNotFound extends AuctionDomainValidation {
  override def errorMessage: String = "The final price could not be fetched"
}

case object StartDateNotFound extends AuctionDomainValidation {
  override def errorMessage: String = "The start date could not be fetched"
}

case object EndDateNotFound extends AuctionDomainValidation {
  override def errorMessage: String = "The end date could not be fetched"
}

case object BidsNotFound extends AuctionDomainValidation {
  override def errorMessage: String = "The list of bids could not be fetched"
}

case object BidsContainerNotFound extends AuctionDomainValidation {
  override def errorMessage: String = "The html bids container could not be fetched"
}

case object MissingBidsForClosedAuction extends AuctionDomainValidation {
  override def errorMessage: String = "Could not fetch bids on a closed auction"
}

case object RequestForBidsForOngoingAuction extends AuctionDomainValidation {
  override def errorMessage: String = "Invalid request to fetch bids on an ongoing auction"
}

case object BidderNicknameNotFound extends AuctionDomainValidation {
  override def errorMessage: String = "The bidder nickname could not be fetched"
}

case object BidderIsAutomaticFlagNotFound extends AuctionDomainValidation {
  override def errorMessage: String = "The bidder automatic bid flag could not be fetched"
}

case object BidPriceNotFound extends AuctionDomainValidation {
  override def errorMessage: String = "The bid price could not be fetched"
}

case object BidDateNotFound extends AuctionDomainValidation {
  override def errorMessage: String = "The bid date could not be fetched"
}
case object InvalidBidQuantity extends AuctionDomainValidation {
  override def errorMessage: String = "The bid quantity could not be fetched"
}

case object InvalidDateFormat extends AuctionDomainValidation {
  override def errorMessage: String = "The date format is invalid"
}

case object InvalidShortDateFormat extends AuctionDomainValidation {
  override def errorMessage: String = "The short date format is invalid"
}

case object InvalidPriceFormat extends AuctionDomainValidation {
  override def errorMessage: String = "The price format is invalid"
}

case object LargeImageUrlNotFound extends AuctionDomainValidation {
  override def errorMessage: String = "The large image url could not be fetched"
}

case object ContainerOfUrlsNotFound extends AuctionDomainValidation {
  override def errorMessage: String = "The container of URLs could not be fetched"
}

case object AuctionLinkNotFound extends AuctionDomainValidation {
  override def errorMessage: String = "The auction link could not be fetched"
}

case object ListingPageNotFound extends AuctionDomainValidation {
  override def errorMessage: String = "The listing page could not be fetched"
}

case object MaximumNumberOfAllowedPagesReached extends AuctionDomainValidation {
  override def errorMessage: String = "The maximum number of allowed page to fetch has been reached"
}

case object LastListingPageReached extends AuctionDomainValidation {
  override def errorMessage: String = "No more pages to fetch"
}
