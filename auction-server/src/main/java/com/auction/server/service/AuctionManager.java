package com.auction.server.service;

import com.auction.model.Auction;
import com.auction.model.Bid;
import com.auction.model.Bidder;
import com.auction.server.domain.ManagedAuction;
import com.auction.server.event.AuctionEventPublisher;
import com.auction.shared.protocol.ServerResponse;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public final class AuctionManager {
    private static volatile AuctionManager instance;

    private final Map<String, ManagedAuction> auctionRegistry = new ConcurrentHashMap<>();
    private final Map<String, ReentrantLock> auctionLocks = new ConcurrentHashMap<>();
    private final Map<String, Map<String, AutoBidRule>> autoBidRules = new ConcurrentHashMap<>();
    private final Set<String> activeSessions = ConcurrentHashMap.newKeySet();
    private volatile AuctionEventPublisher eventPublisher;

    private AuctionManager() {
    }

    public static AuctionManager getInstance() {
        AuctionManager local = instance;
        if (local == null) {
            synchronized (AuctionManager.class) {
                local = instance;
                if (local == null) {
                    local = new AuctionManager();
                    instance = local;
                }
            }
        }
        return local;
    }

    public void configure(AuctionEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public void registerAuction(ManagedAuction record) {
        if (record != null) {
            auctionRegistry.put(record.getAuctionId(), record);
            autoBidRules.remove(record.getAuctionId());
        }
    }

    public void removeAuction(String auctionId) {
        auctionRegistry.remove(auctionId);
        auctionLocks.remove(auctionId);
        autoBidRules.remove(auctionId);
    }

    public Optional<ManagedAuction> findRegisteredAuction(String auctionId) {
        return Optional.ofNullable(auctionRegistry.get(auctionId));
    }

    public List<String> activeAuctionIds() {
        return auctionRegistry.keySet().stream().sorted().toList();
    }

    public ReentrantLock lockForAuction(String auctionId) {
        return auctionLocks.computeIfAbsent(auctionId, ignored -> new ReentrantLock());
    }

    public void registerSession(String sessionId) {
        if (sessionId != null && !sessionId.isBlank()) {
            activeSessions.add(sessionId);
        }
    }

    public void unregisterSession(String sessionId) {
        if (sessionId != null) {
            activeSessions.remove(sessionId);
        }
    }

    public int activeSessionCount() {
        return activeSessions.size();
    }

    public AutoBidRule setAutoBid(String auctionId, Bidder bidder, double maxBid, double increment) {
        AutoBidRule rule = new AutoBidRule(
                auctionId,
                bidder,
                maxBid,
                increment,
                LocalDateTime.now());
        autoBidRules.computeIfAbsent(auctionId, ignored -> new ConcurrentHashMap<>())
                .put(bidder.getId(), rule);
        return rule;
    }

    public int autoBidRuleCount(String auctionId) {
        return autoBidRules.getOrDefault(auctionId, Map.of()).size();
    }

    public List<Bid> runAutoBids(Auction auction) {
        Map<String, AutoBidRule> rules = autoBidRules.get(auction.getAuctionId());
        if (rules == null || rules.isEmpty()) {
            return List.of();
        }

        List<Bid> placedBids = new ArrayList<>();
        int guardLimit = Math.max(4, rules.size() * 8);
        int guard = 0;
        while (guard++ < guardLimit) {
            Optional<AutoBidRule> nextRule = chooseNextRule(auction, rules);
            if (nextRule.isEmpty()) {
                break;
            }
            AutoBidRule rule = nextRule.get();
            double nextAmount = calculateNextAutoBidAmount(auction, rule);
            if (Double.compare(nextAmount, rule.maxBid()) > 0) {
                break;
            }
            Bid bid = new Bid(rule.bidder(), nextAmount);
            auction.placeBid(bid);
            placedBids.add(bid);
        }
        return placedBids;
    }

    public void publishGlobal(ServerResponse<?> response) {
        AuctionEventPublisher publisher = eventPublisher;
        if (publisher != null) {
            publisher.publishGlobal(response);
        }
    }

    public void publishToAuction(String auctionId, ServerResponse<?> response) {
        AuctionEventPublisher publisher = eventPublisher;
        if (publisher != null) {
            publisher.publishToAuction(auctionId, response);
        }
    }

    private Optional<AutoBidRule> chooseNextRule(Auction auction, Map<String, AutoBidRule> rules) {
        String currentHighestBidderId = auction.getHighestBid() == null
                ? null
                : auction.getHighestBid().getBidder().getId();
        double minimumNextBid = auction.getMinimumNextBid();
        PriorityQueue<AutoBidRule> candidates = new PriorityQueue<>(
                Comparator.comparingDouble(AutoBidRule::maxBid).reversed()
                        .thenComparing(AutoBidRule::createdAt));

        rules.values().stream()
                .filter(rule -> !rule.bidder().getId().equals(currentHighestBidderId))
                .filter(rule -> Double.compare(rule.maxBid(), minimumNextBid) >= 0)
                .forEach(candidates::offer);

        return Optional.ofNullable(candidates.poll());
    }

    private double calculateNextAutoBidAmount(Auction auction, AutoBidRule rule) {
        double step = Math.max(auction.getBidIncrement(), rule.increment());
        double minimumNextBid = auction.getMinimumNextBid();
        double targetAmount = Math.min(rule.maxBid(), auction.getItem().getCurrentPrice() + step);
        if (Double.compare(targetAmount, minimumNextBid) < 0) {
            targetAmount = minimumNextBid;
        }
        double normalized = Math.round(targetAmount * 100.0d) / 100.0d;
        return Double.compare(normalized, minimumNextBid) < 0 ? minimumNextBid : normalized;
    }

    public record AutoBidRule(
            String auctionId,
            Bidder bidder,
            double maxBid,
            double increment,
            LocalDateTime createdAt) {
    }
}
