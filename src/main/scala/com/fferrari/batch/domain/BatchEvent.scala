package com.fferrari.batch.domain

import java.time.Instant
import java.util.UUID

import com.fferrari.auction.domain.Auction
import com.fferrari.batchspecification.domain.BatchSpecification
import com.fferrari.escommon.EntityEvent

sealed trait BatchEvent extends EntityEvent[Batch.ID]

case class BatchCreated(entityID: Batch.ID,
                        timestamp: Instant,
                        batchSpecificationId: BatchSpecification.ID,
                        auctions: List[Auction]
                       ) extends BatchEvent

case class AuctionMatched(entityID: Batch.ID,
                          timestamp: Instant,
                          auctionID: Auction.ID,
                          matchID: UUID) extends BatchEvent

