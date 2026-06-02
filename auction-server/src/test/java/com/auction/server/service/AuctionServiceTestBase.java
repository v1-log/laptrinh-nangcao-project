package com.auction.server.service;

import com.auction.model.Admin;
import com.auction.model.Art;
import com.auction.model.Auction;
import com.auction.model.Bidder;
import com.auction.model.Seller;
import com.auction.server.dao.AuctionDao;
import com.auction.server.dao.UserDao;
import com.auction.server.dao.memory.InMemoryAuctionDao;
import com.auction.server.dao.memory.InMemoryUserDao;
import com.auction.server.event.AuctionEventPublisher;
import com.auction.server.mapper.AuctionViewMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

import java.time.LocalDateTime;

public abstract class AuctionServiceTestBase {
    protected static AuctionService auctionService;
    protected static AuctionDao auctionDao;
    protected static UserDao userDao;

    @BeforeAll
    public static void beforeAll() {
        auctionDao = new InMemoryAuctionDao();
        userDao = new InMemoryUserDao();
        AuctionViewMapper mapper = new AuctionViewMapper();
        AuctionEventPublisher eventPublisher = new AuctionEventPublisher();
        auctionService = new AuctionService(auctionDao, userDao, mapper, eventPublisher);

        Seller seller = new Seller("seller01", "Luna Store");
        Seller sellerTwo = new Seller("seller02", "Other Store");
        Bidder bidderOne = new Bidder("bidder01", "Mia Tran");
        Bidder bidderTwo = new Bidder("bidder02", "Quang Le");
        Admin admin = new Admin("admin01", "Ops Desk");

        auctionService.seedUser(seller);
        auctionService.seedUser(sellerTwo);
        auctionService.seedUser(bidderOne);
        auctionService.seedUser(bidderTwo);
        auctionService.seedUser(admin);
    }

    @BeforeEach
    public void beforeEach() {
        // clear existing auctions and re-seed a fresh auction for test isolation
        auctionDao.findAll().forEach(record -> auctionDao.deleteById(record.getAuctionId()));
        Auction auction = new Auction(
                "A1001",
                new Art("I1001", "Poster", "Signed art poster", 100.0, "Artist A"),
                LocalDateTime.now().minusMinutes(5),
                LocalDateTime.now().plusMinutes(15)
        );
        auctionService.seedAuction(auction, (Seller) userDao.findById("seller01").get());
    }
}
