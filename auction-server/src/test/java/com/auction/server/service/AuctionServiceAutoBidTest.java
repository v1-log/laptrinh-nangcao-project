package com.auction.server.service;

import com.auction.shared.protocol.AutoBidRequest;
import com.auction.shared.protocol.AuctionActionRequest;
import com.auction.shared.protocol.BidRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
// test dùng để xác nhận rằng tính năng đặt giá tự động (auto-bid) trong AuctionService hoạt động đúng cách, 
// đảm bảo rằng khi một người dùng đặt giá tự động và sau đó có một giá thầu cạnh
class AuctionServiceAutoBidTest extends AuctionServiceTestBase {

    @Test
    void autoBidRaisesBidAfterCompetingOffer() {
        auctionService.startAuction(new AuctionActionRequest("A1001", "seller01"));

        var armed = auctionService.setAutoBid(new AutoBidRequest("A1001", "bidder01", 170.0, 10.0));
        assertEquals("Mia Tran", armed.highestBid().bidderName());
        assertEquals(110.0, armed.item().currentPrice(), 0.001);

        var afterCompetingBid = auctionService.placeBid(new BidRequest("A1001", "bidder02", 150.0));

        assertEquals("Mia Tran", afterCompetingBid.highestBid().bidderName());
        assertEquals(160.0, afterCompetingBid.item().currentPrice(), 0.001);
        assertEquals(3, afterCompetingBid.bidHistory().size());
    }
}
