package com.fferrari.pricescraper.auction

import cats.data.ValidatedNec

package object application {
  type ValidationResult[A] = ValidatedNec[AuctionDomainValidation, A]
}
