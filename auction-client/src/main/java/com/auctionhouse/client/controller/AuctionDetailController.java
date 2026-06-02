package com.auctionhouse.client.controller;

import com.auction.model.AuctionStatus;
import com.auction.shared.dto.AuctionView;
import com.auction.shared.dto.BidTransactionView;
import com.auction.shared.dto.UserView;
import com.auction.shared.enums.UserRole;
import com.auction.shared.protocol.AutoBidRequest;
import com.auction.shared.protocol.BidRequest;
import com.auction.shared.protocol.ServerResponse;
import com.auctionhouse.client.model.SessionModel;
import com.auctionhouse.client.service.AuctionClientService;
import com.auctionhouse.client.view.AppCoordinator;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

import java.text.NumberFormat;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public final class AuctionDetailController {
    private static final NumberFormat CURRENCY = NumberFormat.getCurrencyInstance(Locale.US);
    private static final DateTimeFormatter DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("MMM dd, HH:mm", Locale.US);

    @FXML private Label titleLabel;
    @FXML private Label sellerLabel;
    @FXML private Label categoryLabel;
    @FXML private Label typeLabel;
    @FXML private Label statusValueLabel;
    @FXML private Label countdownLabel;
    @FXML private Label priceLabel;
    @FXML private Label nextBidLabel;
    @FXML private Label reserveLabel;
    @FXML private Label winnerValueLabel;
    @FXML private Label descriptionLabel;
    @FXML private Label productHighlightLabel;
    @FXML private Label imageHintLabel;
    @FXML private TextField bidAmountField;
    @FXML private Button placeBidButton;
    @FXML private TextField autoBidMaxField;
    @FXML private TextField autoBidIncrementField;
    @FXML private Button autoBidButton;
    @FXML private Label actionStatusLabel;
    @FXML private LineChart<Number, Number> bidChart;
    @FXML private NumberAxis bidChartXAxis;
    @FXML private NumberAxis bidChartYAxis;
    @FXML private ListView<BidTransactionView> bidHistoryListView;

    private AppCoordinator coordinator;
    private AuctionClientService clientService;
    private UserView currentUser;
    private final SessionModel sessionModel = new SessionModel();
    private Timeline countdownTimeline;

    public void init(AppCoordinator coordinator, AuctionClientService clientService,
                     UserView currentUser, AuctionView auction) {
        this.coordinator = coordinator;
        this.clientService = clientService;
        this.currentUser = currentUser;
        // thiết lập giao diện, đăng ký sự kiện, tải dữ liệu chi tiết phiên đấu giá và hiển thị
        configureView();
        clientService.setEventListener(this::handleEventResponse);
        sessionModel.selectAuction(auction);
        renderAuction(auction);
        actionStatusLabel.setText(buildBidHint(auction));
        subscribeAndRender(auction);
    }

    @FXML
    private void goBack() {
        stopCountdown();
        try {
            coordinator.showDashboard(currentUser);
        } catch (Exception exception) {
            actionStatusLabel.setText(exception.getMessage());
        }
    }

    @FXML
    private void placeBid() {
        AuctionView selected = sessionModel.selectedAuctionProperty().get();
        if (selected == null) {
            actionStatusLabel.setText("Auction details are still loading.");
            return;
        }
        if (currentUser.getRole() != UserRole.BIDDER) {
            actionStatusLabel.setText("Only bidder accounts can place bids.");
            return;
        }
        if (!canAcceptBids(selected)) {
            actionStatusLabel.setText("Auction is not accepting bids right now.");
            return;
        }

        try {
            double amount = parseBidAmount(bidAmountField.getText());
            if (amount < selected.getMinimumNextBid()) {
                actionStatusLabel.setText("Bid must be at least "
                        + CURRENCY.format(selected.getMinimumNextBid()));
                return;
            }

            actionStatusLabel.setText("Submitting bid...");
            CompletableFuture.supplyAsync(
                            () -> clientService.placeBid(
                                    new BidRequest(selected.getAuctionId(), currentUser.getId(), amount)))
                    .whenComplete((auction, err) -> Platform.runLater(() -> {
                        if (err != null) {
                            actionStatusLabel.setText(extractMessage(err));
                            return;
                        }
                        sessionModel.selectAuction(auction);
                        renderAuction(auction);
                        actionStatusLabel.setText("Bid accepted!");
                        bidAmountField.clear();
                    }));
        } catch (IllegalArgumentException exception) {
            actionStatusLabel.setText(exception.getMessage());
        }
    }

    @FXML
    private void setAutoBid() {
        AuctionView selected = sessionModel.selectedAuctionProperty().get();
        if (selected == null) {
            actionStatusLabel.setText("Auction details are still loading.");
            return;
        }
        if (currentUser.getRole() != UserRole.BIDDER) {
            actionStatusLabel.setText("Only bidder accounts can configure auto-bidding.");
            return;
        }
        if (!canAcceptBids(selected)) {
            actionStatusLabel.setText("Auction is not accepting auto-bids right now.");
            return;
        }

        try {
            double maxBid = parseBidAmount(autoBidMaxField.getText());
            double increment = parseBidAmount(autoBidIncrementField.getText());
            if (maxBid < selected.getMinimumNextBid()) {
                actionStatusLabel.setText("Auto-bid max must be at least "
                        + CURRENCY.format(selected.getMinimumNextBid()));
                return;
            }
            if (increment <= 0) {
                actionStatusLabel.setText("Auto-bid step must be greater than zero.");
                return;
            }

            actionStatusLabel.setText("Arming auto-bid...");
            CompletableFuture.supplyAsync(
                            () -> clientService.setAutoBid(new AutoBidRequest(
                                    selected.getAuctionId(),
                                    currentUser.getId(),
                                    maxBid,
                                    increment)))
                    .whenComplete((auction, err) -> Platform.runLater(() -> {
                        if (err != null) {
                            actionStatusLabel.setText(extractMessage(err));
                            return;
                        }
                        sessionModel.selectAuction(auction);
                        renderAuction(auction);
                        actionStatusLabel.setText("Auto-bid armed up to " + CURRENCY.format(maxBid) + ".");
                    }));
        } catch (IllegalArgumentException exception) {
            actionStatusLabel.setText(exception.getMessage());
        }
    }
    // bảng vẽ giá thầu theo thời gian, hiển thị lịch sử giá thầu, cập nhật trạng thái và tương tác đặt giá thầu tự động
    private void configureView() {
        boolean bidder = currentUser.getRole() == UserRole.BIDDER;
        bidAmountField.setDisable(!bidder);
        placeBidButton.setDisable(!bidder);
        autoBidMaxField.setDisable(!bidder);
        autoBidIncrementField.setDisable(!bidder);
        autoBidButton.setDisable(!bidder);
        bidChart.setAnimated(false);
        bidChart.setLegendVisible(false);
        bidChartXAxis.setForceZeroInRange(false);
        bidChartYAxis.setForceZeroInRange(false);

        bidHistoryListView.setCellFactory(lv -> new ListCell<>() {
            private final Label bidderLabel = new Label();
            private final Label metaLabel = new Label();
            private final VBox content = new VBox(bidderLabel, metaLabel);

            {
                bidderLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: white;");
                metaLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #b7bfd8;");
                content.setSpacing(4);
                setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            }

            @Override
            protected void updateItem(BidTransactionView item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setStyle("-fx-background-color: transparent;");
                    return;
                }
                bidderLabel.setText(item.getBidderName() + " placed " + CURRENCY.format(item.getAmount()));
                metaLabel.setText(item.getTimestamp().toString());
                setGraphic(content);
                setStyle("-fx-background-color: #111522; -fx-background-radius: 10; "
                        + "-fx-border-color: #2b3047; -fx-border-radius: 10; -fx-padding: 10 12;");
            }
        });
        bidHistoryListView.setItems(sessionModel.getBidHistory());
        bidHistoryListView.setPlaceholder(new Label("No bids have been placed yet."));
    }
    // đăng ký sự kiện, tải dữ liệu chi tiết phiên đấu giá và hiển thị chi tiết,
    // bảng vẽ giá thầu theo thời gian, hiển thị lịch sử giá thầu, cập nhật trạng thái và tương tác đặt giá thầu tự động
    private void subscribeAndRender(AuctionView auction) {
        CompletableFuture.supplyAsync(
                        () -> clientService.subscribe(auction.getAuctionId(), currentUser.getId()))
                .whenComplete((loaded, err) -> Platform.runLater(() -> {
                    if (err != null) {
                        actionStatusLabel.setText(extractMessage(err));
                        return;
                    }
                    sessionModel.selectAuction(loaded);
                    renderAuction(loaded);
                    actionStatusLabel.setText(buildBidHint(loaded));
                }));
    }

    private void renderAuction(AuctionView auction) {
        if (auction == null) {
            return;
        }
        titleLabel.setText(auction.getItem().getName());
        sellerLabel.setText("Seller: " + auction.getSellerName());
        categoryLabel.setText(auction.getItem().getItemType());
        typeLabel.setText(auction.getItem().getDetailLabel());
        statusValueLabel.setText(buildStatusLabel(auction));
        statusValueLabel.setStyle(buildStatusStyle(auction));
        restartCountdown();
        priceLabel.setText(CURRENCY.format(auction.getItem().getCurrentPrice()));
        nextBidLabel.setText(CURRENCY.format(auction.getMinimumNextBid()));
        reserveLabel.setText(CURRENCY.format(auction.getItem().getStartingPrice()));
        winnerValueLabel.setText(safeValue(auction.getWinnerName(), "No winner yet"));
        descriptionLabel.setText(safeValue(auction.getItem().getDescription(), "No description provided."));
        productHighlightLabel.setText(safeValue(auction.getItem().getDetailLabel(), ""));
        imageHintLabel.setText("Auction ID: " + auction.getAuctionId()
                + " | Start: " + auction.getStartTime().format(DATE_TIME_FORMAT)
                + " | End: " + auction.getEndTime().format(DATE_TIME_FORMAT));
        renderBidChart(auction);
        updateBidInteractionState(auction);
    }

    private void updateBidInteractionState(AuctionView auction) {
        boolean canBid = currentUser.getRole() == UserRole.BIDDER && canAcceptBids(auction);
        bidAmountField.setDisable(!canBid);
        placeBidButton.setDisable(!canBid);
        autoBidMaxField.setDisable(!canBid);
        autoBidIncrementField.setDisable(!canBid);
        autoBidButton.setDisable(!canBid);
        bidAmountField.setPromptText("Min " + CURRENCY.format(auction.getMinimumNextBid()));
        autoBidMaxField.setPromptText("Min " + CURRENCY.format(auction.getMinimumNextBid()));
        autoBidIncrementField.setPromptText(CURRENCY.format(auction.getBidIncrement()));
    }

    private void renderBidChart(AuctionView auction) {
        XYChart.Series<Number, Number> series = new XYChart.Series<>();
        if (auction.getBidHistory().isEmpty()) {
            series.getData().add(new XYChart.Data<>(1, auction.getItem().getCurrentPrice()));
        } else {
            for (int i = 0; i < auction.getBidHistory().size(); i++) {
                series.getData().add(new XYChart.Data<>(i + 1, auction.getBidHistory().get(i).getAmount()));
            }
        }
        bidChartXAxis.setLowerBound(0);
        bidChartXAxis.setUpperBound(Math.max(2, series.getData().size() + 1));
        bidChartXAxis.setTickUnit(1);
        bidChart.getData().setAll(java.util.List.of(series));
    }

    private boolean canAcceptBids(AuctionView auction) {
        return auction.getStatus() == AuctionStatus.RUNNING;
    }

    private String buildCountdownLabel(AuctionView auction) {
        return switch (auction.getStatus()) {
            case OPEN -> "Waiting for seller to start";
            case RUNNING -> {
                long secondsRemaining = Math.max(0, Duration.between(LocalDateTime.now(), auction.getEndTime()).getSeconds());
                yield formatRemainingTime(secondsRemaining) + " remaining";
            }
            case FINISHED -> "Auction finished. Waiting for payment or cancellation.";
            case PAID -> "Payment completed";
            case CANCELED -> "Auction canceled";
        };
    }

    private String formatRemainingTime(long totalSeconds) {
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }
    // xử lý phản hồi sự kiện từ máy chủ để cập nhật giao diện người dùng khi có thay đổi về phiên đấu giá, 
    // như giá thầu mới hoặc trạng thái thay đổi
    private void handleEventResponse(ServerResponse<?> response) {
        Platform.runLater(() -> {
            if (!(response.getPayload() instanceof AuctionView auction)) {
                return;
            }
            AuctionView currentAuction = sessionModel.selectedAuctionProperty().get();
            if (currentAuction == null || !currentAuction.getAuctionId().equals(auction.getAuctionId())) {
                return;
            }
            actionStatusLabel.setText(response.getMessage());
            sessionModel.selectAuction(auction);
            renderAuction(auction);
        });
    }

    private String extractMessage(Throwable throwable) {
        Throwable cause = throwable.getCause() == null ? throwable : throwable.getCause();
        return cause.getMessage() == null ? throwable.getMessage() : cause.getMessage();
    }

    private double parseBidAmount(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new IllegalArgumentException("Enter a bid amount.");
        }

        String sanitized = rawValue.trim().replace(" ", "").replace("$", "");
        int lastComma = sanitized.lastIndexOf(',');
        int lastDot = sanitized.lastIndexOf('.');
        if (lastComma >= 0 && lastDot >= 0) {
            if (lastComma > lastDot) {
                sanitized = sanitized.replace(".", "").replace(',', '.');
            } else {
                sanitized = sanitized.replace(",", "");
            }
        } else if (lastComma >= 0) {
            int decimalDigits = sanitized.length() - lastComma - 1;
            sanitized = decimalDigits <= 2
                    ? sanitized.replace(',', '.')
                    : sanitized.replace(",", "");
        }

        try {
            return Double.parseDouble(sanitized);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Enter a valid bid amount.");
        }
    }

    private void restartCountdown() {
        stopCountdown();
        AuctionView selectedAuction = sessionModel.selectedAuctionProperty().get();
        if (selectedAuction == null) {
            countdownLabel.setText("-");
            return;
        }

        countdownLabel.setText(buildCountdownLabel(selectedAuction));
        if (!canAcceptBids(selectedAuction)) {
            return;
        }

        countdownTimeline = new Timeline(new KeyFrame(javafx.util.Duration.seconds(1), event -> {
            AuctionView latestAuction = sessionModel.selectedAuctionProperty().get();
            if (latestAuction == null) {
                stopCountdown();
                countdownLabel.setText("-");
                return;
            }
            countdownLabel.setText(buildCountdownLabel(latestAuction));
        }));
        countdownTimeline.setCycleCount(Timeline.INDEFINITE);
        countdownTimeline.play();
    }

    private void stopCountdown() {
        if (countdownTimeline != null) {
            countdownTimeline.stop();
            countdownTimeline = null;
        }
    }

    private String buildBidHint(AuctionView auction) {
        if (auction == null) {
            return "Auction details are still loading.";
        }
        if (currentUser.getRole() != UserRole.BIDDER) {
            return "Only bidder accounts can place bids.";
        }
        if (auction.getStatus() == AuctionStatus.OPEN) {
            return "Auction is OPEN and waiting for the seller to start it.";
        }
        if (auction.getStatus() == AuctionStatus.FINISHED) {
            return "Auction is FINISHED. No more bids can be placed.";
        }
        if (auction.getStatus() == AuctionStatus.PAID) {
            return "Auction is PAID and has been completed.";
        }
        if (auction.getStatus() == AuctionStatus.CANCELED) {
            return "Auction was CANCELED.";
        }
        return "Place a bid of at least " + CURRENCY.format(auction.getMinimumNextBid()) + ".";
    }

    private String buildStatusLabel(AuctionView auction) {
        return switch (auction.getStatus()) {
            case OPEN -> "OPEN";
            case RUNNING -> "RUNNING";
            case FINISHED -> "FINISHED";
            case PAID -> "PAID";
            case CANCELED -> "CANCELED";
        };
    }

    private String buildStatusStyle(AuctionView auction) {
        return switch (auction.getStatus()) {
            case OPEN -> "-fx-font-size: 11px; -fx-font-weight: bold; "
                    + "-fx-text-fill: #5dcaa5; -fx-background-color: #173527; "
                    + "-fx-padding: 4 10; -fx-border-radius: 999; -fx-background-radius: 999;";
            case RUNNING -> "-fx-font-size: 11px; -fx-font-weight: bold; "
                    + "-fx-text-fill: #f0c040; -fx-background-color: #3a2c14; "
                    + "-fx-padding: 4 10; -fx-border-radius: 999; -fx-background-radius: 999;";
            case FINISHED -> "-fx-font-size: 11px; -fx-font-weight: bold; "
                    + "-fx-text-fill: #d6dcf0; -fx-background-color: #34384a; "
                    + "-fx-padding: 4 10; -fx-border-radius: 999; -fx-background-radius: 999;";
            case PAID -> "-fx-font-size: 11px; -fx-font-weight: bold; "
                    + "-fx-text-fill: #8fe1b5; -fx-background-color: #173527; "
                    + "-fx-padding: 4 10; -fx-border-radius: 999; -fx-background-radius: 999;";
            case CANCELED -> "-fx-font-size: 11px; -fx-font-weight: bold; "
                    + "-fx-text-fill: #f09595; -fx-background-color: #3a1e1e; "
                    + "-fx-padding: 4 10; -fx-border-radius: 999; -fx-background-radius: 999;";
        };
    }

    private String safeValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}

