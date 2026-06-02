package com.auction.shared.protocol;

import java.io.Serializable;

public record AuctionActionRequest(
        String auctionId,
        String actorId) implements Serializable {

    public String getAuctionId() {
        return auctionId;
    }

    public String getActorId() {
        return actorId;
    }

    public AuctionActionRequest withActorId(String normalizedActorId) {
        return new AuctionActionRequest(auctionId, normalizedActorId);
    }
}
