package com.auction.server.app;

import com.auction.server.bootstrap.SampleDataLoader;
import com.auction.server.dao.AuctionDao;
import com.auction.server.dao.UserDao;
import com.auction.server.dao.file.FileBackedUserDao;
import com.auction.server.dao.sqlite.AuctionDatabase;
import com.auction.server.dao.sqlite.SqliteAuctionDao;
import com.auction.server.event.AuctionEventPublisher;
import com.auction.server.mapper.AuctionViewMapper;
import com.auction.server.network.AuctionServer;
import com.auction.server.service.AuthenticationService;
import com.auction.server.service.AuctionService;

import java.io.IOException;
import java.net.BindException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class AuctionServerApplication {
    private static final int DEFAULT_PORT = 5050;
    private static final int MAX_PORT_SEARCH_ATTEMPTS = 25;

    private AuctionServerApplication() {
    }

    public static void main(String[] args) throws Exception {
        Path storageDirectory = resolveStorageDirectory();
        AuctionDao auctionDao = new SqliteAuctionDao(new AuctionDatabase(storageDirectory.resolve("auction.db")));
        UserDao userDao = new FileBackedUserDao(resolveUserStoragePath(storageDirectory));
        AuctionEventPublisher eventPublisher = new AuctionEventPublisher();
        AuctionViewMapper mapper = new AuctionViewMapper();
        AuctionService auctionService = new AuctionService(auctionDao, userDao, mapper, eventPublisher);
        if (auctionDao.findAll().isEmpty()) {
            new SampleDataLoader(auctionService).load();
        }
        AuthenticationService authenticationService = new AuthenticationService(userDao);
        int requestedPort = resolveRequestedPort();
        int port = resolveAvailablePort(requestedPort);
        writeActivePort(storageDirectory, port);

        try (AuctionServer server = new AuctionServer(port, authenticationService, auctionService, eventPublisher)) {
            Runtime.getRuntime().addShutdownHook(new Thread(auctionService::shutdown, "auction-server-shutdown"));
            if (port != requestedPort) {
                System.out.println("Port " + requestedPort + " was busy. Server switched to port " + port + ".");
            }
            server.start();
        } catch (BindException exception) {
            throw new IllegalStateException("Port " + port + " is already in use. Stop the old server or start with -Dauctionhouse.port=<port>.", exception);
        } finally {
            auctionService.shutdown();
        }
    }

    private static Path resolveStorageDirectory() {
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

    private static Path resolveUserStoragePath(Path storageDirectory) {
        String configuredPath = System.getProperty("auctionhouse.userstore");
        if (configuredPath != null && !configuredPath.isBlank()) {
            return normalizeJsonPath(Path.of(configuredPath.trim()));
        }
        return storageDirectory.resolve("users.json");
    }

    private static Path normalizeJsonPath(Path path) {
        String fileName = path.getFileName() == null ? "" : path.getFileName().toString();
        if (fileName.endsWith(".bin")) {
            return path.resolveSibling(fileName.substring(0, fileName.length() - 4) + ".json");
        }
        if (fileName.endsWith(".dat")) {
            return path.resolveSibling(fileName.substring(0, fileName.length() - 4) + ".json");
        }
        return path;
    }

    private static int resolveRequestedPort() {
        String configuredPort = System.getProperty("auctionhouse.port");
        if (configuredPort == null || configuredPort.isBlank()) {
            configuredPort = System.getenv("AUCTIONHOUSE_PORT");
        }
        if (configuredPort == null || configuredPort.isBlank()) {
            return DEFAULT_PORT;
        }
        try {
            int port = Integer.parseInt(configuredPort.trim());
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("Port must be between 1 and 65535.");
            }
            return port;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid port value: " + configuredPort, exception);
        }
    }

    private static int resolveAvailablePort(int requestedPort) {
        if (isPortAvailable(requestedPort)) {
            return requestedPort;
        }

        for (int offset = 1; offset <= MAX_PORT_SEARCH_ATTEMPTS; offset++) {
            int candidate = requestedPort + offset;
            if (candidate > 65535) {
                break;
            }
            if (isPortAvailable(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("No available port found from " + requestedPort
                + " to " + Math.min(65535, requestedPort + MAX_PORT_SEARCH_ATTEMPTS) + ".");
    }

    private static boolean isPortAvailable(int port) {
        try (ServerSocket ignored = new ServerSocket(port)) {
            ignored.setReuseAddress(true);
            return true;
        } catch (IOException exception) {
            return false;
        }
    }

    private static void writeActivePort(Path storageDirectory, int port) {
        Path portFile = storageDirectory.resolve("active-port.txt");
        try {
            Files.createDirectories(storageDirectory);
            Files.writeString(portFile, String.valueOf(port), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to write active port file to " + portFile, exception);
        }
    }
}
