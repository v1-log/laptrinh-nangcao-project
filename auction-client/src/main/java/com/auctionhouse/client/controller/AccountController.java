package com.auctionhouse.client.controller;

import com.auction.model.AuctionStatus;
import com.auction.shared.dto.AuctionView;
import com.auction.shared.dto.BidTransactionView;
import com.auction.shared.dto.UserView;
import com.auction.shared.enums.ResponseStatus;
import com.auction.shared.enums.UserRole;
import com.auction.shared.protocol.ServerResponse;
import com.auctionhouse.client.model.SessionModel;
import com.auctionhouse.client.service.AuctionClientService;
import com.auctionhouse.client.view.AppCoordinator;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public final class   AccountController {
    private static final NumberFormat CURRENCY = NumberFormat.getCurrencyInstance(Locale.US);

    @FXML private Label accountNameLabel;
    @FXML private Label roleLabel;
    @FXML private Label avatarLabel;
    @FXML private Label walletBalanceLabel;
    @FXML private Label accountSummaryLabel;
    @FXML private Label balanceCaptionLabel;
    @FXML private Label paymentCountLabel;
    @FXML private Label joinedCountLabel;
    @FXML private Label accountStatusLabel;
    @FXML private TextField depositAmountField;
    @FXML private ListView<AuctionView> payableAuctionListView;
    @FXML private ListView<AuctionView> participatedAuctionListView;
    @FXML private Label paymentSummaryLabel;
    @FXML private Label paymentItemLabel;
    @FXML private Label paymentAmountLabel;
    @FXML private Label paymentStateLabel;
    @FXML private Label paymentWinnerLabel;
    @FXML private Button payNowButton;
    @FXML private Label joinedSummaryLabel;
    @FXML private Label joinedTitleLabel;
    @FXML private Label joinedStatusLabel;
    @FXML private Label joinedPriceLabel;
    @FXML private Label joinedDetailLabel;

    private final SessionModel sessionModel = new SessionModel();

    private AppCoordinator coordinator;
    private AuctionClientService clientService;
    private UserView currentUser;

    public void init(AppCoordinator coordinator, AuctionClientService clientService, UserView currentUser) {
        this.coordinator = coordinator;
        this.clientService = clientService;
        this.currentUser = currentUser;
        configureView();
        clientService.setEventListener(this::handleEventResponse);
        loadAccountData();
    }

    @FXML
    private void goBack() {
        try {
            coordinator.showDashboard(currentUser);
        } catch (Exception exception) {
            accountStatusLabel.setText(exception.getMessage());
        }
    }

    @FXML
    private void refreshAccount() {
        accountStatusLabel.setText("Refreshing account data...");
        loadAccountData();
    }

    @FXML
    private void depositFunds() {
        String rawAmount = depositAmountField.getText() == null ? "" : depositAmountField.getText().trim();
        if (rawAmount.isBlank()) {
            accountStatusLabel.setText("Enter an amount to deposit.");
            return;
        }

        final double amount;
        try {
            amount = Double.parseDouble(rawAmount);
        } catch (NumberFormatException exception) {
            accountStatusLabel.setText("Deposit amount must be a valid number.");
            return;
        }

        if (amount <= 0) {
            accountStatusLabel.setText("Deposit amount must be greater than zero.");
            return;
        }

        accountStatusLabel.setText("Updating balance...");
        CompletableFuture.supplyAsync(() -> clientService.depositFunds(amount))
                .whenComplete((user, error) -> Platform.runLater(() -> {
                    if (error != null) {
                        accountStatusLabel.setText(extractMessage(error));
                        return;
                    }
                    currentUser = user;
                    depositAmountField.clear();
                    updateUserHeader();
                    updateWalletLabel();
                    updateSummaryCards();
                    accountStatusLabel.setText("Balance saved successfully.");
                }));
    }

    @FXML
    private void paySelectedAuction() {
        AuctionView selectedAuction = payableAuctionListView.getSelectionModel().getSelectedItem();
        if (selectedAuction == null) {
            accountStatusLabel.setText("Select an auction to pay.");
            return;
        }
        if (!isCurrentUserWinner(selectedAuction)) {
            accountStatusLabel.setText("Only the winning bidder can pay this auction.");
            return;
        }
        if (selectedAuction.getStatus() == AuctionStatus.PAID) {
            accountStatusLabel.setText("This auction has already been paid.");
            return;
        }

        payNowButton.setDisable(true);
        accountStatusLabel.setText("Submitting payment...");
        CompletableFuture.supplyAsync(() -> clientService.payAuction(selectedAuction.getAuctionId()))
                .whenComplete((auction, error) -> Platform.runLater(() -> {
                    payNowButton.setDisable(false);
                    if (error != null) {
                        accountStatusLabel.setText(extractMessage(error));
                        return;
                    }
                    accountStatusLabel.setText("Payment completed and saved.");
                    loadAccountData();
                }));
    }

    @FXML
    private void handleLogout() {
        accountStatusLabel.setText("Signing out...");
        CompletableFuture.runAsync(clientService::logout)
                .whenComplete((ignored, error) -> Platform.runLater(() -> {
                    if (error != null) {
                        accountStatusLabel.setText(extractMessage(error));
                        return;
                    }
                    try {
                        coordinator.showLogin();
                    } catch (Exception exception) {
                        accountStatusLabel.setText(exception.getMessage());
                    }
                }));
    }

    private void configureView() {
        updateUserHeader();
        updateWalletLabel();
        updateSummaryCards();
        accountStatusLabel.setText("Manage balance, payments, and joined auctions here.");

        payableAuctionListView.setPlaceholder(buildPlaceholder("No finished auctions are waiting for your payment."));
        participatedAuctionListView.setPlaceholder(buildPlaceholder("This account has not placed any bids yet."));

        payableAuctionListView.setCellFactory(listView -> new AuctionCell());
        participatedAuctionListView.setCellFactory(listView -> new AuctionCell());

        payableAuctionListView.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldValue, newValue) -> updatePaymentDetails(newValue));
        participatedAuctionListView.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldValue, newValue) -> updateJoinedDetails(newValue));

        updatePaymentDetails(null);
        updateJoinedDetails(null);
    }

    private void loadAccountData() {
        CompletableFuture.supplyAsync(() -> clientService.loadDashboard(currentUser.getId()))
                .whenComplete((data, error) -> Platform.runLater(() -> {
                    if (error != null) {
                        accountStatusLabel.setText(extractMessage(error));
                        return;
                    }

                    sessionModel.applyDashboard(data);
                    if (sessionModel.currentUserProperty().get() != null) {
                        currentUser = sessionModel.currentUserProperty().get();
                    }

                    updateUserHeader();
                    updateWalletLabel();
                    renderAuctionLists();
                    accountStatusLabel.setText("Account data refreshed.");
                }));
    }

    private void renderAuctionLists() {
        List<AuctionView> participatedAuctions = sessionModel.getLiveAuctions().stream()
                .filter(this::hasParticipated)
                .toList();
        List<AuctionView> payableAuctions = participatedAuctions.stream()
                .filter(this::isPaymentRelevant)
                .toList();

        participatedAuctionListView.getItems().setAll(participatedAuctions);
        payableAuctionListView.getItems().setAll(payableAuctions);

        joinedSummaryLabel.setText(participatedAuctions.size() + " auctions joined");
        paymentSummaryLabel.setText(payableAuctions.isEmpty()
                ? "No payment is pending for this account."
                : payableAuctions.size() + " auction(s) still need payment.");

        updateSummaryCards();

        if (!payableAuctions.isEmpty()) {
            payableAuctionListView.getSelectionModel().select(0);
        } else {
            updatePaymentDetails(null);
        }

        if (!participatedAuctions.isEmpty()) {
            participatedAuctionListView.getSelectionModel().select(0);
        } else {
            updateJoinedDetails(null);
        }
    }

    private void updateUserHeader() {
        String displayName = currentUser == null ? "User" : safeValue(currentUser.getName(), "User");
        accountNameLabel.setText(displayName);
        avatarLabel.setText(displayName.substring(0, 1).toUpperCase(Locale.ROOT));
        roleLabel.setText(buildRoleLabel(currentUser == null ? null : currentUser.getRole()));
        balanceCaptionLabel.setText("Persistent wallet balance");
    }

    private void updateWalletLabel() {
        walletBalanceLabel.setText(CURRENCY.format(currentUser == null ? 0.0d : currentUser.getBalance()));
    }

    private void updateSummaryCards() {
        int paymentCount = payableAuctionListView.getItems() == null ? 0 : payableAuctionListView.getItems().size();
        int joinedCount = participatedAuctionListView.getItems() == null ? 0 : participatedAuctionListView.getItems().size();
        accountSummaryLabel.setText("Persistent account overview");
        paymentCountLabel.setText(String.valueOf(paymentCount));
        joinedCountLabel.setText(String.valueOf(joinedCount));
    }

    private void updatePaymentDetails(AuctionView auction) {
        if (auction == null) {
            paymentItemLabel.setText("Select a finished auction from the payment list.");
            paymentAmountLabel.setText("Amount due: --");
            paymentStateLabel.setText("Payment status: No auction selected");
            paymentWinnerLabel.setText("Winner: --");
            payNowButton.setDisable(true);
            return;
        }

        paymentItemLabel.setText(auction.getItem().getName());
        paymentAmountLabel.setText("Amount due: " + CURRENCY.format(auction.getItem().getCurrentPrice()));
        paymentStateLabel.setText("Payment status: " + buildPaymentState(auction));
        paymentWinnerLabel.setText("Winner: " + safeValue(auction.getWinnerName(), "Unknown"));
        payNowButton.setDisable(auction.getStatus() == AuctionStatus.PAID || !isCurrentUserWinner(auction));
    }

    private void updateJoinedDetails(AuctionView auction) {
        if (auction == null) {
            joinedTitleLabel.setText("Select one joined auction.");
            joinedStatusLabel.setText("Status: --");
            joinedPriceLabel.setText("Current price: --");
            joinedDetailLabel.setText("Auction details and your participation summary will appear here.");
            return;
        }

        joinedTitleLabel.setText(auction.getItem().getName());
        joinedStatusLabel.setText("Status: " + auction.getStatus().name());
        joinedPriceLabel.setText("Current price: " + CURRENCY.format(auction.getItem().getCurrentPrice()));
        joinedDetailLabel.setText(buildJoinedDetail(auction));
    }

    private boolean hasParticipated(AuctionView auction) {
        if (auction == null || auction.getBidHistory() == null) {
            return false;
        }
        for (BidTransactionView bid : auction.getBidHistory()) {
            if (currentUser.getId().equals(bid.getBidderId())) {
                return true;
            }
        }
        return false;
    }

    private boolean isPaymentRelevant(AuctionView auction) {
        if (auction == null || !isCurrentUserWinner(auction)) {
            return false;
        }
        return auction.getStatus() == AuctionStatus.FINISHED || auction.getStatus() == AuctionStatus.PAID;
    }

    private boolean isCurrentUserWinner(AuctionView auction) {
        return auction != null
                && auction.getHighestBid() != null
                && currentUser.getId().equals(auction.getHighestBid().getBidderId());
    }

    private String buildPaymentState(AuctionView auction) {
        return auction.getStatus() == AuctionStatus.PAID
                ? "Paid and saved on server"
                : "Awaiting payment";
    }

    private String buildJoinedDetail(AuctionView auction) {
        String highestBidText = auction.getHighestBid() == null
                ? "No bids recorded"
                : auction.getHighestBid().getBidderName() + " at " + CURRENCY.format(auction.getHighestBid().getAmount());
        return safeValue(auction.getItem().getDescription(), "No description")
                + " | Top bid: " + highestBidText
                + " | Winner: " + safeValue(auction.getWinnerName(), "Unknown");
    }

    private String buildRoleLabel(UserRole role) {
        if (role == null) {
            return "Bidder account";
        }
        return switch (role) {
            case BIDDER -> "Bidder account";
            case SELLER -> "Seller account";
            case ADMIN -> "Administrator account";
        };
    }

    private Label buildPlaceholder(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-text-fill: #6c746f; -fx-font-size: 12px;");
        return label;
    }

    private String safeValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private void handleEventResponse(ServerResponse<?> response) {
        if (response.getStatus() != ResponseStatus.EVENT) {
            return;
        }
        Platform.runLater(this::loadAccountData);
    }

    private String extractMessage(Throwable throwable) {
        Throwable cause = throwable.getCause() == null ? throwable : throwable.getCause();
        return cause.getMessage() == null ? throwable.getMessage() : cause.getMessage();
    }

    private static final class AuctionCell extends ListCell<AuctionView> {
        private final Label titleLabel = new Label();
        private final Label metaLabel = new Label();
        private final VBox content = new VBox(titleLabel, metaLabel);

        private AuctionCell() {
            titleLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #22322d;");
            metaLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #66736d;");
            content.setSpacing(4);
            setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        }

        @Override
        protected void updateItem(AuctionView item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                setStyle("-fx-background-color: transparent;");
                return;
            }

            titleLabel.setText(item.getItem().getName());
            metaLabel.setText(item.getStatus().name() + " | " + CURRENCY.format(item.getItem().getCurrentPrice()));
            setGraphic(content);
            setStyle("-fx-background-color: #f6f3ee; -fx-background-radius: 14; "
                    + "-fx-border-color: #d7d1c7; -fx-border-radius: 14; -fx-padding: 10 12;");
        }
    }
}
