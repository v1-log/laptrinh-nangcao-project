package com.auction.shared.dto;

import java.io.Serializable;
import java.util.List;

public record DashboardView(
        UserView currentUser,
        List<AuctionView> visibleAuctions,
        List<AuctionView> sellerAuctions,
        AuctionMetrics metrics) implements Serializable {

    public UserView getCurrentUser() {
        return currentUser;
    }

    public List<AuctionView> getVisibleAuctions() {
        return visibleAuctions;
    }

    public List<AuctionView> getSellerAuctions() {
        return sellerAuctions;
    }

    public AuctionMetrics getMetrics() {
        return metrics;
    }
}
