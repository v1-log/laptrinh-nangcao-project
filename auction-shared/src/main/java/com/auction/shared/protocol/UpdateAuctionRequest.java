package com.auction.shared.protocol;

import java.io.Serializable;

public record UpdateAuctionRequest(
        String auctionId,
        String sellerId,
        String itemType,
        String itemName,
        String description,
        double startingPrice,
        double bidIncrement,
        int durationMinutes,
        String extraValue) implements Serializable {

    public UpdateAuctionRequest(
            String auctionId,
            String sellerId,
            String itemType,
            String itemName,
            String description,
            double startingPrice,
            int durationMinutes,
            String extraValue) {
        this(auctionId, sellerId, itemType, itemName, description, startingPrice, 0.1d, durationMinutes, extraValue);
    }

    public String getAuctionId() {
        return auctionId;
    }

    public String getSellerId() {
        return sellerId;
    }

    public String getItemType() {
        return itemType;
    }

    public String getItemName() {
        return itemName;
    }

    public String getDescription() {
        return description;
    }

    public double getStartingPrice() {
        return startingPrice;
    }

    public double getBidIncrement() {
        return bidIncrement;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public String getExtraValue() {
        return extraValue;
    }

    public UpdateAuctionRequest withSellerId(String normalizedSellerId) {
        return new UpdateAuctionRequest(
                auctionId,
                normalizedSellerId,
                itemType,
                itemName,
                description,
                startingPrice,
                bidIncrement,
                durationMinutes,
                extraValue);
    }
}
