package com.auction.server.domain;

import com.auction.model.Auction;
import com.auction.model.Seller;

import java.time.LocalDateTime;

public final class ManagedAuction {
    private final String auctionId;
    private final Seller seller;
    private final LocalDateTime createdAt;
    private Auction auction;

    public ManagedAuction(Auction auction, Seller seller, LocalDateTime createdAt) {
        this.auctionId = auction.getAuctionId();
        this.auction = auction;
        this.seller = seller;
        this.createdAt = createdAt;
    }

    public String getAuctionId() {
        return auctionId;
    }

    public Auction getAuction() {
        return auction;
    }

    public void replaceAuction(Auction auction) {
        this.auction = auction;
    }

    public Seller getSeller() {
        return seller;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
