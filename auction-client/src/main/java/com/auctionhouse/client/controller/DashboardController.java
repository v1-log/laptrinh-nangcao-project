package com.auctionhouse.client.controller;

import com.auction.model.AuctionStatus;
import com.auction.shared.dto.AuctionMetrics;
import com.auction.shared.dto.AuctionView;
import com.auction.shared.dto.UserView;
import com.auction.shared.enums.ResponseStatus;
import com.auction.shared.enums.UserRole;
import com.auction.shared.protocol.AuctionActionRequest;
import com.auction.shared.protocol.ServerResponse;
import com.auctionhouse.client.model.SessionModel;
import com.auctionhouse.client.service.AuctionClientService;
import com.auctionhouse.client.view.AppCoordinator;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class DashboardController {
    private static final NumberFormat PRICE_FORMAT = NumberFormat.getCurrencyInstance(Locale.US);
    private static final DateTimeFormatter FINISHED_TIME_FORMAT =
            DateTimeFormatter.ofPattern("MMM dd, HH:mm", Locale.US);
    private static final String[] CARD_BANNERS = {
            "#d9e6f5",
            "#f2dce6",
            "#e8f0da",
            "#f6e8d5"
    };

    @FXML private Label currentUserLabel;
    @FXML private Label roleBadgeLabel;
    @FXML private Label avatarLabel;
    @FXML private Label summaryLabel;
    @FXML private Label actionStatusLabel;
    @FXML private TilePane auctionTilePane;
    @FXML private Button sellerBtn;
    @FXML private Button logoutButton;
    @FXML private Button allFilterButton;
    @FXML private Button runningFilterButton;
    @FXML private Button openFilterButton;
    @FXML private Button finishedFilterButton;

    private final SessionModel sessionModel = new SessionModel();
    private final Map<String, Label> countdownLabels = new HashMap<>();

    private AppCoordinator coordinator;
    private AuctionClientService clientService;
    private UserView currentUser;
    private DashboardFilter activeFilter = DashboardFilter.ALL;
    private Timeline countdownTimeline;

    public void init(AppCoordinator coordinator, AuctionClientService clientService, UserView currentUser) {
        this.coordinator = coordinator;
        this.clientService = clientService;
        this.currentUser = currentUser;
        configureView();
        clientService.setEventListener(this::handleEventResponse);
        loadDashboard();
    }

    @FXML
    private void refreshDashboard() {
        actionStatusLabel.setText("Refreshing auctions...");
        loadDashboard();
    }

    @FXML
    private void handleLogout() {
        logoutButton.setDisable(true);
        actionStatusLabel.setText("Signing out...");
        stopCountdownTicker();
        CompletableFuture.runAsync(clientService::logout)
                .whenComplete((ignored, error) -> Platform.runLater(() -> {
                    logoutButton.setDisable(false);
                    if (error != null) {
                        actionStatusLabel.setText(extractMessage(error));
                        startCountdownTicker();
                        return;
                    }
                    try {
                        coordinator.showLogin();
                    } catch (Exception exception) {
                        actionStatusLabel.setText(exception.getMessage());
                    }
                }));
    }

    @FXML
    private void goToSeller() {
        stopCountdownTicker();
        try {
            coordinator.showSeller(currentUser);
        } catch (Exception exception) {
            actionStatusLabel.setText(exception.getMessage());
            startCountdownTicker();
        }
    }

    @FXML
    private void openAccountView() {
        stopCountdownTicker();
        try {
            coordinator.showAccount(currentUser);
        } catch (Exception exception) {
            actionStatusLabel.setText(exception.getMessage());
            startCountdownTicker();
        }
    }

    @FXML
    private void showAllAuctions() {
        setFilter(DashboardFilter.ALL);
    }

    @FXML
    private void showRunningAuctions() {
        setFilter(DashboardFilter.RUNNING);
    }

    @FXML
    private void showOpenAuctions() {
        setFilter(DashboardFilter.OPEN);
    }

    @FXML
    private void showFinishedAuctions() {
        setFilter(DashboardFilter.FINISHED);
    }

    private void configureView() {
        updateUserHeader(currentUser);
        actionStatusLabel.setText("Select an auction to view details.");
        updateFilterButtons();
        startCountdownTicker();
    }

    private void loadDashboard() {
        CompletableFuture.supplyAsync(() -> clientService.loadDashboard(currentUser.getId()))
                .whenComplete((data, error) -> Platform.runLater(() -> {
                    if (error != null) {
                        actionStatusLabel.setText(extractMessage(error));
                        return;
                    }
                    sessionModel.applyDashboard(data);
                    currentUser = sessionModel.currentUserProperty().get() == null
                            ? currentUser
                            : sessionModel.currentUserProperty().get();
                    updateUserHeader(currentUser);
                    renderAuctions();
                    actionStatusLabel.setText("Select an auction to view details.");
                }));
    }

    private void renderAuctions() {
        List<AuctionView> visibleAuctions = sessionModel.getLiveAuctions().stream()
                .filter(this::matchesActiveFilter)
                .toList();

        auctionTilePane.getChildren().clear();
        countdownLabels.clear();

        if (visibleAuctions.isEmpty()) {
            auctionTilePane.getChildren().add(buildEmptyState());
            updateSummary(visibleAuctions);
            return;
        }

        for (int index = 0; index < visibleAuctions.size(); index++) {
            auctionTilePane.getChildren().add(buildAuctionCard(visibleAuctions.get(index), index));
        }

        updateSummary(visibleAuctions);
        refreshCountdownLabels();
    }

    private VBox buildAuctionCard(AuctionView auction, int index) {
        VBox card = new VBox();
        card.setSpacing(0);
        card.setPrefWidth(360);
        card.setMaxWidth(Double.MAX_VALUE);
        card.setStyle("-fx-background-color: #2c2a27; -fx-background-radius: 18; "
                + "-fx-border-color: #55504a; -fx-border-radius: 18;");

        StackPane banner = new StackPane();
        banner.setMinHeight(104);
        banner.setPrefHeight(104);
        banner.setAlignment(Pos.CENTER);
        banner.setStyle("-fx-background-color: " + CARD_BANNERS[index % CARD_BANNERS.length]
                + "; -fx-background-radius: 18 18 0 0;");

        Label iconLabel = new Label(resolveItemIcon(auction));
        iconLabel.setStyle("-fx-font-size: 30px;");
        banner.getChildren().add(iconLabel);

        VBox content = new VBox();
        content.setSpacing(10);
        content.setStyle("-fx-padding: 14 14 12 14;");

        Label titleLabel = new Label(safeValue(auction.getItem().getName(), "Auction item"));
        titleLabel.setWrapText(true);
        titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #f5f3ef;");

        Label priceCaptionLabel = new Label("Current price");
        priceCaptionLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #b9b4ad;");

        Label priceLabel = new Label(formatPrice(auction.getItem().getCurrentPrice()));
        priceLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #fbf8f3;");

        HBox metaRow = new HBox();
        metaRow.setAlignment(Pos.CENTER_LEFT);
        metaRow.setSpacing(8);

        Label statusBadge = new Label(buildStatusText(auction));
        statusBadge.setStyle(buildStatusStyle(auction));

        Label typeLabel = new Label(safeValue(auction.getItem().getItemType(), ""));
        typeLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #a29c95;");
        metaRow.getChildren().add(statusBadge);
        if (!typeLabel.getText().isBlank()) {
            metaRow.getChildren().add(typeLabel);
        }

        Label countdownLabel = new Label(buildCountdownLabel(auction));
        countdownLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #e2a72f;");
        countdownLabels.put(auction.getAuctionId(), countdownLabel);

        Button actionButton = buildActionButton(auction);

        content.getChildren().addAll(titleLabel, priceCaptionLabel, priceLabel, metaRow, countdownLabel, actionButton);
        card.getChildren().addAll(banner, content);
        card.setOnMouseClicked(event -> openAuction(auction));
        return card;
    }

    private VBox buildEmptyState() {
        VBox emptyBox = new VBox();
        emptyBox.setAlignment(Pos.CENTER);
        emptyBox.setSpacing(10);
        emptyBox.setPrefWidth(740);
        emptyBox.setMinHeight(220);
        emptyBox.setStyle("-fx-background-color: #2c2a27; -fx-background-radius: 18; "
                + "-fx-border-color: #55504a; -fx-border-radius: 18; -fx-padding: 24;");

        Label title = new Label("No auctions match this filter");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #f5f3ef;");

        Label description = new Label("Switch filters or refresh the dashboard to load the latest auctions.");
        description.setWrapText(true);
        description.setStyle("-fx-font-size: 12px; -fx-text-fill: #b9b4ad;");

        emptyBox.getChildren().addAll(title, description);
        return emptyBox;
    }

    private void openAuction(AuctionView auction) {
        stopCountdownTicker();
        try {
            actionStatusLabel.setText("Opening auction details...");
            coordinator.showAuctionDetail(currentUser, auction);
        } catch (Exception exception) {
            actionStatusLabel.setText(exception.getMessage());
            startCountdownTicker();
        }
    }

    private void updateSummary(List<AuctionView> visibleAuctions) {
        AuctionMetrics metrics = sessionModel.metricsProperty().get();
        int totalBids = metrics == null ? 0 : metrics.totalBidCount();
        summaryLabel.setText(visibleAuctions.size() + " auctions shown | " + totalBids + " bids");
    }

    private void handleEventResponse(ServerResponse<?> response) {
        if (response.getStatus() != ResponseStatus.EVENT) {
            return;
        }
        Platform.runLater(this::loadDashboard);
    }

    private void updateUserHeader(UserView user) {
        String displayName = user == null ? "Guest" : safeValue(user.getName(), "Guest");
        currentUserLabel.setText("Hello, " + displayName);
        avatarLabel.setText(displayName.substring(0, 1).toUpperCase(Locale.ROOT));

        UserRole role = user == null || user.getRole() == null ? UserRole.BIDDER : user.getRole();
        roleBadgeLabel.setText(buildRoleLabel(role));

        boolean canManageAuctions = role == UserRole.SELLER || role == UserRole.ADMIN;
        sellerBtn.setVisible(canManageAuctions);
        sellerBtn.setManaged(canManageAuctions);
    }

    private String buildRoleLabel(UserRole role) {
        return switch (role) {
            case BIDDER -> "Bidder";
            case SELLER -> "Seller";
            case ADMIN -> "Administrator";
        };
    }

    private boolean matchesActiveFilter(AuctionView auction) {
        AuctionStatus status = auction.getStatus();
        return switch (activeFilter) {
            case ALL -> true;
            case RUNNING -> status == AuctionStatus.RUNNING;
            case OPEN -> status == AuctionStatus.OPEN;
            case FINISHED -> status == AuctionStatus.FINISHED
                    || status == AuctionStatus.PAID
                    || status == AuctionStatus.CANCELED;
        };
    }

    private void setFilter(DashboardFilter filter) {
        activeFilter = filter;
        updateFilterButtons();
        renderAuctions();
    }

    private void updateFilterButtons() {
        applyFilterButtonStyle(allFilterButton, activeFilter == DashboardFilter.ALL);
        applyFilterButtonStyle(runningFilterButton, activeFilter == DashboardFilter.RUNNING);
        applyFilterButtonStyle(openFilterButton, activeFilter == DashboardFilter.OPEN);
        applyFilterButtonStyle(finishedFilterButton, activeFilter == DashboardFilter.FINISHED);
    }

    private void applyFilterButtonStyle(Button button, boolean active) {
        if (active) {
            button.setStyle("-fx-background-color: #ece7ff; -fx-text-fill: #5d4ccf; "
                    + "-fx-font-size: 13px; -fx-font-weight: bold; -fx-background-radius: 999; "
                    + "-fx-border-radius: 999; -fx-padding: 8 16; -fx-cursor: hand;");
            return;
        }
        button.setStyle("-fx-background-color: transparent; -fx-text-fill: #c9c3bb; "
                + "-fx-font-size: 13px; -fx-font-weight: bold; -fx-border-color: #6b655f; "
                + "-fx-border-width: 1; -fx-border-radius: 999; -fx-background-radius: 999; "
                + "-fx-padding: 8 16; -fx-cursor: hand;");
    }

    private void startCountdownTicker() {
        stopCountdownTicker();
        countdownTimeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> refreshCountdownLabels()));
        countdownTimeline.setCycleCount(Timeline.INDEFINITE);
        countdownTimeline.play();
    }

    private void stopCountdownTicker() {
        if (countdownTimeline != null) {
            countdownTimeline.stop();
            countdownTimeline = null;
        }
    }

    private void refreshCountdownLabels() {
        countdownLabels.forEach((auctionId, label) -> {
            AuctionView auction = findAuction(auctionId);
            if (auction != null) {
                label.setText(buildCountdownLabel(auction));
            }
        });
    }

    private AuctionView findAuction(String auctionId) {
        return sessionModel.getLiveAuctions().stream()
                .filter(auction -> auctionId.equals(auction.getAuctionId()))
                .findFirst()
                .orElse(null);
    }

    private String buildCountdownLabel(AuctionView auction) {
        AuctionStatus status = auction.getStatus();
        if (status == AuctionStatus.OPEN) {
            if (canStartAuction(auction)) {
                return "Ready to start";
            }
            return "Waiting for seller to start";
        }
        if (status == AuctionStatus.RUNNING) {
            long totalSeconds = java.time.Duration.between(LocalDateTime.now(), auction.getEndTime()).getSeconds();
            long normalizedSeconds = Math.max(totalSeconds, 0);
            long hours = normalizedSeconds / 3600;
            long minutes = (normalizedSeconds % 3600) / 60;
            long seconds = normalizedSeconds % 60;
            return String.format("Ends in: %02d:%02d:%02d", hours, minutes, seconds);
        }
        if (status == AuctionStatus.FINISHED) {
            return "Awaiting payment or cancel decision";
        }
        if (status == AuctionStatus.PAID) {
            return "Payment completed";
        }
        if (status == AuctionStatus.CANCELED) {
            return "Auction canceled";
        }
        return "Ended at: " + auction.getEndTime().format(FINISHED_TIME_FORMAT);
    }

    private String buildStatusText(AuctionView auction) {
        return switch (auction.getStatus()) {
            case RUNNING -> "RUNNING";
            case OPEN -> "OPEN";
            case FINISHED -> "FINISHED";
            case PAID -> "Paid";
            case CANCELED -> "Canceled";
        };
    }

    private String buildStatusStyle(AuctionView auction) {
        return switch (auction.getStatus()) {
            case RUNNING -> "-fx-background-color: #fde6d8; -fx-text-fill: #b76032; "
                    + "-fx-font-size: 11px; -fx-font-weight: bold; -fx-background-radius: 999; -fx-padding: 4 10;";
            case OPEN -> "-fx-background-color: #d8f4ec; -fx-text-fill: #25856d; "
                    + "-fx-font-size: 11px; -fx-font-weight: bold; -fx-background-radius: 999; -fx-padding: 4 10;";
            case FINISHED -> "-fx-background-color: #e7e1dc; -fx-text-fill: #5f5650; "
                    + "-fx-font-size: 11px; -fx-font-weight: bold; -fx-background-radius: 999; -fx-padding: 4 10;";
            case PAID -> "-fx-background-color: #d9f1dd; -fx-text-fill: #2f7a3d; "
                    + "-fx-font-size: 11px; -fx-font-weight: bold; -fx-background-radius: 999; -fx-padding: 4 10;";
            case CANCELED -> "-fx-background-color: #f6d9d9; -fx-text-fill: #9a4343; "
                    + "-fx-font-size: 11px; -fx-font-weight: bold; -fx-background-radius: 999; -fx-padding: 4 10;";
        };
    }

    private String resolveItemIcon(AuctionView auction) {
        String name = safeValue(auction.getItem().getName(), "").toLowerCase(Locale.ROOT);
        if (name.contains("laptop")) {
            return "💻";
        }
        if (name.contains("camera")) {
            return "📷";
        }
        if (name.contains("phone") || name.contains("iphone")) {
            return "📱";
        }
        if (name.contains("headphone") || name.contains("bose")) {
            return "🎧";
        }
        if (name.contains("hoodie") || name.contains("shirt") || name.contains("cloth")) {
            return "👕";
        }
        if ("art".equalsIgnoreCase(safeValue(auction.getItem().getItemType(), ""))) {
            return "🎨";
        }
        return "📦";
    }

    private Button buildActionButton(AuctionView auction) {
        boolean ownedByCurrentSeller = canStartAuction(auction);
        Button actionButton = new Button(ownedByCurrentSeller ? "Start auction" : "Join auction");
        actionButton.setMaxWidth(Double.MAX_VALUE);
        actionButton.setStyle(ownedByCurrentSeller
                ? "-fx-background-color: #22362b; -fx-text-fill: #5dcaa5; "
                + "-fx-font-size: 14px; -fx-font-weight: bold; -fx-border-color: #5dcaa5; "
                + "-fx-border-width: 1; -fx-border-radius: 12; -fx-background-radius: 12; "
                + "-fx-padding: 10 0; -fx-cursor: hand;"
                : "-fx-background-color: transparent; -fx-text-fill: #f7f2eb; "
                + "-fx-font-size: 14px; -fx-font-weight: bold; -fx-border-color: #6e6760; "
                + "-fx-border-width: 1; -fx-border-radius: 12; -fx-background-radius: 12; "
                + "-fx-padding: 10 0; -fx-cursor: hand;");
        actionButton.setOnAction(event -> {
            event.consume();
            if (ownedByCurrentSeller) {
                startOwnedAuction(auction);
                return;
            }
            openAuction(auction);
        });
        return actionButton;
    }

    private void startOwnedAuction(AuctionView auction) {
        if (!canStartAuction(auction)) {
            actionStatusLabel.setText("Only the seller owner can start this auction.");
            return;
        }
        actionStatusLabel.setText("Starting auction...");
        CompletableFuture.supplyAsync(() -> clientService.startAuction(
                        new AuctionActionRequest(auction.getAuctionId(), currentUser.getId())))
                .whenComplete((updatedAuction, err) -> Platform.runLater(() -> {
                    if (err != null) {
                        actionStatusLabel.setText(extractMessage(err));
                        return;
                    }
                    actionStatusLabel.setText("Auction is now RUNNING.");
                    loadDashboard();
                }));
    }

    private boolean canStartAuction(AuctionView auction) {
        return currentUser != null
                && currentUser.getRole() == UserRole.SELLER
                && auction != null
                && auction.getStatus() == AuctionStatus.OPEN
                && currentUser.getId().equals(auction.getSellerId());
    }

    private String formatPrice(double amount) {
        return PRICE_FORMAT.format(amount);
    }

    private String safeValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String extractMessage(Throwable throwable) {
        Throwable cause = throwable.getCause() == null ? throwable : throwable.getCause();
        return cause.getMessage() == null ? throwable.getMessage() : cause.getMessage();
    }

    private enum DashboardFilter {
        ALL,
        RUNNING,
        OPEN,
        FINISHED
    }
}
