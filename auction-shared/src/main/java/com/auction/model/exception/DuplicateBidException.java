package com.auction.model.exception;

public class DuplicateBidException extends IllegalArgumentException {
    private final String auctionId;
    private final String bidderId;
    private final double amount;

    public DuplicateBidException(String auctionId, String bidderId, double amount) {
        super(String.format("Bidder '%s' already has the highest bid %.2f on auction '%s'.",
                bidderId, amount, auctionId));
        this.auctionId = auctionId;
        this.bidderId = bidderId;
        this.amount = amount;
    }

    public String getAuctionId() {
        return auctionId;
    }

    public String getBidderId() {
        return bidderId;
    }

    public double getAmount() {
        return amount;
    }
}
