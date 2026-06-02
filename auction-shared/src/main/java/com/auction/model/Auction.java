package com.auction.model;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public class Auction implements Subject {
  private static final double DEFAULT_BID_INCREMENT = 0.1d;
  private static final long EXTENSION_THRESHOLD_SECONDS = 10L;
  private static final long EXTENSION_SECONDS = 30L;

  private final String auctionId;
  private final Item item;
  private final double bidIncrement;
  private final Duration auctionDuration;
  private Bid highestBid;
  private AuctionStatus status;

  private LocalDateTime startTime;
  private volatile LocalDateTime endTime;
  private ScheduledExecutorService scheduler;
  private final List<Observer> observers = new CopyOnWriteArrayList<>();
  private final List<BidTransaction> bidHistory = new ArrayList<>();

  public Auction(String auctionId, Item item, LocalDateTime startTime, LocalDateTime endTime) {
    this(auctionId, item, startTime, endTime, DEFAULT_BID_INCREMENT);
  }

  public Auction(String auctionId, Item item, LocalDateTime startTime, LocalDateTime endTime, double bidIncrement) {
    this(auctionId, item, startTime, endTime, bidIncrement, null, null, List.of());
  }

  private Auction(
          String auctionId,
          Item item,
          LocalDateTime startTime,
          LocalDateTime endTime,
          double bidIncrement,
          AuctionStatus status,
          Bid highestBid,
          List<BidTransaction> bidHistory) {
    this.auctionId = auctionId;
    this.item = item;
    this.startTime = startTime;
    this.endTime = endTime;
    this.bidIncrement = normalizeBidIncrement(bidIncrement);
    this.auctionDuration = normalizeAuctionDuration(startTime, endTime);
    if (status == null) {
      LocalDateTime now = LocalDateTime.now();
      if (endTime.isBefore(now) || endTime.isEqual(now)) {
        this.status = AuctionStatus.FINISHED;
      } else if (startTime.isAfter(now)) {
        this.status = AuctionStatus.OPEN;
      } else {
        this.status = AuctionStatus.RUNNING;
      }
    } else {
      this.status = status;
    }
    this.highestBid = highestBid;
    if (bidHistory != null && !bidHistory.isEmpty()) {
      this.bidHistory.addAll(bidHistory);
    }
    if (this.status == AuctionStatus.RUNNING) {
      startAutoClose();
    }
  }

  public static Auction restore(
          String auctionId,
          Item item,
          LocalDateTime startTime,
          LocalDateTime endTime,
          double bidIncrement,
          AuctionStatus status,
          Bid highestBid,
          List<BidTransaction> bidHistory,
          double currentPrice) {
    item.restoreCurrentPrice(currentPrice);
    return new Auction(auctionId, item, startTime, endTime, bidIncrement, status, highestBid, bidHistory);
  }

  public synchronized void placeBid(Bid bid) {
    if (status != AuctionStatus.RUNNING) {
      throw new com.auction.model.exception.AuctionClosedException(auctionId, status);
    }
    if (bid == null || bid.getBidder() == null) {
      throw new IllegalArgumentException("Bid and bidder are required.");
    }
    if (bid.getAmount() <= 0) {
      throw new com.auction.model.exception.InvalidBidException(item.getCurrentPrice(), bid.getAmount());
    }
    if (highestBid != null
            && highestBid.getBidder().getId().equals(bid.getBidder().getId())
            && Double.compare(highestBid.getAmount(), bid.getAmount()) == 0) {
      throw new com.auction.model.exception.DuplicateBidException(auctionId, bid.getBidder().getId(), bid.getAmount());
    }

    double minimumRequiredBid = getMinimumNextBid();
    if (Double.compare(bid.getAmount(), minimumRequiredBid) < 0) {
      throw new com.auction.model.exception.InvalidBidException(item.getCurrentPrice(), bid.getAmount());
    }

    long secondsLeft = Duration.between(LocalDateTime.now(), endTime).getSeconds();
    if (secondsLeft <= EXTENSION_THRESHOLD_SECONDS) {
      endTime = endTime.plusSeconds(EXTENSION_SECONDS);
      System.out.println("Auction extended by 30 seconds! New end time: " + endTime);
      restartScheduler();
    }

    item.setCurrentPrice(bid.getAmount());
    highestBid = bid;
    bidHistory.add(new BidTransaction(auctionId, bid));
    notifyObservers();

    System.out.println("New highest bid: " + bid.getAmount() + " by " + bid.getBidder().getName());
  }

  public synchronized void startAuction() {
    if (status == AuctionStatus.RUNNING) {
      return;
    }
    if (status != AuctionStatus.OPEN) {
      throw new IllegalStateException("Only OPEN auctions can be started.");
    }
    startTime = LocalDateTime.now();
    endTime = startTime.plus(auctionDuration);
    status = AuctionStatus.RUNNING;
    restartScheduler();
    notifyObservers();
  }

  private void startAutoClose() {
    scheduler = Executors.newSingleThreadScheduledExecutor(buildDaemonSchedulerFactory());
    long delay = Duration.between(LocalDateTime.now(), endTime).toMillis();
    if (delay <= 0) {
      closeAuction();
      return;
    }
    scheduler.schedule(() -> {
      closeAuction();
      System.out.println("Auction closed automatically!");
    }, delay, TimeUnit.MILLISECONDS);
  }

  private void restartScheduler() {
    if (scheduler != null && !scheduler.isShutdown()) {
      scheduler.shutdownNow();
    }
    startAutoClose();
  }

  public synchronized void closeAuction() {
    if (status == AuctionStatus.FINISHED || status == AuctionStatus.CANCELED || status == AuctionStatus.PAID) {
      return;
    }
    status = AuctionStatus.FINISHED;
    if (scheduler != null && !scheduler.isShutdown()) {
      scheduler.shutdown();
    }
    notifyObservers();
  }

  public synchronized void cancelAuction() {
    status = AuctionStatus.CANCELED;
    if (scheduler != null && !scheduler.isShutdown()) {
      scheduler.shutdown();
    }
    notifyObservers();
  }

  public synchronized void markPaid() {
    if (status == AuctionStatus.FINISHED) {
      status = AuctionStatus.PAID;
      notifyObservers();
    }
  }

  public synchronized void shutdownScheduler() {
    if (scheduler != null && !scheduler.isShutdown()) {
      scheduler.shutdownNow();
    }
  }

  @Override
  public void addObserver(Observer observer) {
    observers.add(observer);
  }

  @Override
  public void removeObserver(Observer observer) {
    observers.remove(observer);
  }

  @Override
  public void notifyObservers() {
    for (Observer observer : observers) {
      observer.update(this);
    }
  }

  public String getAuctionId() {
    return auctionId;
  }

  public Bid getHighestBid() {
    return highestBid;
  }

  public Item getItem() {
    return item;
  }

  public AuctionStatus getStatus() {
    return status;
  }

  public boolean isOpen() {
    return status == AuctionStatus.OPEN || status == AuctionStatus.RUNNING;
  }

  public LocalDateTime getStartTime() {
    return startTime;
  }

  public LocalDateTime getEndTime() {
    return endTime;
  }

  public double getBidIncrement() {
    return bidIncrement;
  }

  public double getMinimumNextBid() {
    return item.getCurrentPrice() + bidIncrement;
  }

  public List<BidTransaction> getBidHistory() {
    return Collections.unmodifiableList(bidHistory);
  }

  private ThreadFactory buildDaemonSchedulerFactory() {
    return runnable -> {
      Thread thread = new Thread(runnable, "auction-" + auctionId + "-scheduler");
      thread.setDaemon(true);
      return thread;
    };
  }

  private double normalizeBidIncrement(double bidIncrement) {
    return bidIncrement > 0 ? bidIncrement : DEFAULT_BID_INCREMENT;
  }

  private Duration normalizeAuctionDuration(LocalDateTime startTime, LocalDateTime endTime) {
    Duration duration = Duration.between(startTime, endTime);
    return duration.isNegative() || duration.isZero() ? Duration.ofMinutes(1) : duration;
  }
}
