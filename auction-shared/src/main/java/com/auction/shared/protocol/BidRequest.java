package com.auction.shared.protocol;

import java.io.Serializable;

public record BidRequest(
        String auctionId,
        String bidderId,
        double amount) implements Serializable {

    public String getAuctionId() {
        return auctionId;
    }

    public String getBidderId() {
        return bidderId;
    }

    public double getAmount() {
        return amount;
    }

    public BidRequest withBidderId(String normalizedBidderId) {
        return new BidRequest(auctionId, normalizedBidderId, amount);
    }
}
