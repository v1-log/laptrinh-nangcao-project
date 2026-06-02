// tạo acution , place bid, close auction
package com.auction.server.service;

import com.auction.model.Admin;
import com.auction.model.Art;
import com.auction.model.Auction;
import com.auction.model.AuctionStatus;
import com.auction.model.Bid;
import com.auction.model.Bidder;
import com.auction.model.Clothing;
import com.auction.model.Electronics;
import com.auction.model.Item;
import com.auction.model.ItemFactory;
import com.auction.model.ItemType;
import com.auction.model.Seller;
import com.auction.model.User;
import com.auction.model.exception.AuctionClosedException;
import com.auction.model.exception.InvalidAuctionException;
import com.auction.model.exception.InvalidBidException;
import com.auction.model.exception.SellerOwnAuctionException;
import com.auction.model.exception.UnauthorizedActionException;
import com.auction.server.dao.AuctionDao;
import com.auction.server.dao.UserDao;
import com.auction.server.domain.ManagedAuction;
import com.auction.server.event.AuctionEventPublisher;
import com.auction.server.mapper.AuctionViewMapper;
import com.auction.shared.dto.AuctionView;
import com.auction.shared.dto.DashboardView;
import com.auction.shared.dto.UserView;
import com.auction.shared.enums.AuctionEventType;
import com.auction.shared.protocol.AuctionActionRequest;
import com.auction.shared.protocol.AutoBidRequest;
import com.auction.shared.protocol.BidRequest;
import com.auction.shared.protocol.CreateAuctionRequest;
import com.auction.shared.protocol.DepositFundsRequest;
import com.auction.shared.protocol.ServerResponse;
import com.auction.shared.protocol.UpdateAuctionRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

