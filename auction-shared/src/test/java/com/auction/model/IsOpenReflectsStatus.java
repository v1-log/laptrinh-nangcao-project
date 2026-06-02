package com.auction.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IsOpenReflectsStatusTest extends AuctionTestBase {
// Kiểm tra rằng phương thức isOpen() phản ánh đúng trạng thái của đấu giá, nghĩa là trả về true khi đấu giá đang mở và false khi đấu giá đã đóng hoặc bị hủy
    @Test
    void isOpen_reflectsStatus() {
        Item item = createArt("A09", "Obj", 20.0);
        Auction auction = new Auction("AU-09", item, LocalDateTime.now().minusMinutes(1), LocalDateTime.now().plusMinutes(5));
        assertTrue(auction.isOpen());

        Bidder bidder = new Bidder("B09", "Test");
        auction.placeBid(new Bid(bidder, 40.0));
        assertTrue(auction.isOpen());
        // Hủy đấu giá, sau đó kiểm tra isOpen trả về false
        auction.cancelAuction();
        assertFalse(auction.isOpen());
    }
}
