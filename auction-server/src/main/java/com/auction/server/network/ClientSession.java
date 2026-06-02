package com.auction.server.network;

import com.auction.model.User;
import com.auction.server.event.AuctionEventListener;
import com.auction.server.event.AuctionEventPublisher;
import com.auction.server.service.AuthenticationService;
import com.auction.server.service.AuctionService;
import com.auction.shared.dto.AuctionView;
import com.auction.shared.dto.DashboardView;
import com.auction.shared.dto.UserView;
import com.auction.shared.enums.CommandType;
import com.auction.shared.protocol.AuctionActionRequest;
import com.auction.shared.protocol.AuctionSelectionRequest;
import com.auction.shared.protocol.AuctionSubscriptionRequest;
import com.auction.shared.protocol.AutoBidRequest;
import com.auction.shared.protocol.BidRequest;
import com.auction.shared.protocol.ClientRequest;
import com.auction.shared.protocol.CreateAuctionRequest;
import com.auction.shared.protocol.DashboardRequest;
import com.auction.shared.protocol.DepositFundsRequest;
import com.auction.shared.protocol.LoginRequest;
import com.auction.shared.protocol.RegisterRequest;
import com.auction.shared.protocol.ServerResponse;
import com.auction.shared.protocol.UpdateAuctionRequest;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketException;

public final class ClientSession implements Runnable, AuctionEventListener {
    private final Socket socket;
    private final AuthenticationService authenticationService;
    private final AuctionService auctionService;
    private final AuctionEventPublisher eventPublisher;
    private final com.auction.server.service.AuctionManager auctionManager =
            com.auction.server.service.AuctionManager.getInstance();
    private final String sessionId;
    private volatile boolean running = true;
    private User authenticatedUser;
    private ObjectOutputStream outputStream;
    private ObjectInputStream inputStream;

    public ClientSession(
            Socket socket,
            AuthenticationService authenticationService,
            AuctionService auctionService,
            AuctionEventPublisher eventPublisher) {
        this.socket = socket;
        this.authenticationService = authenticationService;
        this.auctionService = auctionService;
        this.eventPublisher = eventPublisher;
        this.sessionId = socket.getRemoteSocketAddress() + "#" + System.identityHashCode(this);
    }

    @Override
    public void run() {
        auctionManager.registerSession(sessionId);
        eventPublisher.subscribeGlobal(this);
        try {
            outputStream = new ObjectOutputStream(socket.getOutputStream());
            outputStream.flush();
            inputStream = new ObjectInputStream(socket.getInputStream());

            while (running) {
                Object value = inputStream.readObject();
                if (!(value instanceof ClientRequest<?> request)) {
                    send(ServerResponse.error("Unsupported request payload."));
                    continue;
                }
                handleRequest(request);
            }
        } catch (EOFException | SocketException ignored) {
            closeQuietly();
        } catch (Exception exception) {
            send(ServerResponse.error(exception.getMessage()));
            closeQuietly();
        } finally {
            closeQuietly();
        }
    }

    @Override
    public void onAuctionEvent(ServerResponse<?> response) {
        if (authenticatedUser == null || !running) {
            return;
        }
        send(response);
    }

    // Dieu phoi request tu client den dung service/phuong thuc xu ly.
    private void handleRequest(ClientRequest<?> request) {
        CommandType commandType = request.getCommandType();
        if (commandType != CommandType.LOGIN && commandType != CommandType.REGISTER && authenticatedUser == null) {
            send(ServerResponse.error("Please login first."));
            return;
        }

        //phân loại request
        switch (commandType) {
            case LOGIN -> handleLogin((LoginRequest) request.getPayload());
            case REGISTER -> handleRegister((RegisterRequest) request.getPayload());
            case LOAD_DASHBOARD -> handleDashboard((DashboardRequest) request.getPayload());
            case LOAD_AUCTION_DETAILS -> handleAuctionSelection((AuctionSelectionRequest) request.getPayload());
            case SUBSCRIBE_AUCTION -> handleSubscribe((AuctionSubscriptionRequest) request.getPayload());
            case PLACE_BID -> handleBid((BidRequest) request.getPayload());
            case SET_AUTO_BID -> handleAutoBid((AutoBidRequest) request.getPayload());
            case CREATE_AUCTION -> handleCreateAuction((CreateAuctionRequest) request.getPayload());
            case UPDATE_AUCTION -> handleUpdateAuction((UpdateAuctionRequest) request.getPayload());
            case DELETE_AUCTION -> handleDeleteAuction((AuctionActionRequest) request.getPayload());
            case START_AUCTION -> handleStartAuction((AuctionActionRequest) request.getPayload());
            case FINISH_AUCTION -> handleFinishAuction((AuctionActionRequest) request.getPayload());
            case MARK_PAID -> handleMarkPaid((AuctionActionRequest) request.getPayload());
            case CANCEL_AUCTION -> handleCancelAuction((AuctionActionRequest) request.getPayload());
            case DEPOSIT_FUNDS -> handleDepositFunds((DepositFundsRequest) request.getPayload());
            case PAY_AUCTION -> handlePayAuction((AuctionActionRequest) request.getPayload());
            case LOGOUT -> running = false;
        }
    }

