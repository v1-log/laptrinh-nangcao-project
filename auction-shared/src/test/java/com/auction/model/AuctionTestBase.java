package com.auction.model;

import org.junit.jupiter.api.BeforeAll;

import java.time.LocalDateTime;

public abstract class AuctionTestBase {

    protected static Bidder defaultBidder;
    // thiết lập một bidder mặc định để sử dụng trong các test, tránh phải tạo mới trong mỗi test case
    @BeforeAll
    static void globalSetup() {
        defaultBidder = new Bidder("DEF", "Default");
    }
    // giúp tạo art không cần quan tâm đến chi tiết, chỉ cần id, name, startingPrice
    protected Item createArt(String id, String name, double startingPrice) {
        return new Art(id, name, "desc", startingPrice, "artist");
    }
    // giúp tạo auction với end time cách hiện tại một khoảng thời gian nhất định, thuận tiện cho việc test các tình huống liên quan đến thời gian
    protected Auction createAuctionWithEndOffset(String auctionId, Item item, long secondsFromNow) {
        LocalDateTime start = LocalDateTime.now().minusMinutes(1);
        LocalDateTime end = LocalDateTime.now().plusSeconds(secondsFromNow);
        return new Auction(auctionId, item, start, end);
    }
}
