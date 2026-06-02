package com.auction.shared.dto;

import java.io.Serializable;

public record AuctionMetrics(
        int visibleAuctionCount,
        int sellerAuctionCount,
        int totalBidCount) implements Serializable {

    public int getVisibleAuctionCount() {
        return visibleAuctionCount;
    }

    public int getSellerAuctionCount() {
        return sellerAuctionCount;
    }

    public int getTotalBidCount() {
        return totalBidCount;
    }
}
