package com.auction.server.service;

import com.auction.shared.protocol.AuctionActionRequest;
import com.auction.shared.protocol.BidRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuctionServicePlacementTest extends AuctionServiceTestBase {

    @Test
    void sellerCannotBidOnAuction() {
        assertThrows(IllegalArgumentException.class,
                () -> auctionService.placeBid(new BidRequest("A1001", "seller01", 150.0)));
    }

    @Test
    void nonOwnerSellerCanPlaceBid() {
        auctionService.startAuction(new AuctionActionRequest("A1001", "seller01"));

        var view = auctionService.placeBid(new BidRequest("A1001", "seller02", 150.0));

        assertEquals("Other Store", view.highestBid().bidderName());
        assertEquals(150.0, view.item().currentPrice(), 0.001);
    }

    @Test
    void auctionMustBeStartedBeforeBidding() {
        var exception = org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> auctionService.placeBid(new BidRequest("A1001", "bidder01", 150.0)));

        assertEquals("Auction is not running for bidding", exception.getMessage());

        var running = auctionService.startAuction(new AuctionActionRequest("A1001", "seller01"));
        org.junit.jupiter.api.Assertions.assertEquals(com.auction.model.AuctionStatus.RUNNING, running.status());
    }
}
