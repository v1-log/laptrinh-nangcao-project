package com.auction.server.bootstrap;

import com.auction.model.Admin;
import com.auction.model.Auction;
import com.auction.model.Bidder;
import com.auction.model.Clothing;
import com.auction.model.Electronics;
import com.auction.model.Item;
import com.auction.model.Seller;
import com.auction.server.service.AuctionService;
import com.auction.shared.protocol.AuctionActionRequest;
import com.auction.shared.protocol.BidRequest;

import java.time.LocalDateTime;

public final class SampleDataLoader {
    private final AuctionService auctionService;

    public SampleDataLoader(AuctionService auctionService) {
        this.auctionService = auctionService;
    }

    public void load() {
        Seller seller = new Seller("seller01", "Luna Store");
        Bidder bidderOne = new Bidder("bidder01", "Mia Tran");
        Bidder bidderTwo = new Bidder("bidder02", "Quang Le");
        Admin admin = new Admin("admin01", "Ops Desk");

        auctionService.seedUser(seller);
        auctionService.seedUser(bidderOne);
        auctionService.seedUser(bidderTwo);
        auctionService.seedUser(admin);

        Item camera = new Electronics("I2001", "Sony Alpha A7 IV", "Mirrorless camera kit", 1250.0, 18);
        Auction runningAuction = new Auction("A1001", camera, LocalDateTime.now().minusMinutes(10), LocalDateTime.now().plusMinutes(90));
        auctionService.seedAuction(runningAuction, seller);
        auctionService.startAuction(new AuctionActionRequest("A1001", "seller01"));
        auctionService.placeBid(new BidRequest("A1001", "bidder01", 1325.0));

        Item headphones = new Electronics("I2002", "Bose QC Ultra", "Noise canceling headphones", 350.0, 12);
        Auction openAuction = new Auction("A1002", headphones, LocalDateTime.now(), LocalDateTime.now().plusMinutes(60));
        auctionService.seedAuction(openAuction, seller);

        Item laptop = new Electronics("I2003", "Framework Laptop 16", "DIY Edition with upgraded modules", 1700.0, 24);
        Auction finishedAuction = new Auction("A1003", laptop, LocalDateTime.now().minusMinutes(70), LocalDateTime.now().plusMinutes(15));
        auctionService.seedAuction(finishedAuction, seller);
        auctionService.startAuction(new AuctionActionRequest("A1003", "admin01"));
        auctionService.placeBid(new BidRequest("A1003", "bidder02", 1860.0));
        auctionService.finishAuction(new AuctionActionRequest("A1003", "admin01"));

        Item hoodie = new Clothing("I2004", "Oversized Cotton Hoodie", "Streetwear hoodie in washed black", 65.0, "L");
        Auction clothingAuction = new Auction("A1004", hoodie, LocalDateTime.now().minusMinutes(2), LocalDateTime.now().plusMinutes(80));
        auctionService.seedAuction(clothingAuction, seller);
        auctionService.startAuction(new AuctionActionRequest("A1004", "seller01"));
        auctionService.placeBid(new BidRequest("A1004", "bidder02", 79.0));
        auctionService.finishAuction(new AuctionActionRequest("A1004", "admin01"));
        auctionService.markPaid(new AuctionActionRequest("A1004", "admin01"));

        Item gamingLaptop = new Electronics("I2005", "ASUS ROG Zephyrus G16", "Gaming laptop with RTX graphics", 1450.0, 24);
        Auction laptopAuction = new Auction("A1005", gamingLaptop, LocalDateTime.now().minusMinutes(4), LocalDateTime.now().plusMinutes(110));
        auctionService.seedAuction(laptopAuction, seller);
        auctionService.cancelAuction(new AuctionActionRequest("A1005", "admin01"));
    }
}
