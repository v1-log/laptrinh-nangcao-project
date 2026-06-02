package com.auction.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertThrows;

class PlaceBidWhenNotOpenThrowsTest extends AuctionTestBase {

    @Test
    void placeBid_whenAuctionNotOpen_throwsIllegalState() {
        Item item = createArt("A03", "The Scream", 500.0);
        Auction auction = new Auction("AU-03", item, LocalDateTime.now().minusMinutes(1), LocalDateTime.now().plusMinutes(5));
        auction.closeAuction();

        Bidder bidder = new Bidder("B03", "Hoa");
        Bid bid = new Bid(bidder, 600.0);

        assertThrows(IllegalStateException.class, () -> auction.placeBid(bid));
    }
}
