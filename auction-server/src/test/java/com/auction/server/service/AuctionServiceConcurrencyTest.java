package com.auction.server.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuctionServiceConcurrencyTest extends AuctionServiceTestBase {

    @Test
    void concurrentBidsLeaveAuctionInConsistentState() throws Exception {
        auctionService.startAuction(new com.auction.shared.protocol.AuctionActionRequest("A1001", "seller01"));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(1);

        Future<?> first = executor.submit(() -> awaitAndBid(latch, "bidder01", 150.0));
        Future<?> second = executor.submit(() -> awaitAndBid(latch, "bidder02", 160.0));

        latch.countDown();
        first.get(5, TimeUnit.SECONDS);
        second.get(5, TimeUnit.SECONDS);
        executor.shutdownNow();

        var auction = auctionService.loadAuction("A1001");
        assertEquals(160.0, auction.item().currentPrice(), 0.001);
        assertTrue(List.of(1, 2).contains(auction.bidHistory().size()));
    }

    private void awaitAndBid(CountDownLatch latch, String bidderId, double amount) {
        try {
            latch.await();
            auctionService.placeBid(new com.auction.shared.protocol.BidRequest("A1001", bidderId, amount));
        } catch (Exception ignored) {
        }
    }
}
