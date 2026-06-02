package com.auction.model;

public class Seller extends User {

    public Seller(String id, String name) {
        super(id, name);
    }

    public Seller(String id, String name, String password) {
        super(id, name, password);
    }

    public Seller(String id, String name, String password, double balance) {
        super(id, name, password, balance);
    }
}
