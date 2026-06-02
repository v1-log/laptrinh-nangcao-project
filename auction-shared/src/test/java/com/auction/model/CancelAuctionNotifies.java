package com.auction.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CancelAuctionNotifiesTest extends AuctionTestBase {
    // Kiểm tra rằng khi hủy đấu giá, trạng thái được cập nhật đúng và các observer được thông báo
    @Test
    void cancelAuction_setsCanceledAndNotifies() {
        Item item = createArt("A07", "Obj", 75.0);
        Auction auction = new Auction("AU-07", item, LocalDateTime.now().minusMinutes(1), LocalDateTime.now().plusMinutes(5));
//
        AtomicInteger count = new AtomicInteger(0);
        // Đăng ký một observer để đếm số lần được thông báo khi đấu giá bị hủy
        auction.addObserver(a -> count.incrementAndGet());

        auction.cancelAuction();

        assertEquals(AuctionStatus.CANCELED, auction.getStatus());
        assertEquals(1, count.get());
    }
}
