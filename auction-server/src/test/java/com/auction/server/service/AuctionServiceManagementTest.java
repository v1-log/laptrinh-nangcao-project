package com.auction.server.service;

import com.auction.shared.protocol.AuctionActionRequest;
import com.auction.shared.protocol.CreateAuctionRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuctionServiceManagementTest extends AuctionServiceTestBase {

    @Test
    void createAuctionAddsOpenAuctionForSeller() {
        int before = auctionService.loadDashboard("seller01").sellerAuctions().size();

        var createdAuction = auctionService.createAuction(new CreateAuctionRequest(
                "seller01",
                "electronics",
                "Canon Lens",
                "Fast portrait lens",
                450.0,
                60,
                "24"));

        int after = auctionService.loadDashboard("seller01").sellerAuctions().size();
        assertEquals(before + 1, after);
        assertEquals(com.auction.model.AuctionStatus.OPEN, createdAuction.status());
    }

    @Test
    void createClothingAuctionMapsClothingDetailsForDashboard() {
        var createdAuction = auctionService.createAuction(new CreateAuctionRequest(
                "seller01",
                "clothing",
                "Denim Jacket",
                "Vintage blue denim jacket",
                120.0,
                45,
                "XL"));

        assertEquals("Clothing", createdAuction.item().itemType());
        assertEquals("Size: XL", createdAuction.item().detailLabel());
        assertEquals(com.auction.model.AuctionStatus.OPEN, createdAuction.status());
    }

    @Test
    void adminCanFinishAndMarkPaidAuction() {
        auctionService.startAuction(new AuctionActionRequest("A1001", "admin01"));
        auctionService.placeBid(new com.auction.shared.protocol.BidRequest("A1001", "bidder02", 150.0));

        var finished = auctionService.finishAuction(new AuctionActionRequest("A1001", "admin01"));
        assertEquals(com.auction.model.AuctionStatus.FINISHED, finished.status());
        assertEquals("Quang Le", finished.winnerName());

        var paid = auctionService.markPaid(new AuctionActionRequest("A1001", "admin01"));
        assertEquals(com.auction.model.AuctionStatus.PAID, paid.status());
    }

    @Test
    void loadDashboardIncludesFinishedAuctionsInVisibleList() {
        auctionService.startAuction(new AuctionActionRequest("A1001", "admin01"));
        auctionService.finishAuction(new AuctionActionRequest("A1001", "admin01"));

        boolean containsFinishedAuction = auctionService.loadDashboard("bidder01").visibleAuctions().stream()
                .anyMatch(auction -> "A1001".equals(auction.auctionId()) && auction.status() == com.auction.model.AuctionStatus.FINISHED);

        assertTrue(containsFinishedAuction);
    }
}
