package com.auctionhouse.client.controller;

import com.auction.shared.dto.UserView;
import com.auctionhouse.client.service.AuctionClientService;
import com.auctionhouse.client.view.AppCoordinator;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

import java.util.concurrent.CompletableFuture;

public final class LoginController {
    @FXML
    private TextField usernameField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private TextField passwordVisibleField;
    @FXML
    private Button togglePasswordButton;
    @FXML
    private Label statusLabel;
    @FXML
    private Button loginButton;

    private AppCoordinator coordinator;
    private AuctionClientService clientService;
    private boolean passwordVisible;

    public void init(AppCoordinator coordinator, AuctionClientService clientService) {
        this.coordinator = coordinator;
        this.clientService = clientService;
        passwordField.textProperty().bindBidirectional(passwordVisibleField.textProperty());
        setPasswordVisibility(false);
        statusLabel.setText("Sample login: bidder01/bidder01, bidder02/bidder02, seller01/seller01, admin01/admin01");
    }

    @FXML
    private void handleLogin() {
        String username = readText(usernameField);  //đọc dữ liệu từ input
        String password = readText(passwordField);
        if (username.isBlank() || password.isBlank()) {
            statusLabel.setText("Enter both username and password.");//nếu đầu vào trống thì dừng hàm
            return;
        }

        loginButton.setDisable(true); // khóa nút login để tránh spam
        statusLabel.setText("Connecting to auction server...");
        //chạy login ở luồng phụ
        //socket gửi request về sever->AuthenticationService.login()->sever trả lại ueserView
        CompletableFuture.supplyAsync(() -> clientService.login(username, password))
                .whenComplete((user, throwable) -> Platform.runLater(() -> finishLogin(user, throwable)));
    }

    @FXML
    private void goToRegister() {
        try {
            coordinator.showRegister();
        } catch (Exception exception) {
            statusLabel.setText(exception.getMessage());
        }
    }

    @FXML
    private void togglePasswordVisibility() {
        setPasswordVisibility(!passwordVisible);
    }

    @FXML
    private void loginAsBidder() {
        usernameField.setText("bidder01");
        passwordField.setText("bidder01");
        handleLogin();
    }

    @FXML
    private void loginAsSeller() {
        usernameField.setText("seller01");
        passwordField.setText("seller01");
        handleLogin();
    }

    @FXML
    private void loginAsAdmin() {
        usernameField.setText("admin01");
        passwordField.setText("admin01");
        handleLogin();
    }

    private void finishLogin(UserView user, Throwable throwable) {
        loginButton.setDisable(false);
        if (throwable != null) {
            statusLabel.setText(extractMessage(throwable));
            return;
        }

        try {
            coordinator.showDashboard(user);
        } catch (Exception exception) {
            statusLabel.setText(exception.getMessage());
        }
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

        TextField activeField = visible ? passwordVisibleField : passwordField;
        activeField.positionCaret(activeField.getText() == null ? 0 : activeField.getText().length());
    }
}

