package com.auction.server.mapper;

import com.auction.model.Admin;
import com.auction.model.Art;
import com.auction.model.Auction;
import com.auction.model.Bid;
import com.auction.model.BidTransaction;
import com.auction.model.Bidder;
import com.auction.model.Clothing;
import com.auction.model.Electronics;
import com.auction.model.Item;
import com.auction.model.ItemType;
import com.auction.model.Seller;
import com.auction.model.User;
import com.auction.server.domain.ManagedAuction;
import com.auction.shared.dto.AuctionMetrics;
import com.auction.shared.dto.AuctionView;
import com.auction.shared.dto.BidTransactionView;
import com.auction.shared.dto.BidView;
import com.auction.shared.dto.DashboardView;
import com.auction.shared.dto.ItemView;
import com.auction.shared.dto.UserView;
import com.auction.shared.enums.UserRole;

import java.util.List;

public final class AuctionViewMapper {

    public DashboardView toDashboard(User currentUser, List<ManagedAuction> visibleAuctions, List<ManagedAuction> sellerAuctions) {
        int totalBids = visibleAuctions.stream()
                .mapToInt(record -> record.getAuction().getBidHistory().size())
                .sum();
        return new DashboardView(
                toUserView(currentUser),
                visibleAuctions.stream().map(this::toView).toList(),
                sellerAuctions.stream().map(this::toView).toList(),
                new AuctionMetrics(visibleAuctions.size(), sellerAuctions.size(), totalBids)
        );
    }

    public AuctionView toView(ManagedAuction managedAuction) {
        Auction auction = managedAuction.getAuction();
        return new AuctionView(
                auction.getAuctionId(),
                managedAuction.getSeller().getId(),
                managedAuction.getSeller().getName(),
                auction.getStatus(),
                auction.getStartTime(),
                auction.getEndTime(),
                auction.getBidIncrement(),
                auction.getMinimumNextBid(),
                toItemView(auction.getItem()),
                toBidView(auction.getHighestBid()),
                auction.getBidHistory().stream().map(this::toBidTransactionView).toList(),
                resolveWinnerName(auction)
        );
    }

    public UserView toUserView(User user) {
        return new UserView(user.getId(), user.getName(), resolveRole(user), user.getBalance());
    }

    private ItemView toItemView(Item item) {
        String type = formatItemType(item.getItemType());
        String detail;
        if (item instanceof Art art) {
            detail = "Artist: " + art.getArtist();
        } else if (item instanceof Electronics electronics) {
            detail = "Warranty: " + electronics.getWarrantyMonths() + " months";
        } else if (item instanceof Clothing clothing) {
            detail = "Size: " + clothing.getSizeLabel();
        } else {
            detail = "General item";
        }
        return new ItemView(
                item.getId(),
                item.getName(),
                item.getDescription(),
                item.getStartingPrice(),
                item.getCurrentPrice(),
                type,
                detail
        );
    }

    private String formatItemType(ItemType itemType) {
        String lowerCase = itemType.name().toLowerCase();
        return Character.toUpperCase(lowerCase.charAt(0)) + lowerCase.substring(1);
    }

    private BidView toBidView(Bid bid) {
        if (bid == null) {
            return null;
        }
        return new BidView(
                bid.getBidder().getId(),
                bid.getBidder().getName(),
                bid.getAmount(),
                bid.getTimestamp()
        );
    }

    private BidTransactionView toBidTransactionView(BidTransaction transaction) {
        return new BidTransactionView(
                transaction.getBid().getBidder().getId(),
                transaction.getBidderName(),
                transaction.getAmount(),
                transaction.getTransactionTime()
        );
    }

    private String resolveWinnerName(Auction auction) {
        if ((auction.getStatus() == com.auction.model.AuctionStatus.FINISHED
                || auction.getStatus() == com.auction.model.AuctionStatus.PAID)
                && auction.getHighestBid() != null) {
            return auction.getHighestBid().getBidder().getName();
        }
        return "No winner yet";
    }

    private UserRole resolveRole(User user) {
        if (user instanceof Bidder) {
            return UserRole.BIDDER;
        }
        if (user instanceof Seller) {
            return UserRole.SELLER;
        }
        if (user instanceof Admin) {
            return UserRole.ADMIN;
        }
        throw new IllegalArgumentException("Unknown user role: " + user.getClass().getSimpleName());
    }
}
