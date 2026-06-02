package com.auction.model;

public abstract class Item {
    private String id;
    private String name;
    private String description;
    private double startingPrice;
    private double currentPrice;

    public Item(String id, String name, String description, double startingPrice) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.startingPrice = startingPrice;
        this.currentPrice = startingPrice;
    }

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

    public void setCurrentPrice(double currentPrice) {
        if (currentPrice > this.currentPrice) {
            this.currentPrice = currentPrice;
        } else {
            throw new RuntimeException("New price must be higher than current price");
        }
    }

    public void restoreCurrentPrice(double currentPrice) {
        this.currentPrice = Math.max(startingPrice, currentPrice);
    }

    public abstract ItemType getItemType();

    public abstract void printInfo();
}
