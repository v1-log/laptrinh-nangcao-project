package com.auction.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AutoSnipping extends AuctionTestBase {
// Kiểm tra rằng khi đặt một giá thầu gần đến thời điểm kết thúc của đấu giá, thời gian kết thúc 
// sẽ được tự động gia hạn thêm một khoảng thời gian nhất định (ví dụ: 30 giây) để tạo cơ hội cho các người tham gia khác đặt giá thầu
    @Test
    void placeBid_extendsEndTimeWhenCloseToFinish() {
        Item item = createArt("A04", "Old Master", 200.0);
        LocalDateTime originalEnd = LocalDateTime.now().plusSeconds(5);
        Auction auction = new Auction("AU-04", item, LocalDateTime.now().minusMinutes(1), originalEnd);
    
        Bidder bidder = new Bidder("B04", "An");
        Bid bid = new Bid(bidder, 300.0);

        auction.placeBid(bid);

        LocalDateTime newEnd = auction.getEndTime();
        assertEquals(originalEnd.plusSeconds(30), newEnd);

        auction.closeAuction();
    }
}
