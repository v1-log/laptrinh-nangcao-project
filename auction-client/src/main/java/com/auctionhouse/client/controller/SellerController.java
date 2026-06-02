package com.auctionhouse.client.controller;

import com.auction.shared.dto.AuctionView;
import com.auction.shared.dto.UserView;
import com.auction.shared.enums.UserRole;
import com.auction.shared.protocol.AuctionActionRequest;
import com.auction.shared.protocol.CreateAuctionRequest;
import com.auction.shared.protocol.UpdateAuctionRequest;
import com.auctionhouse.client.service.AuctionClientService;
import com.auctionhouse.client.view.AppCoordinator;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

import java.text.NumberFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public final class SellerController {
    private static final NumberFormat CURRENCY = NumberFormat.getCurrencyInstance(Locale.US);

    @FXML private Label pageTitle;
    @FXML private Label actionStatusLabel;
    @FXML private ListView<AuctionView> myAuctionListView;
    @FXML private ChoiceBox<String> productTypeChoice;
    @FXML private TextField createTitleField;
    @FXML private TextArea createDescriptionArea;
    @FXML private TextField createCategoryField;
    @FXML private TextField createImageHintField;
    @FXML private TextField createOpeningPriceField;
    @FXML private TextField createReservePriceField;
    @FXML private TextField createIncrementField;
    @FXML private Spinner<Integer> durationSpinner;
    @FXML private Label secondaryFieldLabel;
    @FXML private TextField createSecondaryField;
    @FXML private Label tertiaryFieldLabel;
    @FXML private TextField createTertiaryField;
    @FXML private VBox adminPanel;

    private AppCoordinator coordinator;
    private AuctionClientService clientService;
    private UserView currentUser;
    private AuctionView selectedAuction;

    public void init(AppCoordinator coordinator, AuctionClientService clientService, UserView user) {
        this.coordinator = coordinator;
        this.clientService = clientService;
        this.currentUser = user;
        configureView();
        loadMyAuctions();
    }

    @FXML
    private void goBack() {
        try {
            coordinator.showDashboard(currentUser);
        } catch (Exception exception) {
            actionStatusLabel.setText(exception.getMessage());
        }
    }

    @FXML
    private void createAuction() {
        try {
            CreateAuctionRequest request = buildCreateRequest();
            actionStatusLabel.setText("Publishing auction...");
            CompletableFuture.supplyAsync(() -> clientService.createAuction(request))
                    .whenComplete((auction, err) -> Platform.runLater(() -> {
                        if (err != null) {
                            actionStatusLabel.setText(extractMessage(err));
                            return;
                        }
                        actionStatusLabel.setText("Auction created.");
                        clearForm();
                        loadMyAuctions();
                    }));
        } catch (IllegalArgumentException exception) {
            actionStatusLabel.setText(exception.getMessage());
        }
    }

    @FXML
    private void updateAuction() {
        if (selectedAuction == null) {
            actionStatusLabel.setText("Select an auction first.");
            return;
        }

        try {
            UpdateAuctionRequest request = buildUpdateRequest(selectedAuction);
            actionStatusLabel.setText("Updating auction...");
            CompletableFuture.supplyAsync(() -> clientService.updateAuction(request))
                    .whenComplete((auction, err) -> Platform.runLater(() -> {
                        if (err != null) {
                            actionStatusLabel.setText(extractMessage(err));
                            return;
                        }
                        actionStatusLabel.setText("Auction updated.");
                        loadMyAuctions();
                    }));
        } catch (IllegalArgumentException exception) {
            actionStatusLabel.setText(exception.getMessage());
        }
    }

    @FXML
    private void deleteAuction() {
        if (selectedAuction == null) {
            actionStatusLabel.setText("Select an auction first.");
            return;
        }
        actionStatusLabel.setText("Deleting auction...");
        CompletableFuture.runAsync(
                        () -> clientService.deleteAuction(
                                new AuctionActionRequest(selectedAuction.getAuctionId(), currentUser.getId())))
                .whenComplete((ignored, err) -> Platform.runLater(() -> {
                    if (err != null) {
                        actionStatusLabel.setText(extractMessage(err));
                        return;
                    }
                    selectedAuction = null;
                    clearForm();
                    actionStatusLabel.setText("Auction deleted.");
                    loadMyAuctions();
                }));
    }

    @FXML
    private void startAuction() {
        updateAuctionState("Starting auction...",
                () -> clientService.startAuction(new AuctionActionRequest(selectedAuction.getAuctionId(), currentUser.getId())),
                "Auction is now RUNNING.");
    }

    @FXML
    private void finishAuction() {
        updateAuctionState("Finishing auction...",
                () -> clientService.finishAuction(new AuctionActionRequest(selectedAuction.getAuctionId(), currentUser.getId())),
                "Auction finished.");
    }

    @FXML
    private void markAuctionPaid() {
        updateAuctionState("Marking auction paid...",
                () -> clientService.markPaid(new AuctionActionRequest(selectedAuction.getAuctionId(), currentUser.getId())),
                "Auction marked paid.");
    }

    @FXML
    private void cancelAuction() {
        updateAuctionState("Canceling auction...",
                () -> clientService.cancelAuction(new AuctionActionRequest(selectedAuction.getAuctionId(), currentUser.getId())),
                "Auction canceled.");
    }

    private void updateAuctionState(String pendingMessage,
                                    ThrowingSupplier<AuctionView> action,
                                    String successMessage) {
        if (selectedAuction == null) {
            actionStatusLabel.setText("Select an auction first.");
            return;
        }
        actionStatusLabel.setText(pendingMessage);
        CompletableFuture.supplyAsync(() -> {
            try {
                return action.get();
            } catch (Exception exception) {
                throw new IllegalStateException(exception.getMessage(), exception);
            }
        }).whenComplete((auction, err) -> Platform.runLater(() -> {
            if (err != null) {
                actionStatusLabel.setText(extractMessage(err));
                return;
            }
            actionStatusLabel.setText(successMessage);
            loadMyAuctions();
        }));
    }

    private void configureView() {
        pageTitle.setText(currentUser.getRole() == UserRole.ADMIN ? "Admin Panel" : "Seller Panel");
        productTypeChoice.getItems().setAll("electronics", "art", "clothing");
        productTypeChoice.setValue("electronics");
        durationSpinner.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(5, 720, 60, 5));

        boolean isAdmin = currentUser.getRole() == UserRole.ADMIN;
        adminPanel.setVisible(isAdmin);
        adminPanel.setManaged(isAdmin);

        myAuctionListView.setCellFactory(lv -> new ListCell<>() {
            private final Label titleLabel = new Label();
            private final Label metaLabel = new Label();
            private final VBox content = new VBox(titleLabel, metaLabel);

            {
                titleLabel.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: white;");
                metaLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #b7bfd8;");
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
                metaLabel.setText(formatStatus(item) + " | " + CURRENCY.format(item.getItem().getCurrentPrice()));
                setGraphic(content);
                setStyle("-fx-background-color: #111522; -fx-background-radius: 10; "
                        + "-fx-border-color: #2b3047; -fx-border-radius: 10; -fx-padding: 10 12;");
            }
        });

        myAuctionListView.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldValue, newValue) -> {
                    if (newValue != null) {
                        selectedAuction = newValue;
                        populateForm(newValue);
                    }
                });
        productTypeChoice.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldValue, newValue) -> updateItemTypeHints());
        updateItemTypeHints();
    }

    private void loadMyAuctions() {
        CompletableFuture.supplyAsync(() -> clientService.loadDashboard(currentUser.getId()))
                .whenComplete((data, err) -> Platform.runLater(() -> {
                    if (err != null) {
                        actionStatusLabel.setText(extractMessage(err));
                        return;
                    }
                    myAuctionListView.getItems().setAll(data.sellerAuctions());
                }));
    }

    private void populateForm(AuctionView auction) {
        createTitleField.setText(auction.getItem().getName());
        createDescriptionArea.setText(auction.getItem().getDescription());
        createCategoryField.setText(auction.getItem().getItemType());
        createImageHintField.clear();
        createOpeningPriceField.setText(String.valueOf(auction.getItem().getStartingPrice()));
        createReservePriceField.clear();
        createIncrementField.setText(String.valueOf(auction.getBidIncrement()));
        durationSpinner.getValueFactory().setValue(
                (int) Math.max(5, Duration.between(auction.getStartTime(), auction.getEndTime()).toMinutes()));
        productTypeChoice.setValue(auction.getItem().getItemType().toLowerCase());
        createSecondaryField.setText(extractDetailValue(auction.getItem().getDetailLabel()));
        createTertiaryField.clear();
    }

    private CreateAuctionRequest buildCreateRequest() {
        String itemType = selectedItemType();
        return new CreateAuctionRequest(
                currentUser.getId(),
                itemType,
                requiredText(createTitleField.getText(), "Title is required."),
                buildDescription(),
                parsePositiveDouble(createOpeningPriceField.getText(), "Opening price must be greater than zero."),
                parsePositiveDouble(createIncrementField.getText(), "Bid increment must be greater than zero."),
                durationSpinner.getValue(),
                buildExtraValue(itemType));
    }

    private UpdateAuctionRequest buildUpdateRequest(AuctionView auction) {
        String itemType = selectedItemType();
        return new UpdateAuctionRequest(
                auction.getAuctionId(),
                currentUser.getId(),
                itemType,
                requiredText(createTitleField.getText(), "Title is required."),
                buildDescription(),
                parsePositiveDouble(createOpeningPriceField.getText(), "Opening price must be greater than zero."),
                parsePositiveDouble(createIncrementField.getText(), "Bid increment must be greater than zero."),
                durationSpinner.getValue(),
                buildExtraValue(itemType));
    }

    private String buildDescription() {
        List<String> parts = new ArrayList<>();
        addDescriptionPart(parts, createDescriptionArea.getText());
        addDescriptionPart(parts, prefixed("Category", createCategoryField.getText()));
        addDescriptionPart(parts, prefixed("Image hint", createImageHintField.getText()));
        addDescriptionPart(parts, prefixed("Reserve note", createReservePriceField.getText()));
        addDescriptionPart(parts, prefixed("Increment note", createIncrementField.getText()));
        addDescriptionPart(parts, prefixed("Extra notes", createTertiaryField.getText()));
        if (parts.isEmpty()) {
            throw new IllegalArgumentException("Description is required.");
        }
        return String.join(" | ", parts);
    }

    private void addDescriptionPart(List<String> parts, String value) {
        String cleaned = clean(value);
        if (!cleaned.isBlank()) {
            parts.add(cleaned);
        }
    }

    private String buildExtraValue(String itemType) {
        String secondary = clean(createSecondaryField.getText());
        if ("art".equalsIgnoreCase(itemType)) {
            return secondary.isBlank() ? "Unknown Artist" : secondary;
        }
        if ("clothing".equalsIgnoreCase(itemType)) {
            return secondary.isBlank() ? "M" : secondary.toUpperCase(Locale.ROOT);
        }
        if (secondary.isBlank()) {
            return "12";
        }
        try {
            return String.valueOf(Integer.parseInt(secondary));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("For electronics, warranty must be a whole number of months.");
        }
    }

    private String selectedItemType() {
        return productTypeChoice.getValue() == null ? "electronics" : productTypeChoice.getValue().trim();
    }

    private String requiredText(String value, String message) {
        String cleaned = clean(value);
        if (cleaned.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return cleaned;
    }

    private double parsePositiveDouble(String value, String message) {
        try {
            double parsed = parseFlexibleDouble(value);
            if (parsed <= 0) {
                throw new IllegalArgumentException(message);
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(message);
        }
    }

    private String prefixed(String prefix, String value) {
        String cleaned = clean(value);
        return cleaned.isBlank() ? "" : prefix + ": " + cleaned;
    }

    private String extractDetailValue(String detailLabel) {
        if (detailLabel == null || detailLabel.isBlank()) {
            return "";
        }
        int separator = detailLabel.indexOf(':');
        return separator >= 0 ? detailLabel.substring(separator + 1).trim() : detailLabel;
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private void clearForm() {
        createTitleField.clear();
        createDescriptionArea.clear();
        createCategoryField.clear();
        createImageHintField.clear();
        createOpeningPriceField.clear();
        createReservePriceField.clear();
        createIncrementField.clear();
        createSecondaryField.clear();
        createTertiaryField.clear();
        durationSpinner.getValueFactory().setValue(60);
        productTypeChoice.setValue("electronics");
        updateItemTypeHints();
    }

    private String extractMessage(Throwable throwable) {
        Throwable cause = throwable.getCause() == null ? throwable : throwable.getCause();
        return cause.getMessage() == null ? throwable.getMessage() : cause.getMessage();
    }

    private String formatStatus(AuctionView auction) {
        return switch (auction.getStatus()) {
            case OPEN -> "OPEN - Waiting to start";
            case RUNNING -> "RUNNING - Accepting bids";
            case FINISHED -> "FINISHED - Awaiting settlement";
            case PAID -> "PAID - Completed";
            case CANCELED -> "CANCELED - Closed";
        };
    }

    private void updateItemTypeHints() {
        String itemType = selectedItemType();
        if ("art".equalsIgnoreCase(itemType)) {
            secondaryFieldLabel.setText("Artist");
            createSecondaryField.setPromptText("e.g. Artist A");
            tertiaryFieldLabel.setText("Era / Medium");
            createTertiaryField.setPromptText("e.g. Limited print");
            return;
        }
        if ("clothing".equalsIgnoreCase(itemType)) {
            secondaryFieldLabel.setText("Size");
            createSecondaryField.setPromptText("e.g. M / L");
            tertiaryFieldLabel.setText("Material / Condition");
            createTertiaryField.setPromptText("e.g. Cotton, like new");
            return;
        }
        secondaryFieldLabel.setText("Warranty (months)");
        createSecondaryField.setPromptText("e.g. 24");
        tertiaryFieldLabel.setText("Brand / Condition");
        createTertiaryField.setPromptText("e.g. ASUS, like new");
    }

    private double parseFlexibleDouble(String value) {
        String sanitized = clean(value).replace(" ", "").replace("$", "");
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
        return Double.parseDouble(sanitized);
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}

