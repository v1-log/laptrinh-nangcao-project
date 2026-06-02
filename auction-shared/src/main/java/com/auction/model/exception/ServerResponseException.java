package com.auction.model.exception;

public class ServerResponseException extends RuntimeException {
    public ServerResponseException(String message) {
        super(message);
    }
}
