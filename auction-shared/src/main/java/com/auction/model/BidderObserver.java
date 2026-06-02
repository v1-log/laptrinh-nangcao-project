package com.auction.model;

public class BidderObserver implements Observer {
    private String name;

    public BidderObserver(String name) {
        this.name = name;
    }

    @Override
    public void update(Auction auction) {
        System.out.println(name + "thấy giá mới: " + auction.getItem().getCurrentPrice());
    }
}