public final class AuctionService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuctionService.class);

    private final AuctionDao auctionDao;
    private final UserDao userDao;
    private final AuctionViewMapper mapper;
    private final AuctionEventPublisher eventPublisher;
    private final AuctionManager auctionManager = AuctionManager.getInstance();
    private final AtomicInteger auctionSequence = new AtomicInteger(1000);
    private final AtomicInteger itemSequence = new AtomicInteger(2000);

    public AuctionService(
            AuctionDao auctionDao,
            UserDao userDao,
            AuctionViewMapper mapper,
            AuctionEventPublisher eventPublisher) {
        this.auctionDao = auctionDao;
        this.userDao = userDao;
        this.mapper = mapper;
        this.eventPublisher = eventPublisher;
        this.auctionManager.configure(eventPublisher);
        auctionDao.findAll().forEach(record -> {
            attachObserver(record);
            auctionManager.registerAuction(record);
            syncSequences(record);
        });
    }

    public void seedUser(User user) {
        userDao.save(user);
    }

    public void seedAuction(Auction auction, Seller seller) {
        Auction seedAuction = normalizeSeedAuction(auction);
        ManagedAuction record = new ManagedAuction(seedAuction, seller, LocalDateTime.now());
        attachObserver(record);
        auctionDao.save(record);
        auctionManager.registerAuction(record);
        syncSequences(record);
    }

    public DashboardView loadDashboard(String userId) {
        User user = requireUser(userId);
        List<ManagedAuction> visibleAuctions = auctionDao.findVisibleAuctions();
        List<ManagedAuction> sellerAuctions = user instanceof Admin
                ? auctionDao.findAll()
                : auctionDao.findBySellerId(user.getId());
        return mapper.toDashboard(user, visibleAuctions, sellerAuctions);
    }

    public AuctionView loadAuction(String auctionId) {
        return mapper.toView(requireAuctionRecord(auctionId));
    }

    public AuctionView createAuction(CreateAuctionRequest request) {
        Seller seller = requireSeller(request.sellerId());
        validateCreateOrUpdate(request.startingPrice(), request.bidIncrement(), request.durationMinutes(), request.itemName());

        Item item = buildItem(
                request.itemType(),
                nextItemId(),
                request.itemName(),
                request.description(),
                request.startingPrice(),
                request.extraValue());
        LocalDateTime startTime = LocalDateTime.now().plusSeconds(1);
        Auction auction = new Auction(
                nextAuctionId(),
                item,
                startTime,
                startTime.plusMinutes(request.durationMinutes()),
                request.bidIncrement());
        ManagedAuction record = new ManagedAuction(auction, seller, LocalDateTime.now());
        attachObserver(record);
        auctionDao.save(record);
        auctionManager.registerAuction(record);

        AuctionView view = mapper.toView(record);
        publishGlobal(AuctionEventType.AUCTION_CREATED, "Auction created: " + item.getName(), view);
        LOGGER.info("Auction {} created by seller {}", view.auctionId(), seller.getId());
        return view;
    }

    public AuctionView updateAuction(UpdateAuctionRequest request) {
        Seller seller = requireSeller(request.sellerId());
        ManagedAuction record = requireAuctionRecord(request.auctionId());
        requireOwner(record, seller);
        if (record.getAuction().getStatus() != AuctionStatus.OPEN) {
            throw new AuctionClosedException(record.getAuctionId(), record.getAuction().getStatus(), "Only OPEN auctions can be updated.");
        }
        validateCreateOrUpdate(request.startingPrice(), request.bidIncrement(), request.durationMinutes(), request.itemName());
        record.getAuction().shutdownScheduler();

        Item newItem = buildItem(
                request.itemType(),
                record.getAuction().getItem().getId(),
                request.itemName(),
                request.description(),
                request.startingPrice(),
                request.extraValue());
        LocalDateTime startTime = LocalDateTime.now().plusSeconds(1);
        Auction replacement = new Auction(
                record.getAuctionId(),
                newItem,
                startTime,
                startTime.plusMinutes(request.durationMinutes()),
                request.bidIncrement());
        record.replaceAuction(replacement);
        attachObserver(record);
        auctionDao.save(record);
        auctionManager.registerAuction(record);

        AuctionView view = mapper.toView(record);
        publishGlobal(AuctionEventType.AUCTION_UPDATED, "Auction updated: " + newItem.getName(), view);
        LOGGER.info("Auction {} updated by seller {}", view.auctionId(), seller.getId());
        return view;
    }

    public void deleteAuction(AuctionActionRequest request) {
        ManagedAuction record = requireAuctionRecord(request.auctionId());
        User actor = requireUser(request.actorId());
        requireOwnerOrAdmin(record, actor);
        if (record.getAuction().getStatus() != AuctionStatus.OPEN) {
            throw new AuctionClosedException(record.getAuctionId(), record.getAuction().getStatus(), "Only OPEN auctions can be deleted.");
        }
        AuctionView deletedView = mapper.toView(record);
        record.getAuction().shutdownScheduler();
        auctionDao.deleteById(record.getAuctionId());
        auctionManager.removeAuction(record.getAuctionId());
        publishGlobal(AuctionEventType.AUCTION_DELETED, "Auction deleted: " + deletedView.item().name(), deletedView);
        LOGGER.info("Auction {} deleted by {}", record.getAuctionId(), actor.getId());
    }

    public AuctionView placeBid(BidRequest request) {
        User user = requireUser(request.bidderId());
        ManagedAuction record = requireAuctionRecord(request.auctionId());
        if (record.getSeller().getId().equals(user.getId())) {
            throw new SellerOwnAuctionException(record.getAuctionId(), user.getId());
        }
        Bidder bidder;
        if (user instanceof Bidder b) {
            bidder = b;
        } else if (user instanceof Seller s) {
            bidder = new Bidder(s.getId(), s.getName(), s.getPassword(), s.getBalance());
        } else {
            throw new UnauthorizedActionException("Only bidder or seller accounts can place bids.");
        }
        if (request.amount() <= 0) {
            throw new InvalidBidException(record.getAuction().getItem().getCurrentPrice(), request.amount());
        }

        ReentrantLock lock = auctionManager.lockForAuction(record.getAuctionId());
        lock.lock();
        try {
            if (record.getAuction().getStatus() != AuctionStatus.RUNNING) {
                throw new AuctionClosedException(record.getAuctionId(), record.getAuction().getStatus(), "Auction is not running for bidding");
            }
            record.getAuction().placeBid(new Bid(bidder, request.amount()));
            auctionManager.runAutoBids(record.getAuction());
            AuctionView view = mapper.toView(record);
            LOGGER.info("Bid accepted auction={} bidder={} amount={}", record.getAuctionId(), bidder.getId(), request.amount());
            return view;
        } finally {
            lock.unlock();
        }
    }

    public AuctionView setAutoBid(AutoBidRequest request) {
        User user = requireUser(request.bidderId());
        ManagedAuction record = requireAuctionRecord(request.auctionId());
        if (record.getSeller().getId().equals(user.getId())) {
            throw new SellerOwnAuctionException(record.getAuctionId(), user.getId());
        }
        Bidder bidder;
        if (user instanceof Bidder b) {
            bidder = b;
        } else if (user instanceof Seller s) {
            bidder = new Bidder(s.getId(), s.getName(), s.getPassword(), s.getBalance());
        } else {
            throw new UnauthorizedActionException("Only bidder or seller accounts can configure auto-bidding.");
        }
        if (request.maxBid() <= 0) {
            throw new InvalidBidException(record.getAuction().getMinimumNextBid(), request.maxBid());
        }
        if (request.increment() <= 0) {
            throw new InvalidBidException(record.getAuction().getMinimumNextBid(), request.increment());
        }

        ReentrantLock lock = auctionManager.lockForAuction(record.getAuctionId());
        lock.lock();
        try {
            Auction auction = record.getAuction();
            if (auction.getStatus() != AuctionStatus.RUNNING) {
                throw new AuctionClosedException(record.getAuctionId(), auction.getStatus(), "Auction is not running for bidding");
            }
            if (request.maxBid() < auction.getMinimumNextBid()) {
                throw new InvalidBidException(auction.getMinimumNextBid(), request.maxBid());
            }

            auctionManager.setAutoBid(record.getAuctionId(), bidder, request.maxBid(), request.increment());
            List<Bid> automaticBids = auctionManager.runAutoBids(auction);
            auctionDao.save(record);
            AuctionView view = mapper.toView(record);
            if (automaticBids.isEmpty()) {
                ServerResponse<AuctionView> response = ServerResponse.event(
                        AuctionEventType.AUCTION_UPDATED,
                        "Auto-bid armed for " + bidder.getName(),
                        view);
                auctionManager.publishToAuction(record.getAuctionId(), response);
            }
            LOGGER.info("Auto-bid configured auction={} bidder={} maxBid={} increment={} immediateBids={}",
                    record.getAuctionId(), bidder.getId(), request.maxBid(), request.increment(), automaticBids.size());
            return view;
        } finally {
            lock.unlock();
        }
    }

    public AuctionView startAuction(AuctionActionRequest request) {
        ManagedAuction record = requireAuctionRecord(request.auctionId());
        User actor = requireUser(request.actorId());
        requireOwnerOrAdmin(record, actor);
        if (record.getAuction().getStatus() != AuctionStatus.OPEN) {
            throw new AuctionClosedException(record.getAuctionId(), record.getAuction().getStatus(), "Only OPEN auctions can be started.");
        }
        record.getAuction().startAuction();
        AuctionView view = mapper.toView(record);
        LOGGER.info("Auction {} started by {}", record.getAuctionId(), actor.getId());
        return view;
    }

    public AuctionView finishAuction(AuctionActionRequest request) {
        ManagedAuction record = requireAuctionRecord(request.auctionId());
        User actor = requireUser(request.actorId());
        requireOwnerOrAdmin(record, actor);
        if (record.getAuction().getStatus() != AuctionStatus.RUNNING) {
            throw new AuctionClosedException(record.getAuctionId(), record.getAuction().getStatus(), "Only RUNNING auctions can be finished.");
        }
        record.getAuction().closeAuction();
        AuctionView view = mapper.toView(record);
        LOGGER.info("Auction {} finished by {}", record.getAuctionId(), actor.getId());
        return view;
    }

    public AuctionView markPaid(AuctionActionRequest request) {
        ManagedAuction record = requireAuctionRecord(request.auctionId());
        User actor = requireUser(request.actorId());
        requireAdmin(actor);
        if (record.getAuction().getStatus() != AuctionStatus.FINISHED) {
            throw new AuctionClosedException(record.getAuctionId(), record.getAuction().getStatus(), "Only FINISHED auctions can be marked paid.");
        }
        record.getAuction().markPaid();
        AuctionView view = mapper.toView(record);
        LOGGER.info("Auction {} marked paid by {}", record.getAuctionId(), actor.getId());
        return view;
    }

    public UserView depositFunds(DepositFundsRequest request) {
        User actor = requireUser(request.userId());
        if (request.amount() <= 0) {
            throw new IllegalArgumentException("Deposit amount must be greater than zero.");
        }
        actor.depositFunds(request.amount());
        userDao.save(actor);
        LOGGER.info("Balance topped up user={} amount={}", actor.getId(), request.amount());
        return mapper.toUserView(actor);
    }

    public AuctionView payAuction(AuctionActionRequest request) {
        User actor = requireUser(request.actorId());
        if (!(actor instanceof Bidder bidder)) {
            throw new UnauthorizedActionException("Only bidder accounts can pay for won auctions.");
        }

        ManagedAuction record = requireAuctionRecord(request.auctionId());
        Auction auction = record.getAuction();
        if (auction.getStatus() == AuctionStatus.PAID) {
            throw new AuctionClosedException(record.getAuctionId(), auction.getStatus(), "This auction has already been paid.");
        }
        if (auction.getStatus() != AuctionStatus.FINISHED) {
            throw new AuctionClosedException(record.getAuctionId(), auction.getStatus(), "Only FINISHED auctions can be paid.");
        }
        if (auction.getHighestBid() == null) {
            throw new AuctionClosedException(record.getAuctionId(), auction.getStatus(), "This auction has no winning bidder.");
        }
        if (!bidder.getId().equals(auction.getHighestBid().getBidder().getId())) {
            throw new UnauthorizedActionException("Only the winning bidder can pay for this auction.");
        }

        bidder.withdrawFunds(auction.getItem().getCurrentPrice());
        userDao.save(bidder);
        auction.markPaid();
        AuctionView view = mapper.toView(record);
        LOGGER.info("Auction {} paid by {}", record.getAuctionId(), bidder.getId());
        return view;
    }

    public AuctionView cancelAuction(AuctionActionRequest request) {
        ManagedAuction record = requireAuctionRecord(request.auctionId());
        User actor = requireUser(request.actorId());
        requireAdmin(actor);
        if (record.getAuction().getStatus() == AuctionStatus.PAID) {
            throw new AuctionClosedException(record.getAuctionId(), record.getAuction().getStatus(), "PAID auctions cannot be canceled.");
        }
        record.getAuction().cancelAuction();
        AuctionView view = mapper.toView(record);
        LOGGER.info("Auction {} canceled by {}", record.getAuctionId(), actor.getId());
        return view;
    }

    // Dung tat ca timer auction de server co the shutdown sach ma khong de lai thread nen.
    public void shutdown() {
        auctionDao.findAll().forEach(record -> record.getAuction().shutdownScheduler());
    }

    private void attachObserver(ManagedAuction record) {
        record.getAuction().addObserver(new AuctionObserverBridge(
                record.getAuctionId(),
                record.getAuction(),
                auctionDao,
                mapper,
                eventPublisher));
    }

    private void publishGlobal(AuctionEventType type, String message, AuctionView view) {
        ServerResponse<AuctionView> response = ServerResponse.event(type, message, view);
        auctionManager.publishGlobal(response);
    }

    private Item buildItem(String itemTypeValue, String itemId, String name, String description, double startingPrice, String extraValue) {
        ItemType itemType = parseItemType(itemTypeValue);
        return switch (itemType) {
            case ART -> ItemFactory.createItem(itemType, itemId, name, description, startingPrice, blankToDefault(extraValue, "Unknown Artist"));
            case ELECTRONICS -> ItemFactory.createItem(itemType, itemId, name, description, startingPrice, parseWarranty(extraValue));
            case CLOTHING -> ItemFactory.createItem(itemType, itemId, name, description, startingPrice, blankToDefault(extraValue, "M"));
        };
    }

    private ItemType parseItemType(String itemTypeValue) {
        if (itemTypeValue == null || itemTypeValue.isBlank()) {
            return ItemType.ELECTRONICS;
        }
        try {
            return ItemType.valueOf(itemTypeValue.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new InvalidAuctionException("Unsupported item type: " + itemTypeValue, "itemType");
        }
    }

    private int parseWarranty(String extraValue) {
        if (extraValue == null || extraValue.isBlank()) {
            return 12;
        }
        try {
            return Integer.parseInt(extraValue.trim());
        } catch (NumberFormatException exception) {
            throw new InvalidAuctionException("Warranty must be a whole number of months.", "extraValue");
        }
    }

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private void validateCreateOrUpdate(double startingPrice, double bidIncrement, int durationMinutes, String itemName) {
        if (itemName == null || itemName.isBlank()) {
            throw new InvalidAuctionException("Item name is required.", "itemName");
        }
        if (startingPrice <= 0) {
            throw new InvalidAuctionException("Starting price must be greater than zero.", "startingPrice");
        }
        if (bidIncrement <= 0) {
            throw new InvalidAuctionException("Bid increment must be greater than zero.", "bidIncrement");
        }
        if (durationMinutes < 1) {
            throw new InvalidAuctionException("Duration must be at least 1 minute.", "durationMinutes");
        }
    }

    private ManagedAuction requireAuctionRecord(String auctionId) {
        return auctionDao.findById(auctionId)
                .orElseThrow(() -> new IllegalArgumentException("Auction not found: " + auctionId));
    }

    private User requireUser(String userId) {
        return userDao.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown user: " + userId));
    }

    private Seller requireSeller(String sellerId) {
        User user = requireUser(sellerId);
        if (!(user instanceof Seller seller)) {
            throw new UnauthorizedActionException("Only seller accounts can manage auctions.");
        }
        return seller;
    }

    private void requireOwner(ManagedAuction record, Seller seller) {
        if (!record.getSeller().getId().equals(seller.getId())) {
            throw new UnauthorizedActionException("You can only manage your own auctions.");
        }
    }

    private void requireOwnerOrAdmin(ManagedAuction record, User actor) {
        if (actor instanceof Admin) {
            return;
        }
        if (!(actor instanceof Seller seller)) {
            throw new UnauthorizedActionException("Only the seller owner or admin can perform this action.");
        }
        requireOwner(record, seller);
    }

    private void requireAdmin(User actor) {
        if (!(actor instanceof Admin)) {
            throw new UnauthorizedActionException("Only admin accounts can perform this action.");
        }
    }

    private Auction normalizeSeedAuction(Auction auction) {
        if (auction.getStatus() != AuctionStatus.RUNNING || !auction.getBidHistory().isEmpty()) {
            return auction;
        }
        return Auction.restore(
                auction.getAuctionId(),
                auction.getItem(),
                LocalDateTime.now().plusSeconds(1),
                auction.getEndTime(),
                auction.getBidIncrement(),
                AuctionStatus.OPEN,
                null,
                List.of(),
                auction.getItem().getCurrentPrice());
    }

    private void syncSequences(ManagedAuction record) {
        auctionSequence.set(Math.max(auctionSequence.get(), extractNumericSuffix(record.getAuctionId(), 1000)));
        itemSequence.set(Math.max(itemSequence.get(), extractNumericSuffix(record.getAuction().getItem().getId(), 2000)));
    }

    private int extractNumericSuffix(String id, int fallback) {
        if (id == null || id.length() < 2) {
            return fallback;
        }
        try {
            return Integer.parseInt(id.replaceAll("\\D+", ""));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private String nextAuctionId() {
        return "A" + auctionSequence.incrementAndGet();
    }

    private String nextItemId() {
        return "I" + itemSequence.incrementAndGet();
    }
}
