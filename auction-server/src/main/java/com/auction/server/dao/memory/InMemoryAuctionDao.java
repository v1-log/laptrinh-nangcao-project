package com.auction.server.dao.memory;

import com.auction.server.dao.AuctionDao;
import com.auction.server.domain.ManagedAuction;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryAuctionDao implements AuctionDao {
    private final Map<String, ManagedAuction> auctions = new ConcurrentHashMap<>();

    @Override
    public Optional<ManagedAuction> findById(String auctionId) {
        return Optional.ofNullable(auctions.get(auctionId));
    }

    @Override
    public List<ManagedAuction> findAll() {
        return auctions.values().stream()
                .sorted(Comparator.comparing(ManagedAuction::getCreatedAt).reversed())
                .toList();
    }

    @Override
    public List<ManagedAuction> findVisibleAuctions() {
        return auctions.values().stream()
                .sorted(Comparator.comparing(record -> record.getAuction().getEndTime()))
                .toList();
    }

    @Override
    public List<ManagedAuction> findBySellerId(String sellerId) {
        return auctions.values().stream()
                .filter(record -> record.getSeller().getId().equalsIgnoreCase(sellerId))
                .sorted(Comparator.comparing(ManagedAuction::getCreatedAt).reversed())
                .toList();
    }

    @Override
    public ManagedAuction save(ManagedAuction managedAuction) {
        auctions.put(managedAuction.getAuctionId(), managedAuction);
        return managedAuction;
    }

    @Override
    public void deleteById(String auctionId) {
        auctions.remove(auctionId);
    }
}
