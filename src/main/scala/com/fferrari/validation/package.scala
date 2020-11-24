package com.fferrari

import cats.data.ValidatedNec

package object validation {
  type ValidationResult[A] = ValidatedNec[AuctionDomainValidation, A]
}
