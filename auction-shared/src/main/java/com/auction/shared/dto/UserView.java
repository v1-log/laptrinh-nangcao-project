package com.auction.shared.dto;

import com.auction.shared.enums.UserRole;

import java.io.Serializable;

public record UserView(
        String id,
        String name,
        UserRole role,
        double balance) implements Serializable {

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public UserRole getRole() {
        return role;
    }

    public double getBalance() {
        return balance;
    }
}
