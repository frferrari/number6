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
