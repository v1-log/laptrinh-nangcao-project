package com.auction.shared.dto;

import java.io.Serializable;
import java.time.LocalDateTime;

public record BidView(
        String bidderId,
        String bidderName,
        double amount,
        LocalDateTime timestamp) implements Serializable {

    public String getBidderId() {
        return bidderId;
    }

    public String getBidderName() {
        return bidderName;
    }

    public double getAmount() {
        return amount;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }
}
