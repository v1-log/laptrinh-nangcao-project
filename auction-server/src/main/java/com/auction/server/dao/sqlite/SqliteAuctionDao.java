package com.auction.server.dao.sqlite;

import com.auction.model.Art;
import com.auction.model.Auction;
import com.auction.model.AuctionStatus;
import com.auction.model.Bid;
import com.auction.model.BidTransaction;
import com.auction.model.Bidder;
import com.auction.model.Clothing;
import com.auction.model.Electronics;
import com.auction.model.Item;
import com.auction.model.ItemType;
import com.auction.model.Seller;
import com.auction.server.dao.AuctionDao;
import com.auction.server.domain.ManagedAuction;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class SqliteAuctionDao implements AuctionDao {
    private final Map<String, ManagedAuction> auctions = new ConcurrentHashMap<>();
    private final AuctionDatabase database;

    public SqliteAuctionDao(AuctionDatabase database) {
        if (database == null) {
            throw new IllegalArgumentException("Database is required.");
        }
        this.database = database;
        loadFromDatabase();
    }

    @Override
    public Optional<ManagedAuction> findById(String auctionId) {
        return Optional.ofNullable(auctions.get(auctionId));
    }

    @Override
    public List<ManagedAuction> findAll() {
        return auctions.values().stream()
                .sorted(Comparator.comparing(ManagedAuction::getCreatedAt).reversed())
                .toList();
    }

    @Override
    public List<ManagedAuction> findVisibleAuctions() {
        return auctions.values().stream()
                .sorted(Comparator.comparing(record -> record.getAuction().getEndTime()))
                .toList();
    }

    @Override
    public List<ManagedAuction> findBySellerId(String sellerId) {
        return auctions.values().stream()
                .filter(record -> record.getSeller().getId().equalsIgnoreCase(sellerId))
                .sorted(Comparator.comparing(ManagedAuction::getCreatedAt).reversed())
                .toList();
    }

    @Override
    public synchronized ManagedAuction save(ManagedAuction managedAuction) {
        if (managedAuction == null) {
            throw new IllegalArgumentException("Managed auction is required.");
        }
        persistAuction(managedAuction);
        auctions.put(managedAuction.getAuctionId(), managedAuction);
        return managedAuction;
    }

    @Override
    public synchronized void deleteById(String auctionId) {
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     DELETE FROM auctions
                     WHERE auction_id = ?
                     """)) {
            statement.setString(1, auctionId);
            statement.executeUpdate();
            auctions.remove(auctionId);
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to delete auction " + auctionId + " from " + database.getDatabasePath(), exception);
        }
    }

    private void loadFromDatabase() {
        auctions.clear();
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT
                         auction_id,
                         seller_id,
                         seller_name,
                         seller_password,
                         created_at,
                         status,
                         start_time,
                         end_time,
                         bid_increment,
                         item_id,
                         item_name,
                         item_description,
                         item_starting_price,
                         item_current_price,
                         item_type,
                         item_detail_value,
                         highest_bidder_id,
                         highest_bidder_name,
                         highest_bidder_password,
                         highest_bid_amount,
                         highest_bid_timestamp
                     FROM auctions
                     """);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                ManagedAuction auction = toDomainManagedAuction(connection, resultSet);
                auctions.put(auction.getAuctionId(), auction);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to load auctions from " + database.getDatabasePath(), exception);
        }
    }

    private void persistAuction(ManagedAuction managedAuction) {
        try (Connection connection = database.openConnection()) {
            connection.setAutoCommit(false);
            try {
                upsertAuction(connection, managedAuction);
                replaceBidHistory(connection, managedAuction);
                connection.commit();
            } catch (SQLException exception) {
                rollbackQuietly(connection);
                throw exception;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to save auction " + managedAuction.getAuctionId()
                    + " to " + database.getDatabasePath(), exception);
        }
    }

    private void upsertAuction(Connection connection, ManagedAuction managedAuction) throws SQLException {
        Auction auction = managedAuction.getAuction();
        Item item = auction.getItem();
        Bid highestBid = auction.getHighestBid();
        Seller seller = managedAuction.getSeller();

        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO auctions (
                    auction_id,
                    seller_id,
                    seller_name,
                    seller_password,
                    created_at,
                    status,
                    start_time,
                    end_time,
                    bid_increment,
                    item_id,
                    item_name,
                    item_description,
                    item_starting_price,
                    item_current_price,
                    item_type,
                    item_detail_value,
                    highest_bidder_id,
                    highest_bidder_name,
                    highest_bidder_password,
                    highest_bid_amount,
                    highest_bid_timestamp
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(auction_id) DO UPDATE SET
                    seller_id = excluded.seller_id,
                    seller_name = excluded.seller_name,
                    seller_password = excluded.seller_password,
                    created_at = excluded.created_at,
                    status = excluded.status,
                    start_time = excluded.start_time,
                    end_time = excluded.end_time,
                    bid_increment = excluded.bid_increment,
                    item_id = excluded.item_id,
                    item_name = excluded.item_name,
                    item_description = excluded.item_description,
                    item_starting_price = excluded.item_starting_price,
                    item_current_price = excluded.item_current_price,
                    item_type = excluded.item_type,
                    item_detail_value = excluded.item_detail_value,
                    highest_bidder_id = excluded.highest_bidder_id,
                    highest_bidder_name = excluded.highest_bidder_name,
                    highest_bidder_password = excluded.highest_bidder_password,
                    highest_bid_amount = excluded.highest_bid_amount,
                    highest_bid_timestamp = excluded.highest_bid_timestamp
                """)) {
            statement.setString(1, managedAuction.getAuctionId());
            statement.setString(2, seller.getId());
            statement.setString(3, seller.getName());
            statement.setString(4, seller.getPassword());
            statement.setString(5, managedAuction.getCreatedAt().toString());
            statement.setString(6, auction.getStatus().name());
            statement.setString(7, auction.getStartTime().toString());
            statement.setString(8, auction.getEndTime().toString());
            statement.setDouble(9, auction.getBidIncrement());
            statement.setString(10, item.getId());
            statement.setString(11, item.getName());
            statement.setString(12, item.getDescription());
            statement.setDouble(13, item.getStartingPrice());
            statement.setDouble(14, item.getCurrentPrice());
            statement.setString(15, item.getItemType().name());
            statement.setString(16, extractItemDetail(item));
            if (highestBid == null) {
                statement.setNull(17, Types.VARCHAR);
                statement.setNull(18, Types.VARCHAR);
                statement.setNull(19, Types.VARCHAR);
                statement.setNull(20, Types.DOUBLE);
                statement.setNull(21, Types.VARCHAR);
            } else {
                statement.setString(17, highestBid.getBidder().getId());
                statement.setString(18, highestBid.getBidder().getName());
                statement.setString(19, highestBid.getBidder().getPassword());
                statement.setDouble(20, highestBid.getAmount());
                statement.setString(21, highestBid.getTimestamp().toString());
            }
            statement.executeUpdate();
        }
    }

    private void replaceBidHistory(Connection connection, ManagedAuction managedAuction) throws SQLException {
        try (PreparedStatement deleteStatement = connection.prepareStatement("""
                DELETE FROM bids
                WHERE auction_id = ?
                """)) {
            deleteStatement.setString(1, managedAuction.getAuctionId());
            deleteStatement.executeUpdate();
        }

        List<BidTransaction> bidHistory = managedAuction.getAuction().getBidHistory();
        if (bidHistory.isEmpty()) {
            return;
        }

        try (PreparedStatement insertStatement = connection.prepareStatement("""
                INSERT INTO bids (
                    auction_id,
                    position,
                    bidder_id,
                    bidder_name,
                    bidder_password,
                    amount,
                    bid_timestamp,
                    transaction_time
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            for (int index = 0; index < bidHistory.size(); index++) {
                BidTransaction transaction = bidHistory.get(index);
                Bid bid = transaction.getBid();
                insertStatement.setString(1, managedAuction.getAuctionId());
                insertStatement.setInt(2, index);
                insertStatement.setString(3, bid.getBidder().getId());
                insertStatement.setString(4, bid.getBidder().getName());
                insertStatement.setString(5, bid.getBidder().getPassword());
                insertStatement.setDouble(6, bid.getAmount());
                insertStatement.setString(7, bid.getTimestamp().toString());
                insertStatement.setString(8, transaction.getTransactionTime().toString());
                insertStatement.addBatch();
            }
            insertStatement.executeBatch();
        }
    }

    private ManagedAuction toDomainManagedAuction(Connection connection, ResultSet resultSet) throws SQLException {
        Seller seller = new Seller(
                resultSet.getString("seller_id"),
                resultSet.getString("seller_name"),
                resultSet.getString("seller_password"));
        Item item = toDomainItem(resultSet);
        Bid highestBid = toDomainBid(
                resultSet.getString("highest_bidder_id"),
                resultSet.getString("highest_bidder_name"),
                resultSet.getString("highest_bidder_password"),
                resultSet.getObject("highest_bid_amount") == null ? null : resultSet.getDouble("highest_bid_amount"),
                resultSet.getString("highest_bid_timestamp"));
        List<BidTransaction> bidHistory = loadBidHistory(connection, resultSet.getString("auction_id"));
        Auction auction = Auction.restore(
                resultSet.getString("auction_id"),
                item,
                LocalDateTime.parse(resultSet.getString("start_time")),
                LocalDateTime.parse(resultSet.getString("end_time")),
                resultSet.getDouble("bid_increment"),
                AuctionStatus.valueOf(resultSet.getString("status")),
                highestBid,
                bidHistory,
                resultSet.getDouble("item_current_price"));
        return new ManagedAuction(
                auction,
                seller,
                LocalDateTime.parse(resultSet.getString("created_at")));
    }

    private List<BidTransaction> loadBidHistory(Connection connection, String auctionId) throws SQLException {
        List<BidTransaction> bidHistory = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT
                    bidder_id,
                    bidder_name,
                    bidder_password,
                    amount,
                    bid_timestamp,
                    transaction_time
                FROM bids
                WHERE auction_id = ?
                ORDER BY position
                """)) {
            statement.setString(1, auctionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    Bid bid = new Bid(
                            new Bidder(
                                    resultSet.getString("bidder_id"),
                                    resultSet.getString("bidder_name"),
                                    resultSet.getString("bidder_password")),
                            resultSet.getDouble("amount"),
                            LocalDateTime.parse(resultSet.getString("bid_timestamp")));
                    bidHistory.add(new BidTransaction(
                            auctionId,
                            bid,
                            LocalDateTime.parse(resultSet.getString("transaction_time"))));
                }
            }
        }
        return bidHistory;
    }

    private Item toDomainItem(ResultSet resultSet) throws SQLException {
        ItemType itemType = ItemType.valueOf(resultSet.getString("item_type"));
        String itemId = resultSet.getString("item_id");
        String itemName = resultSet.getString("item_name");
        String description = resultSet.getString("item_description");
        double startingPrice = resultSet.getDouble("item_starting_price");
        String detailValue = resultSet.getString("item_detail_value");

        return switch (itemType) {
            case ART -> new Art(itemId, itemName, description, startingPrice, detailValue);
            case ELECTRONICS -> new Electronics(itemId, itemName, description, startingPrice, Integer.parseInt(detailValue));
            case CLOTHING -> new Clothing(itemId, itemName, description, startingPrice, detailValue);
        };
    }

    private Bid toDomainBid(
            String bidderId,
            String bidderName,
            String bidderPassword,
            Double amount,
            String timestamp) {
        if (bidderId == null || amount == null || timestamp == null) {
            return null;
        }
        return new Bid(
                new Bidder(bidderId, bidderName, bidderPassword),
                amount,
                LocalDateTime.parse(timestamp));
    }

    private String extractItemDetail(Item item) {
        if (item instanceof Art art) {
            return art.getArtist();
        }
        if (item instanceof Electronics electronics) {
            return String.valueOf(electronics.getWarrantyMonths());
        }
        if (item instanceof Clothing clothing) {
            return clothing.getSizeLabel();
        }
        throw new IllegalArgumentException("Unsupported item type: " + item.getClass().getName());
    }

    private void rollbackQuietly(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // Ignore rollback failures and surface the original storage exception.
        }
    }
}
