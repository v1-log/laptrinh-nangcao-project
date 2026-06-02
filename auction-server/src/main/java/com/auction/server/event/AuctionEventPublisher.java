//phát realtime tới client
package com.auction.server.event;

import com.auction.shared.protocol.ServerResponse;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

public final class AuctionEventPublisher {
    private final Set<AuctionEventListener> globalListeners = new CopyOnWriteArraySet<>();
    private final Map<String, Set<AuctionEventListener>> auctionListeners = new ConcurrentHashMap<>();

    public void subscribeGlobal(AuctionEventListener listener) {
        globalListeners.add(listener);
    }

    public void subscribeToAuction(String auctionId, AuctionEventListener listener) {
        auctionListeners.computeIfAbsent(auctionId, ignored -> new CopyOnWriteArraySet<>()).add(listener);
    }

    public void unsubscribe(AuctionEventListener listener) {
        globalListeners.remove(listener);
        auctionListeners.values().forEach(listeners -> listeners.remove(listener));
    }

    public void publishGlobal(ServerResponse<?> response) {
        globalListeners.forEach(listener -> listener.onAuctionEvent(response));
    }

    public void publishToAuction(String auctionId, ServerResponse<?> response) {
        Set<AuctionEventListener> recipients = new HashSet<>(globalListeners);
        recipients.addAll(auctionListeners.getOrDefault(auctionId, Set.of()));
        recipients.forEach(listener -> listener.onAuctionEvent(response));
    }
}
