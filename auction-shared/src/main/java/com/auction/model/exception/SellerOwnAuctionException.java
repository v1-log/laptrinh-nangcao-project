package com.auction.model.exception;

public class SellerOwnAuctionException extends IllegalArgumentException {
    private final String auctionId;
    private final String sellerId;

    public SellerOwnAuctionException(String auctionId, String sellerId) {
        super("Seller cannot bid on their own auction.");
        this.auctionId = auctionId;
        this.sellerId = sellerId;
    }

    public String getAuctionId() {
        return auctionId;
    }

    public String getSellerId() {
        return sellerId;
    }
}
