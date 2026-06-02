package com.auction.shared.protocol;

import java.io.Serializable;

public record CreateAuctionRequest(
        String sellerId,
        String itemType,
        String itemName,
        String description,
        double startingPrice,
        double bidIncrement,
        int durationMinutes,
        String extraValue) implements Serializable {

    public CreateAuctionRequest(
            String sellerId,
            String itemType,
            String itemName,
            String description,
            double startingPrice,
            int durationMinutes,
            String extraValue) {
        this(sellerId, itemType, itemName, description, startingPrice, 0.1d, durationMinutes, extraValue);
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

    public CreateAuctionRequest withSellerId(String normalizedSellerId) {
        return new CreateAuctionRequest(
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
