package com.auction.shared.protocol;
//  respone của seber trả về cho client
import com.auction.shared.enums.AuctionEventType;
import com.auction.shared.enums.ResponseStatus;

import java.io.Serializable;

public final class ServerResponse<T extends Serializable> implements Serializable {
    private final ResponseStatus status;
    private final String message;
    private final T payload;
    private final AuctionEventType eventType;

    public ServerResponse(ResponseStatus status, String message, T payload, AuctionEventType eventType) {
        this.status = status;
        this.message = message;
        this.payload = payload;
        this.eventType = eventType;
    }

    public static <T extends Serializable> ServerResponse<T> success(String message, T payload) {
        return new ServerResponse<>(ResponseStatus.SUCCESS, message, payload, AuctionEventType.SYSTEM);
    }

    public static <T extends Serializable> ServerResponse<T> error(String message) {
        return new ServerResponse<>(ResponseStatus.ERROR, message, null, AuctionEventType.SYSTEM);
    }

    public static <T extends Serializable> ServerResponse<T> event(AuctionEventType eventType, String message, T payload) {
        return new ServerResponse<>(ResponseStatus.EVENT, message, payload, eventType);
    }

    public ResponseStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public T getPayload() {
        return payload;
    }

    public AuctionEventType getEventType() {
        return eventType;
    }
}
