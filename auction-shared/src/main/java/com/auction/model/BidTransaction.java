package com.auction.model;

import java.time.LocalDateTime;

public class BidTransaction {

    private final String auctionId;
    private final Bid bid;
    private final LocalDateTime transactionTime;

    public BidTransaction(String auctionId, Bid bid) {
        this(auctionId, bid, LocalDateTime.now());
    }

    public BidTransaction(String auctionId, Bid bid, LocalDateTime transactionTime) {
        this.auctionId = auctionId;
        this.bid = bid;
        this.transactionTime = transactionTime == null ? LocalDateTime.now() : transactionTime;
    }

    public String getAuctionId() {
        return auctionId;
    }

    public Bid getBid() {
        return bid;
    }

    public double getAmount() {
        return bid.getAmount();
    }

    public String getBidderName() {
        return bid.getBidder().getName();
    }

    public LocalDateTime getTransactionTime() {
        return transactionTime;
    }

    @Override
    public String toString() {
        return "[" + transactionTime + "] " + getBidderName() + " bid " + getAmount();
    }
}
