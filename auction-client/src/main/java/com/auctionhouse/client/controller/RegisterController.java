package com.auctionhouse.client.controller;

import com.auction.shared.dto.UserView;
import com.auction.shared.enums.UserRole;
import com.auction.shared.protocol.RegisterRequest;
import com.auctionhouse.client.service.AuctionClientService;
import com.auctionhouse.client.view.AppCoordinator;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.util.concurrent.CompletableFuture;

public final class RegisterController {
    @FXML
    private TextField displayNameField;
    @FXML
    private TextField usernameField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private TextField passwordVisibleField;
    @FXML
    private Button togglePasswordButton;
    @FXML
    private PasswordField confirmPasswordField;
    @FXML
    private TextField confirmPasswordVisibleField;
    @FXML
    private Button toggleConfirmPasswordButton;
    @FXML
    private ChoiceBox<UserRole> roleChoice;
    @FXML
    private Label roleDescriptionLabel;
    @FXML
    private VBox storefrontBox;
    @FXML
    private TextField storefrontField;
    @FXML
    private Label statusLabel;
    @FXML
    private Button registerButton;
    @FXML
    private Button backAfterSuccessButton;

    private AppCoordinator coordinator;
    private AuctionClientService clientService;
    private boolean passwordVisible;
    private boolean confirmPasswordVisible;

    public void init(AppCoordinator coordinator, AuctionClientService clientService) {
        this.coordinator = coordinator;
        this.clientService = clientService;
        passwordField.textProperty().bindBidirectional(passwordVisibleField.textProperty());
        confirmPasswordField.textProperty().bindBidirectional(confirmPasswordVisibleField.textProperty());
        roleChoice.setConverter(new StringConverter<>() {
            @Override
            public String toString(UserRole role) {
                return formatRoleLabel(role);
            }

            @Override
            public UserRole fromString(String value) {
                return null;
            }
        });
        roleChoice.getItems().setAll(UserRole.BIDDER, UserRole.SELLER);
        roleChoice.setValue(UserRole.BIDDER);
        roleChoice.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldValue, newValue) -> updateRoleSelection());
        backAfterSuccessButton.setVisible(false);
        backAfterSuccessButton.setManaged(false);
        setPasswordVisibility(false);
        setConfirmPasswordVisibility(false);
        updateRoleSelection();
        statusLabel.setText("Create a new account.");
    }

    @FXML
    private void handleRegister() {
        String displayName = readText(displayNameField);
        String username = readText(usernameField);
        String password = readText(passwordField);
        String confirmPassword = readText(confirmPasswordField);
        UserRole role = roleChoice.getValue() == null ? UserRole.BIDDER : roleChoice.getValue();
        String storefrontName = readText(storefrontField);

        if (displayName.isBlank() || username.isBlank() || password.isBlank() || confirmPassword.isBlank()) {
            statusLabel.setText("Fill all required register fields.");
            return;
        }
        if (password.length() < 4) {
            statusLabel.setText("Password must be at least 4 characters.");
            return;
        }
        if (!password.equals(confirmPassword)) {
            statusLabel.setText("Password confirmation does not match.");
            return;
        }
        if (role == UserRole.SELLER && storefrontName.isBlank()) {
            statusLabel.setText("Seller accounts need a storefront name.");
            return;
        }

        registerButton.setDisable(true);
        statusLabel.setText("Creating account...");
        RegisterRequest request = new RegisterRequest(username, password, displayName, role, storefrontName);
        CompletableFuture.supplyAsync(() -> clientService.register(request))
                .whenComplete((user, throwable) -> Platform.runLater(() -> finishRegister(user, throwable)));
    }

    @FXML
    private void goToLogin() {
        try {
            coordinator.showLogin();
        } catch (Exception exception) {
            statusLabel.setText(exception.getMessage());
        }
    }

    @FXML
    private void togglePasswordVisibility() {
        setPasswordVisibility(!passwordVisible);
    }

    @FXML
    private void toggleConfirmPasswordVisibility() {
        setConfirmPasswordVisibility(!confirmPasswordVisible);
    }

    private void finishRegister(UserView user, Throwable throwable) {
        registerButton.setDisable(false);
        if (throwable != null) {
            statusLabel.setText(extractMessage(throwable));
            return;
        }

        passwordField.clear();
        confirmPasswordField.clear();
        registerButton.setDisable(true);
        backAfterSuccessButton.setVisible(true);
        backAfterSuccessButton.setManaged(true);
        statusLabel.setText("Account created successfully for " + user.getName() + ". Click Back to sign in.");
    }

    private void updateRoleSelection() {
        UserRole selectedRole = roleChoice.getValue();
        boolean sellerSelected = selectedRole == UserRole.SELLER;
        storefrontBox.setVisible(sellerSelected);
        storefrontBox.setManaged(sellerSelected);
        roleDescriptionLabel.setText(buildRoleDescription(selectedRole));
        if (!sellerSelected) {
            storefrontField.clear();
        }
    }

    private String formatRoleLabel(UserRole role) {
        if (role == null) {
            return "";
        }
        return switch (role) {
            case BIDDER -> "Bidder Account";
            case SELLER -> "Seller Account";
            case ADMIN -> "Admin Account";
        };
    }

    private String buildRoleDescription(UserRole role) {
        if (role == null) {
            return "";
        }
        return switch (role) {
            case BIDDER -> "Place bids, follow live auctions, and compete for items.";
            case SELLER -> "Create auctions, manage listings, and run your storefront.";
            case ADMIN -> "Administrative tools are not available for self-registration.";
        };
    }

    private String readText(TextField textField) {
        return textField.getText() == null ? "" : textField.getText().trim();
    }

    private String extractMessage(Throwable throwable) {
        Throwable cause = throwable.getCause() == null ? throwable : throwable.getCause();
        return cause.getMessage() == null ? throwable.getMessage() : cause.getMessage();
    }

    private void setPasswordVisibility(boolean visible) {
        passwordVisible = visible;
        passwordField.setVisible(!visible);
        passwordField.setManaged(!visible);
        passwordVisibleField.setVisible(visible);
        passwordVisibleField.setManaged(visible);
        togglePasswordButton.setText(visible ? "🙈" : "👁");
        positionCaret(passwordVisibleField, passwordField, visible);
    }

    private void setConfirmPasswordVisibility(boolean visible) {
        confirmPasswordVisible = visible;
        confirmPasswordField.setVisible(!visible);
        confirmPasswordField.setManaged(!visible);
        confirmPasswordVisibleField.setVisible(visible);
        confirmPasswordVisibleField.setManaged(visible);
        toggleConfirmPasswordButton.setText(visible ? "🙈" : "👁");
        positionCaret(confirmPasswordVisibleField, confirmPasswordField, visible);
    }

    private void positionCaret(TextField visibleField, TextField hiddenField, boolean showingVisibleField) {
        TextField activeField = showingVisibleField ? visibleField : hiddenField;
        activeField.positionCaret(activeField.getText() == null ? 0 : activeField.getText().length());
    }
}