    private void handleLogin(LoginRequest payload) {
        authenticatedUser = authenticationService.login(payload.username(), payload.password());
        UserView view = auctionService.loadDashboard(authenticatedUser.getId()).currentUser();
        send(ServerResponse.success("Login successful.", view));
    }

    // Register tra ve UserView de client co the hien thi ket qua ngay tren man hinh dang ky.
    private void handleRegister(RegisterRequest payload) {
        User registeredUser = authenticationService.register(payload);
        authenticatedUser = registeredUser;
        UserView view = auctionService.loadDashboard(registeredUser.getId()).currentUser();
        send(ServerResponse.success("Account created successfully.", view));
    }

    private void handleDashboard(DashboardRequest payload) {
        DashboardRequest normalizedRequest = payload.withUserId(authenticatedUser.getId());
        DashboardView dashboard = auctionService.loadDashboard(normalizedRequest.userId());
        send(ServerResponse.success("Dashboard loaded.", dashboard));
    }

    private void handleAuctionSelection(AuctionSelectionRequest payload) {
        AuctionView auction = auctionService.loadAuction(payload.auctionId());
        send(ServerResponse.success("Auction loaded.", auction));
    }

    private void handleSubscribe(AuctionSubscriptionRequest payload) {
        AuctionSubscriptionRequest normalizedRequest = payload.withViewerId(authenticatedUser.getId());
        eventPublisher.subscribeToAuction(normalizedRequest.auctionId(), this);
        AuctionView auction = auctionService.loadAuction(normalizedRequest.auctionId());
        send(ServerResponse.success("Subscribed to auction.", auction));
    }

    private void handleBid(BidRequest payload) {
        BidRequest normalizedRequest = payload.withBidderId(authenticatedUser.getId());
        AuctionView auction = auctionService.placeBid(normalizedRequest);
        send(ServerResponse.success("Bid accepted.", auction));
    }

    private void handleAutoBid(AutoBidRequest payload) {
        AutoBidRequest normalizedRequest = payload.withBidderId(authenticatedUser.getId());
        AuctionView auction = auctionService.setAutoBid(normalizedRequest);
        send(ServerResponse.success("Auto-bid configured.", auction));
    }

    private void handleCreateAuction(CreateAuctionRequest payload) {
        CreateAuctionRequest normalizedRequest = payload.withSellerId(authenticatedUser.getId());
        AuctionView auction = auctionService.createAuction(normalizedRequest);
        send(ServerResponse.success("Auction created.", auction));
    }

    private void handleUpdateAuction(UpdateAuctionRequest payload) {
        UpdateAuctionRequest normalizedRequest = payload.withSellerId(authenticatedUser.getId());
        AuctionView auction = auctionService.updateAuction(normalizedRequest);
        send(ServerResponse.success("Auction updated.", auction));
    }

    private void handleDeleteAuction(AuctionActionRequest payload) {
        AuctionActionRequest normalizedRequest = payload.withActorId(authenticatedUser.getId());
        auctionService.deleteAuction(normalizedRequest);
        send(ServerResponse.success("Auction deleted.", "deleted"));
    }

    private void handleStartAuction(AuctionActionRequest payload) {
        AuctionActionRequest normalizedRequest = payload.withActorId(authenticatedUser.getId());
        AuctionView auction = auctionService.startAuction(normalizedRequest);
        send(ServerResponse.success("Auction started.", auction));
    }

    private void handleFinishAuction(AuctionActionRequest payload) {
        AuctionActionRequest normalizedRequest = payload.withActorId(authenticatedUser.getId());
        AuctionView auction = auctionService.finishAuction(normalizedRequest);
        send(ServerResponse.success("Auction finished.", auction));
    }

    private void handleMarkPaid(AuctionActionRequest payload) {
        AuctionActionRequest normalizedRequest = payload.withActorId(authenticatedUser.getId());
        AuctionView auction = auctionService.markPaid(normalizedRequest);
        send(ServerResponse.success("Auction marked paid.", auction));
    }

    private void handleCancelAuction(AuctionActionRequest payload) {
        AuctionActionRequest normalizedRequest = payload.withActorId(authenticatedUser.getId());
        AuctionView auction = auctionService.cancelAuction(normalizedRequest);
        send(ServerResponse.success("Auction canceled.", auction));
    }

    private void handleDepositFunds(DepositFundsRequest payload) {
        DepositFundsRequest normalizedRequest = payload.withUserId(authenticatedUser.getId());
        UserView user = auctionService.depositFunds(normalizedRequest);
        authenticatedUser = authenticationService.login(user.getId(), authenticatedUser.getPassword());
        send(ServerResponse.success("Balance updated.", user));
    }

    private void handlePayAuction(AuctionActionRequest payload) {
        AuctionActionRequest normalizedRequest = payload.withActorId(authenticatedUser.getId());
        AuctionView auction = auctionService.payAuction(normalizedRequest);
        send(ServerResponse.success("Auction paid successfully.", auction));
    }

    private synchronized void send(ServerResponse<?> response) {
        if (outputStream == null || !running) {
            return;
        }
        try {
            outputStream.writeObject(response);
            outputStream.flush();
            outputStream.reset();
        } catch (IOException ignored) {
            closeQuietly();
        }
    }

    private void closeQuietly() {
        running = false;
        eventPublisher.unsubscribe(this);
        auctionManager.unregisterSession(sessionId);
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }
}
