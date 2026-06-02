package com.auction.server.dao.sqlite;

import com.auction.model.Auction;
import com.auction.model.Bid;
import com.auction.model.Bidder;
import com.auction.model.Electronics;
import com.auction.model.Seller;
import com.auction.server.dao.AuctionDao;
import com.auction.server.domain.ManagedAuction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SqliteAuctionDaoTest {
    @TempDir
    Path tempDir;
    // test này đảm bảo rằng khi một cuộc đấu giá mới được lưu vào SqliteAuctionDao, 
    // nó có thể được tải lại chính xác từ cơ sở dữ liệu SQLite, xác nhận rằng dữ liệu được lưu trữ và truy xuất đúng cách
    @Test
    void persistsAuctionsAndBidHistoryAcrossDaoInstances() {
        AuctionDatabase database = new AuctionDatabase(tempDir.resolve("auction.db"));
        AuctionDao auctionDao = new SqliteAuctionDao(database);

        Seller seller = new Seller("seller01", "Luna Store", "seller01");
        Bidder bidder = new Bidder("bidder01", "Mia Tran", "bidder01");
        LocalDateTime now = LocalDateTime.now();

        Auction auction = new Auction(
                "A1001",
                new Electronics("I2001", "Sony Alpha A7 IV", "Mirrorless camera kit", 1250.0, 18),
                now.minusMinutes(1),
                now.plusMinutes(20),
                25.0);
        ManagedAuction record = new ManagedAuction(auction, seller, now.minusMinutes(2));
        auctionDao.save(record);

        auction.startAuction();
        auction.placeBid(new Bid(bidder, 1300.0, now));
        auctionDao.save(record);
        // Tạo một instance mới của SqliteAuctionDao để kiểm tra việc tải lại dữ liệu từ cơ sở dữ liệu SQLite, 
        // đảm bảo rằng dữ liệu đã được lưu trữ chính xác và có thể truy xuất được qua các instance khác nhau của DAO.
        SqliteAuctionDao reloadedAuctionDao = new SqliteAuctionDao(database);
        try {
            ManagedAuction storedAuction = reloadedAuctionDao.findById("A1001").orElseThrow();
            assertEquals("seller01", storedAuction.getSeller().getId());
            assertEquals(1, storedAuction.getAuction().getBidHistory().size());
            assertNotNull(storedAuction.getAuction().getHighestBid());
            assertEquals(1300.0, storedAuction.getAuction().getHighestBid().getAmount());
            assertEquals(1300.0, storedAuction.getAuction().getItem().getCurrentPrice());
        } finally {
            shutdown(auctionDao);
            shutdown(reloadedAuctionDao);
        }
    }

    private void shutdown(AuctionDao auctionDao) {
        auctionDao.findAll().forEach(record -> record.getAuction().shutdownScheduler());
    }
}
