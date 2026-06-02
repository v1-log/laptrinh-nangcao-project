package com.auction.shared.enums;

import java.io.Serializable;

public enum CommandType implements Serializable {
    LOGIN,
    REGISTER,
    LOAD_DASHBOARD,
    LOAD_AUCTION_DETAILS,
    SUBSCRIBE_AUCTION,
    PLACE_BID,
    SET_AUTO_BID,
    CREATE_AUCTION,
    UPDATE_AUCTION,
    DELETE_AUCTION,
    START_AUCTION,
    FINISH_AUCTION,
    MARK_PAID,
    CANCEL_AUCTION,
    DEPOSIT_FUNDS,
    PAY_AUCTION,
    LOGOUT
}
