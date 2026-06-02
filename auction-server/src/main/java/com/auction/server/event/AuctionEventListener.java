package com.auction.server.event;

import com.auction.shared.protocol.ServerResponse;

public interface AuctionEventListener {
    void onAuctionEvent(ServerResponse<?> response);
}
