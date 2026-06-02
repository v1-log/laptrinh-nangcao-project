package com.auction.shared.protocol;

import java.io.Serializable;

public record DepositFundsRequest(
        String userId,
        double amount) implements Serializable {

    public String getUserId() {
        return userId;
    }

    public double getAmount() {
        return amount;
    }

    public DepositFundsRequest withUserId(String normalizedUserId) {
        return new DepositFundsRequest(normalizedUserId, amount);
    }
}
