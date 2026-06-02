package com.auction.shared.enums;

import java.io.Serializable;

public enum AuctionEventType implements Serializable {
    SYSTEM,
    AUCTION_CREATED,
    AUCTION_UPDATED,
    AUCTION_DELETED,
    AUCTION_STATUS_CHANGED,
    BID_PLACED
}
