package com.auction.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CloseAuctionIdempotentTest extends AuctionTestBase {
// Kiểm tra rằng phương thức closeAuction() là idempotent, nghĩa là gọi nhiều lần sẽ không thay đổi trạng thái sau lần đầu tiên và chỉ thông báo một lần cho các observer
    @Test
    void closeAuction_isIdempotent_andNotifiesOnce() {
        Item item = createArt("A06", "Piece", 50.0);
        Auction auction = new Auction("AU-06", item, LocalDateTime.now().minusMinutes(1), LocalDateTime.now().plusMinutes(5));

        AtomicInteger count = new AtomicInteger(0);
        auction.addObserver(a -> count.incrementAndGet());
// Gọi closeAuction lần đầu tiên, trạng thái sẽ chuyển sang FINISHED và observer được thông báo
        auction.closeAuction();
// Gọi closeAuction lần thứ hai, trạng thái vẫn giữ nguyên và observer không được thông báo thêm lần nào nữa
        auction.closeAuction();

        assertEquals(AuctionStatus.FINISHED, auction.getStatus());
        assertEquals(1, count.get());
    }
}
