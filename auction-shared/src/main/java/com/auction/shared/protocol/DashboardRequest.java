package com.auction.shared.protocol;

import java.io.Serializable;

public record DashboardRequest(
        String userId) implements Serializable {

    public String getUserId() {
        return userId;
    }

    public DashboardRequest withUserId(String normalizedUserId) {
        return new DashboardRequest(normalizedUserId);
    }
}
