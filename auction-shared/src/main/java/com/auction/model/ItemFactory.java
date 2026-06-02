package com.auction.model;

public class ItemFactory {

    public static Item createItem(
            ItemType type,
            String id,
            String name,
            String description,
            double startingPrice,
            Object... extra
    ) {

        switch (type) {
            case ELECTRONICS:
                int warranty = (int) extra[0];
                return new Electronics(id, name, description, startingPrice, warranty);

            case ART:
                String artist = (String) extra[0];
                return new Art(id, name, description, startingPrice, artist);

            case CLOTHING:
                String size = (String) extra[0];
                return new Clothing(id, name, description, startingPrice, size);

            default:
                throw new RuntimeException("Invalid item type");
        }
    }
}
