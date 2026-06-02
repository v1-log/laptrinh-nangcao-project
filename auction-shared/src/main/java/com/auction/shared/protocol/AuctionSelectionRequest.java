package com.auction.shared.protocol;

import java.io.Serializable;

public record AuctionSelectionRequest(
        String auctionId) implements Serializable {

    public String getAuctionId() {
        return auctionId;
    }
}
