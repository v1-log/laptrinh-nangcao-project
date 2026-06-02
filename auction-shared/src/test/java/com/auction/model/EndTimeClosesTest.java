package com.auction.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConstructorPastEndTimeClosesTest extends AuctionTestBase {
    // Kiểm tra rằng khi tạo một đấu giá với thời gian kết thúc đã qua, trạng thái của đấu giá sẽ được đặt thành FINISHED ngay lập tức
    @Test
    void constructor_withPastEndTime_closesImmediately() {
        Item item = createArt("A05", "Ancient", 100.0);
        Auction auction = new Auction("AU-05", item, LocalDateTime.now().minusHours(1), LocalDateTime.now().minusSeconds(1));
        // Ngay sau khi tạo, đấu giá đã kết thúc vì end time đã qua
        assertEquals(AuctionStatus.FINISHED, auction.getStatus());
    }
}
