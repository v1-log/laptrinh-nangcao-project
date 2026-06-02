package com.auction.shared.protocol;

import com.auction.shared.enums.CommandType;

import java.io.Serializable;

public final class ClientRequest<T extends Serializable> implements Serializable {
    private final CommandType commandType;
    private final T payload;

    public ClientRequest(CommandType commandType, T payload) {
        this.commandType = commandType;
        this.payload = payload;
    }

    public CommandType getCommandType() {
        return commandType;
    }

    public T getPayload() {
        return payload;
    }
}
