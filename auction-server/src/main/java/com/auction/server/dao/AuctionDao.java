package com.auction.server.dao;

import com.auction.server.domain.ManagedAuction;

import java.util.List;
import java.util.Optional;

public interface AuctionDao {
    Optional<ManagedAuction> findById(String auctionId);

    List<ManagedAuction> findAll();

    List<ManagedAuction> findVisibleAuctions();

    List<ManagedAuction> findBySellerId(String sellerId);

    ManagedAuction save(ManagedAuction managedAuction);

    void deleteById(String auctionId);
}
