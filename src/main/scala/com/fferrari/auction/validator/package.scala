package com.fferrari.auction.validator

import cats.data.ValidatedNec

package object validator {
  type ValidationResult[A] = ValidatedNec[AuctionDomainValidation, A]
}
