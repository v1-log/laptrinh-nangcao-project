package com.auction.model;

public class Electronics extends Item {
    private int warrantyMonths;

    public Electronics(String id, String name, String description,
                       double startingPrice, int warrantyMonths) {
        super(id, name, description, startingPrice);
        this.warrantyMonths = warrantyMonths;
    }

    public int getWarrantyMonths() {
        return warrantyMonths;
    }

    @Override
    public ItemType getItemType() {
        return ItemType.ELECTRONICS;
    }

    @Override
    public void printInfo() {
        System.out.println("Electronics: " + getName()
                + " | Price: " + getCurrentPrice()
                + " | Warranty: " + warrantyMonths + " months");
    }
}
