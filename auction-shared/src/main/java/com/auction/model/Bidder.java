package com.auction.model;

public class Bidder extends User {
    public Bidder(String id, String name) {
        super(id, name);
    }

    public Bidder(String id, String name, String password) {
        super(id, name, password);
    }

    public Bidder(String id, String name, String password, double balance) {
        super(id, name, password, balance);
    }
}
