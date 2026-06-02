package com.auction.server.dao.sqlite;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public final class AuctionDatabase {
    private final Path databasePath;
    private final String jdbcUrl;

    public AuctionDatabase(Path databasePath) {
        if (databasePath == null) {
            throw new IllegalArgumentException("Database path is required.");
        }
        this.databasePath = databasePath.toAbsolutePath().normalize();
        this.jdbcUrl = "jdbc:sqlite:" + this.databasePath.toString().replace('\\', '/');
        loadDriver();
        createParentDirectoryIfNeeded();
        initializeSchema();
    }

    public Connection openConnection() throws SQLException {
        Connection connection = DriverManager.getConnection(jdbcUrl);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA busy_timeout = 5000");
        }
        return connection;
    }

    public Path getDatabasePath() {
        return databasePath;
    }

    private void loadDriver() {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("SQLite JDBC driver is not available.", exception);
        }
    }

    private void createParentDirectoryIfNeeded() {
        try {
            Path parent = databasePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to create database directory for " + databasePath, exception);
        }
    }

    private void initializeSchema() {
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS auctions (
                        auction_id TEXT PRIMARY KEY,
                        seller_id TEXT NOT NULL,
                        seller_name TEXT NOT NULL,
                        seller_password TEXT NOT NULL,
                        created_at TEXT NOT NULL,
                        status TEXT NOT NULL,
                        start_time TEXT NOT NULL,
                        end_time TEXT NOT NULL,
                        bid_increment REAL NOT NULL,
                        item_id TEXT NOT NULL,
                        item_name TEXT NOT NULL,
                        item_description TEXT NOT NULL,
                        item_starting_price REAL NOT NULL,
                        item_current_price REAL NOT NULL,
                        item_type TEXT NOT NULL,
                        item_detail_value TEXT NOT NULL,
                        highest_bidder_id TEXT,
                        highest_bidder_name TEXT,
                        highest_bidder_password TEXT,
                        highest_bid_amount REAL,
                        highest_bid_timestamp TEXT
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS bids (
                        auction_id TEXT NOT NULL,
                        position INTEGER NOT NULL,
                        bidder_id TEXT NOT NULL,
                        bidder_name TEXT NOT NULL,
                        bidder_password TEXT NOT NULL,
                        amount REAL NOT NULL,
                        bid_timestamp TEXT NOT NULL,
                        transaction_time TEXT NOT NULL,
                        PRIMARY KEY (auction_id, position),
                        FOREIGN KEY (auction_id) REFERENCES auctions(auction_id) ON DELETE CASCADE
                    )
                    """);
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_auctions_seller ON auctions (seller_id)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_auctions_status_end_time ON auctions (status, end_time)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_bids_auction_time ON bids (auction_id, transaction_time)");
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to initialize database schema at " + databasePath, exception);
        }
    }
}
