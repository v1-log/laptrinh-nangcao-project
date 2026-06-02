package com.auction.server.service;

import com.auction.model.Auction;
import com.auction.model.AuctionStatus;
import com.auction.model.Observer;
import com.auction.server.dao.AuctionDao;
import com.auction.server.mapper.AuctionViewMapper;
import com.auction.shared.dto.AuctionView;
import com.auction.shared.enums.AuctionEventType;
import com.auction.shared.protocol.ServerResponse;

final class AuctionObserverBridge implements Observer {
    private final String auctionId;
    private final AuctionDao auctionDao;
    private final AuctionViewMapper mapper;
    private final com.auction.server.event.AuctionEventPublisher eventPublisher;
    private final AuctionManager auctionManager = AuctionManager.getInstance();
    private AuctionStatus lastStatus;
    private Double lastHighestAmount;

    AuctionObserverBridge(
            String auctionId,
            Auction auction,
            AuctionDao auctionDao,
            AuctionViewMapper mapper,
            com.auction.server.event.AuctionEventPublisher eventPublisher) {
        this.auctionId = auctionId;
        this.auctionDao = auctionDao;
        this.mapper = mapper;
        this.eventPublisher = eventPublisher;
        this.lastStatus = auction.getStatus();
        this.lastHighestAmount = auction.getHighestBid() == null ? null : auction.getHighestBid().getAmount();
    }

    @Override
    public synchronized void update(Auction auction) {
        auctionDao.findById(auctionId).ifPresent(record -> {
            auctionDao.save(record);
            AuctionView view = mapper.toView(record);
            AuctionEventType eventType = resolveEventType(auction);
            String message = buildMessage(view, eventType);
            ServerResponse<AuctionView> response = ServerResponse.event(eventType, message, view);
            auctionManager.publishToAuction(auctionId, response);
            lastStatus = auction.getStatus();
            lastHighestAmount = auction.getHighestBid() == null ? null : auction.getHighestBid().getAmount();
        });
    }

    private AuctionEventType resolveEventType(Auction auction) {
        Double currentAmount = auction.getHighestBid() == null ? null : auction.getHighestBid().getAmount();
        if (lastStatus != auction.getStatus()) {
            return AuctionEventType.AUCTION_STATUS_CHANGED;
        }
        if (currentAmount != null && (lastHighestAmount == null || Double.compare(lastHighestAmount, currentAmount) != 0)) {
            return AuctionEventType.BID_PLACED;
        }
        return AuctionEventType.AUCTION_UPDATED;
    }

    private String buildMessage(AuctionView view, AuctionEventType eventType) {
        return switch (eventType) {
            case BID_PLACED -> "New highest bid on " + view.item().name();
            case AUCTION_STATUS_CHANGED -> "Auction status changed to " + view.status();
            case AUCTION_UPDATED -> "Auction updated: " + view.item().name();
            default -> "Auction event received";
        };
    }
}
