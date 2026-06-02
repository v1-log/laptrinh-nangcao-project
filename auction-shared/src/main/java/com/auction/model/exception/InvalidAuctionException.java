package com.auction.model.exception;

public class InvalidAuctionException extends IllegalArgumentException {
    private final String fieldName;

    public InvalidAuctionException(String message) {
        this(message, null);
    }

    public InvalidAuctionException(String message, String fieldName) {
        super(message);
        this.fieldName = fieldName;
    }

    public String getFieldName() {
        return fieldName;
    }
}
