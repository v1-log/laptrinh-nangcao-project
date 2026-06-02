package com.auction.server.network;

import com.auction.server.event.AuctionEventPublisher;
import com.auction.server.service.AuthenticationService;
import com.auction.server.service.AuctionService;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AuctionServer implements AutoCloseable {
    private final int port;
    private final AuthenticationService authenticationService;
    private final AuctionService auctionService;
    private final AuctionEventPublisher eventPublisher;
    private final ExecutorService clientPool = Executors.newCachedThreadPool();
    private volatile boolean running;
    private ServerSocket serverSocket;

    public AuctionServer(
            int port,
            AuthenticationService authenticationService,
            AuctionService auctionService,
            AuctionEventPublisher eventPublisher) {
        this.port = port;
        this.authenticationService = authenticationService;
        this.auctionService = auctionService;
        this.eventPublisher = eventPublisher;
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        running = true;
        System.out.println("Auction server listening on port " + port);

        while (running) {
            try {
                Socket socket = serverSocket.accept();
                clientPool.submit(new ClientSession(socket, authenticationService, auctionService, eventPublisher));
            } catch (SocketException exception) {
                if (running) {
                    throw exception;
                }
            }
        }
    }

    @Override
    public void close() throws IOException {
        running = false;
        clientPool.shutdownNow();
        if (serverSocket != null && !serverSocket.isClosed()) {
            serverSocket.close();
        }
    }
}
