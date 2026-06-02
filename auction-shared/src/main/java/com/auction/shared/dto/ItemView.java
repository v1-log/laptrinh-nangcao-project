package com.auction.shared.dto;

import java.io.Serializable;

public record ItemView(
        String id,
        String name,
        String description,
        double startingPrice,
        double currentPrice,
        String itemType,
        String detailLabel) implements Serializable {

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public double getStartingPrice() {
        return startingPrice;
    }

    public double getCurrentPrice() {
        return currentPrice;
    }

    public String getItemType() {
        return itemType;
    }

    public String getDetailLabel() {
        return detailLabel;
    }
}
