package com.auction.model.exception;

public class InvalidBidException extends IllegalArgumentException {
    private final double currentPrice;
    private final double attemptedAmount;

    public InvalidBidException(double currentPrice, double attemptedAmount) {
        super(String.format("Bid %.2f is not higher than current price %.2f.", attemptedAmount, currentPrice));
        this.currentPrice = currentPrice;
        this.attemptedAmount = attemptedAmount;
    }

    public double getCurrentPrice() {
        return currentPrice;
    }

    public double getAttemptedAmount() {
        return attemptedAmount;
    }
}