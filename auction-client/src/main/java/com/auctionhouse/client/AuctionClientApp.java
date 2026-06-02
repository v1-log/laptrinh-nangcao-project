package com.auctionhouse.client;

import com.auctionhouse.client.service.AuctionClientService;
import com.auctionhouse.client.view.AppCoordinator;
import javafx.application.Application;
import javafx.stage.Stage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class AuctionClientApp extends Application {
    private static final int DEFAULT_PORT = 5050;
    private AuctionClientService clientService;

    @Override
    public void start(Stage stage) throws Exception {
        clientService = new AuctionClientService(resolveHost(), resolvePort());
        AppCoordinator coordinator = new AppCoordinator(stage, clientService);
        coordinator.showLogin();
    }

    @Override
    public void stop() throws Exception {
        if (clientService != null) {
            clientService.close();
        }
        super.stop();
    }

    public static void main(String[] args) {
        launch(args);
    }

    private String resolveHost() {
        String configuredHost = System.getProperty("auctionhouse.host");
        if (configuredHost == null || configuredHost.isBlank()) {
            configuredHost = System.getenv("AUCTIONHOUSE_HOST");
        }
        return configuredHost == null || configuredHost.isBlank() ? "192.168.2.108" : configuredHost.trim();
    }

    private int resolvePort() {
        String configuredPort = System.getProperty("auctionhouse.port");
        if (configuredPort == null || configuredPort.isBlank()) {
            configuredPort = System.getenv("AUCTIONHOUSE_PORT");
        }
        if (configuredPort != null && !configuredPort.isBlank()) {
            return Integer.parseInt(configuredPort.trim());
        }

        Path portFile = resolveStorageDirectory().resolve("active-port.txt");
        if (Files.exists(portFile)) {
            try {
                String value = Files.readString(portFile, StandardCharsets.UTF_8).trim();
                if (!value.isBlank()) {
                    return Integer.parseInt(value);
                }
            } catch (IOException | NumberFormatException ignored) {
            }
        }
        return DEFAULT_PORT;
    }

    private Path resolveStorageDirectory() {
        String configuredDirectory = System.getProperty("auctionhouse.storageDir");
        if (configuredDirectory != null && !configuredDirectory.isBlank()) {
            return Path.of(configuredDirectory.trim());
        }
        Path moduleRelativeStorage = Path.of("auction-server", "data");
        if (moduleRelativeStorage.toFile().exists()) {
            return moduleRelativeStorage;
        }
        return Path.of("data");
    }
}
