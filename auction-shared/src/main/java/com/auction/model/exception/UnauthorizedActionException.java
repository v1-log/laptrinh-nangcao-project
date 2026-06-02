package com.auction.model.exception;

public class UnauthorizedActionException extends IllegalArgumentException {
    public UnauthorizedActionException(String message) {
        super(message);
    }
}
