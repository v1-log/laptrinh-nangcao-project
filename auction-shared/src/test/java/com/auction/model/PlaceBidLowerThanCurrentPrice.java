package com.auction.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlaceBidLowerThanCurrentPriceTest extends AuctionTestBase {

    @Test
    void placeBid_lowerThanCurrentPrice() {
        Item item = createArt("A01", "Mona Lisa", 1_000.0);
        Auction auction = new Auction(
                "AU-01",
                item,
                LocalDateTime.now().minusMinutes(1),
                LocalDateTime.now().plusMinutes(5)
        );
        Bidder bidder = new Bidder("B01", "Minh");
        Bid lowBid = new Bid(bidder, 900.0);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> auction.placeBid(lowBid));

        assertEquals("Bid 900.00 is not higher than current price 1000.00.", exception.getMessage());
        assertEquals(1_000.0, item.getCurrentPrice());
        assertNull(auction.getHighestBid());

        auction.closeAuction();
    }
}
