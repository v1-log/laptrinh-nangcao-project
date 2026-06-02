package com.auction.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlaceBidValidUpdatesStateTest extends AuctionTestBase {

    @Test
    void placeBid_validUpdatesStateAndNotifiesObservers() {
        Item item = createArt("A02", "Starry Night", 1_000.0);
        Auction auction = new Auction("AU-02", item, LocalDateTime.now().minusMinutes(1), LocalDateTime.now().plusMinutes(5));

        Bidder bidder = new Bidder("B02", "Lan");
        Bid bid = new Bid(bidder, 1_500.0);

        AtomicInteger notified = new AtomicInteger(0);
        Observer obs = a -> notified.incrementAndGet();
        auction.addObserver(obs);

        auction.placeBid(bid);

        assertEquals(1_500.0, item.getCurrentPrice());
        assertEquals(1_500.0, auction.getHighestBid().getAmount());
        assertEquals(AuctionStatus.RUNNING, auction.getStatus());
        assertEquals(1, auction.getBidHistory().size());
        assertEquals(1, notified.get());

        auction.closeAuction();
    }
}
