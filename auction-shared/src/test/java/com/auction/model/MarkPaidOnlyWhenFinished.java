package com.auction.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class MarkPaidOnlyWhenFinishedTest extends AuctionTestBase {
// Kiểm tra rằng phương thức markPaid() chỉ có hiệu lực khi đấu giá đã kết thúc, 
// nghĩa là trạng thái sẽ chuyển sang PAID chỉ khi đấu giá đang ở trạng thái FINISHED, và không thay đổi trạng thái nếu đấu giá chưa kết thúc
    @Test
    void markPaid_onlyWhenFinished() {
        Item item = createArt("A08", "Obj", 80.0);
        Auction auction = new Auction("AU-08", item, LocalDateTime.now().minusMinutes(1), LocalDateTime.now().plusMinutes(5));
// Thử markPaid khi đấu giá vẫn đang mở, trạng thái không nên thay đổi và observer không được thông báo
        AtomicInteger count = new AtomicInteger(0);
        auction.addObserver(a -> count.incrementAndGet());
        auction.markPaid();
        assertFalse(auction.getStatus() == AuctionStatus.PAID);
        assertEquals(0, count.get());

        // finish then markPaid
        auction.closeAuction();
        auction.addObserver(a -> count.incrementAndGet());
        auction.markPaid();
        assertEquals(AuctionStatus.PAID, auction.getStatus());
    }
}
