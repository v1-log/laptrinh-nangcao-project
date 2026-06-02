package com.auction.model.exception;

public class ClientProtocolException extends RuntimeException {
    public ClientProtocolException(String message) {
        super(message);
    }

    public ClientProtocolException(String message, Throwable cause) {
        super(message, cause);
    }
}
