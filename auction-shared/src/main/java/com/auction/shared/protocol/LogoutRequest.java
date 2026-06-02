package com.auction.shared.protocol;

import java.io.Serializable;

public record LogoutRequest(
        String clientId) implements Serializable {

    public String getClientId() {
        return clientId;
    }

    public LogoutRequest withClientId(String normalizedClientId) {
        return new LogoutRequest(normalizedClientId);
    }
}
