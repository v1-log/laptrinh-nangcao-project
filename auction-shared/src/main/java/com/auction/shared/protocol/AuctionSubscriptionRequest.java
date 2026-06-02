package com.auction.shared.protocol;

import java.io.Serializable;

public record AuctionSubscriptionRequest(
        String auctionId,
        String viewerId) implements Serializable {

    public String getAuctionId() {
        return auctionId;
    }

    public String getViewerId() {
        return viewerId;
    }

    public AuctionSubscriptionRequest withViewerId(String normalizedViewerId) {
        return new AuctionSubscriptionRequest(auctionId, normalizedViewerId);
    }
}
