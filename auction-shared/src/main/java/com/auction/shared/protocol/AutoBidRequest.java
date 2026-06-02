package com.auction.shared.protocol;

import java.io.Serializable;

public record AutoBidRequest(
        String auctionId,
        String bidderId,
        double maxBid,
        double increment) implements Serializable {

    public String getAuctionId() {
        return auctionId;
    }

    public String getBidderId() {
        return bidderId;
    }

    public double getMaxBid() {
        return maxBid;
    }

    public double getIncrement() {
        return increment;
    }

    public AutoBidRequest withBidderId(String normalizedBidderId) {
        return new AutoBidRequest(auctionId, normalizedBidderId, maxBid, increment);
    }
}
