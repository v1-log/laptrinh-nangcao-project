package com.auction.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
// test này dùng để đảm bảo rằng danh sách lịch sử đấu giá được trả về là không thể sửa đổi, 
// ngăn chặn việc thêm giao dịch mới vào lịch sử sau khi đã nhận được.
class BidHistoryUnmodifiableTest extends AuctionTestBase {

    @Test
    void getBidHistory_returnsUnmodifiableList() {
        Item item = createArt("A10", "Obj", 60.0);
        Auction auction = new Auction("AU-10", item, LocalDateTime.now().minusMinutes(1), 
        LocalDateTime.now().plusMinutes(5));
        
        Bidder bidder = new Bidder("B10", "T");
        auction.placeBid(new Bid(bidder, 70.0));
        
        List<BidTransaction> history = auction.getBidHistory();
        assertThrowsExactly(UnsupportedOperationException.class, () -> history.add(new BidTransaction("AU-10", new Bid(bidder, 80.0))));

        auction.closeAuction();
    }
}
