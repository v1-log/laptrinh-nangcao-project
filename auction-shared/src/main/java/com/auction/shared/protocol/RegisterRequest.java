package com.auction.shared.protocol;

import com.auction.shared.enums.UserRole;

import java.io.Serializable;

public record RegisterRequest(
        String username,
        String password,
        String displayName,
        UserRole role,
        String storefrontName) implements Serializable {

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getDisplayName() {
        return displayName;
    }

    public UserRole getRole() {
        return role;
    }

    public String getStorefrontName() {
        return storefrontName;
    }
}
