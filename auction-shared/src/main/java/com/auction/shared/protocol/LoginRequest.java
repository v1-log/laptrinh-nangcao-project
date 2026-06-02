package com.auction.shared.protocol;

import java.io.Serializable;

public record LoginRequest(
        String username,
        String password) implements Serializable {

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }
}
