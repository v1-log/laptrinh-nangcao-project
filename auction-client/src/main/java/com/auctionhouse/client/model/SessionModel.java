package com.auctionhouse.client.model;


import com.auction.shared.dto.AuctionMetrics;
import com.auction.shared.dto.AuctionView;
import com.auction.shared.dto.BidTransactionView;
import com.auction.shared.dto.DashboardView;
import com.auction.shared.dto.UserView;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.List;


public final class SessionModel {
    private final ObjectProperty<UserView> currentUser = new SimpleObjectProperty<UserView>();
    private final ObjectProperty<AuctionView> selectedAuction = new SimpleObjectProperty<AuctionView>();
    private final ObjectProperty<AuctionMetrics> metrics = new SimpleObjectProperty<AuctionMetrics>();
    private final ObservableList<AuctionView> liveAuctions = FXCollections.observableArrayList();
    private final ObservableList<AuctionView> sellerAuctions = FXCollections.observableArrayList();
    private final ObservableList<BidTransactionView> bidHistory = FXCollections.observableArrayList();

    public void applyDashboard(DashboardView data) {
        if (data == null) {
            clearDashboard();
            return;
        }

        currentUser.setValue(data.currentUser());
        replaceAuctions(liveAuctions, data.visibleAuctions());
        replaceAuctions(sellerAuctions, data.sellerAuctions());
        metrics.setValue(data.metrics());
        refreshSelection();

    }

    public void selectAuction(AuctionView auction) {
        selectedAuction.setValue(auction);
        if (auction == null) {
            bidHistory.clear();
            return;
        }
        replaceBidHistory(auction.getBidHistory());
    }

    public void clearDashboard() {
        currentUser.setValue(null);
        metrics.setValue(null);
        liveAuctions.clear();
        sellerAuctions.clear();
        selectAuction(null);
    }

    private void replaceAuctions(ObservableList<AuctionView> target, List<AuctionView> source) {
        target.clear();
        if (source != null) {
            target.addAll(source);
        }
    }

    private void replaceBidHistory(List<BidTransactionView> source) {
        bidHistory.clear();
        if (source != null) {
            bidHistory.addAll(source);
        }
    }

    private void refreshSelection() {
        AuctionView currentSelection = selectedAuction.get();
        if (currentSelection == null) {
            bidHistory.clear();
            return;
        }

        AuctionView refreshedSelection = findAuction(currentSelection.getAuctionId());
        selectAuction(refreshedSelection);
    }

    private AuctionView findAuction(String auctionId) {
        if (auctionId == null || auctionId.isBlank()) {
            return null;
        }

        for (AuctionView auction : liveAuctions) {
            if (auctionId.equals(auction.getAuctionId())) {
                return auction;
            }
        }
        for (AuctionView auction : sellerAuctions) {
            if (auctionId.equals(auction.getAuctionId())) {
                return auction;
            }
        }
        return null;
    }

    public ObjectProperty<UserView> currentUserProperty() {
        return currentUser;
    }

    public ObjectProperty<AuctionView> selectedAuctionProperty() {
        return selectedAuction;
    }

    public ObjectProperty<AuctionMetrics> metricsProperty() {
        return metrics;
    }

    public ObservableList<AuctionView> getLiveAuctions() {
        return liveAuctions;
    }

    public ObservableList<AuctionView> getSellerAuctions() {
        return sellerAuctions;
    }

    public ObservableList<BidTransactionView> getBidHistory() {
        return bidHistory;
    }
}

