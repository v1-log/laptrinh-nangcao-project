package com.auctionhouse.client.service;


import com.auction.model.exception.ClientConnectionException;
import com.auction.model.exception.ClientProtocolException;
import com.auction.model.exception.ServerResponseException;
import com.auction.shared.dto.AuctionView;
import com.auction.shared.dto.DashboardView;
import com.auction.shared.dto.UserView;
import com.auction.shared.enums.CommandType;
import com.auction.shared.enums.ResponseStatus;
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
import com.auction.shared.protocol.LogoutRequest;
import com.auction.shared.protocol.RegisterRequest;
import com.auction.shared.protocol.ServerResponse;
import com.auction.shared.protocol.UpdateAuctionRequest;

import java.io.Closeable;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

public final class AuctionClientService implements Closeable {
    private final String host;
    private final int port;
    private final BlockingQueue<ServerResponse<?>> responses = new LinkedBlockingQueue<>();
    private volatile Consumer<ServerResponse<?>> eventListener = response -> {
    };
    private Socket socket;
    private ObjectOutputStream outputStream;
    private ObjectInputStream inputStream;
    private CompletableFuture<Void> listenerLoop;
    private volatile String authenticatedUserId = "client";
    private volatile boolean closingConnection;
    private volatile int connectionVersion;

    public AuctionClientService(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void setEventListener(Consumer<ServerResponse<?>> eventListener) {
        this.eventListener = eventListener == null ? response -> {
        } : eventListener;
    }
    // tạo loginRequest gửi cho sever và đợi sever trả về respone
    public UserView login(String username, String password) {
        UserView userView = expectPayload(sendAndAwait(new ClientRequest<>(
                CommandType.LOGIN,
                new LoginRequest(username, password))), UserView.class);
        authenticatedUserId = userView.getId();
        return userView;
    }

    public UserView register(RegisterRequest request) {
        UserView userView = expectPayload(sendAndAwait(new ClientRequest<>(CommandType.REGISTER, request)), UserView.class);
        authenticatedUserId = userView.getId();
        return userView;
    }


    public DashboardView loadDashboard(String userId) {
        return expectPayload(sendAndAwait(new ClientRequest<>(
                CommandType.LOAD_DASHBOARD,
                new DashboardRequest(userId))), DashboardView.class);

    }

    public AuctionView loadAuction(String auctionId) {
        return expectPayload(sendAndAwait(new ClientRequest<>(
                CommandType.LOAD_AUCTION_DETAILS,
                new AuctionSelectionRequest(auctionId))), AuctionView.class);
    }

    public AuctionView subscribe(String auctionId, String viewerId) {
        return expectPayload(sendAndAwait(new ClientRequest<>(
                CommandType.SUBSCRIBE_AUCTION,
                new AuctionSubscriptionRequest(auctionId, viewerId))), AuctionView.class);
    }

    public AuctionView placeBid(BidRequest request) {
        return expectPayload(sendAndAwait(new ClientRequest<>(CommandType.PLACE_BID, request)), AuctionView.class);
    }

    public AuctionView setAutoBid(AutoBidRequest request) {
        return expectPayload(sendAndAwait(new ClientRequest<>(CommandType.SET_AUTO_BID, request)), AuctionView.class);
    }

    public AuctionView createAuction(CreateAuctionRequest request) {
        return expectPayload(sendAndAwait(new ClientRequest<>(CommandType.CREATE_AUCTION, request)), AuctionView.class);
    }

    public AuctionView updateAuction(UpdateAuctionRequest request) {
        return expectPayload(sendAndAwait(new ClientRequest<>(CommandType.UPDATE_AUCTION, request)), AuctionView.class);
    }

    public void deleteAuction(AuctionActionRequest request) {
        sendAndAwait(new ClientRequest<>(CommandType.DELETE_AUCTION, request));
    }

    public AuctionView startAuction(AuctionActionRequest request) {
        return expectPayload(sendAndAwait(new ClientRequest<>(CommandType.START_AUCTION, request)), AuctionView.class);
    }

    public AuctionView finishAuction(AuctionActionRequest request) {
        return expectPayload(sendAndAwait(new ClientRequest<>(CommandType.FINISH_AUCTION, request)), AuctionView.class);
    }

    public AuctionView markPaid(AuctionActionRequest request) {
        return expectPayload(sendAndAwait(new ClientRequest<>(CommandType.MARK_PAID, request)), AuctionView.class);
    }

    public AuctionView cancelAuction(AuctionActionRequest request) {
        return expectPayload(sendAndAwait(new ClientRequest<>(CommandType.CANCEL_AUCTION, request)), AuctionView.class);
    }

    public UserView depositFunds(double amount) {
        return expectPayload(sendAndAwait(new ClientRequest<>(
                CommandType.DEPOSIT_FUNDS,
                new DepositFundsRequest(authenticatedUserId, amount))), UserView.class);
    }

    public AuctionView payAuction(String auctionId) {
        return expectPayload(sendAndAwait(new ClientRequest<>(
                CommandType.PAY_AUCTION,
                new AuctionActionRequest(auctionId, authenticatedUserId))), AuctionView.class);
    }

    public synchronized void logout() {
        closingConnection = true;
        try {
            if (outputStream != null && socket != null && socket.isConnected() && !socket.isClosed()) {
                outputStream.writeObject(new ClientRequest<>(CommandType.LOGOUT, new LogoutRequest(authenticatedUserId)));
                outputStream.flush();
                outputStream.reset();
            }
        } catch (Exception ignored) {
        } finally {
            authenticatedUserId = "client";
            eventListener = response -> {
            };
            responses.clear();
            closeResourcesQuietly();
        }
    }

    //chỉ cho 1thread chạy 1 lúc
    private synchronized ServerResponse<?> sendAndAwait(ClientRequest<?> request) {
        ensureConnected(); // confirm Socket còn connect
        try {

            outputStream.writeObject(request);//gửi request qa socket

            outputStream.flush();//ép buffer gửi request cho sever ngay
            outputStream.reset();//xóa cache obj để tránh gửi dữ liệu sai

            ServerResponse<?> response = responses.take();  // là nơi đợi response sever gửi về

            //check lỗi rồi throw ra một java exception
            if (response.getStatus() == ResponseStatus.ERROR) {
                throw new ServerResponseException(response.getMessage());
            }
            return response; // không sai thì trả lại response
        } catch (IOException exception) {
            //bắt lỗi mạng như mất kết nối , socket lỗi hay sever tắt
            throw new ClientConnectionException("Network error: " + exception.getMessage(), exception);
        } catch (InterruptedException exception) {//bắt lỗi bị interrupt
            Thread.currentThread().interrupt();
            throw new ClientConnectionException("Interrupted while waiting for server response.", exception);
        }
    }

    //đảm bảo client đang kết nối với sever
    private void ensureConnected() {
        //nếu socket đã được tạo và kết nối và chưa đóng thì ok
        if (socket != null && socket.isConnected() && !socket.isClosed()) {
            return;
        }
        try {
            int listenerConnectionVersion = ++connectionVersion;
            responses.clear();// xóa để tránh request mới nhận nhầm response cũ
            closingConnection = false;
            socket = new Socket(host, port);//tạo socket
            // serialization ( tuần tự hóa ) biến các đối tượng thành các chuỗi byte đến buffer trên socket máy nhận
            outputStream = new ObjectOutputStream(socket.getOutputStream());
            outputStream.flush();//ép gưỉ đi luôn
            inputStream = new ObjectInputStream(socket.getInputStream());//đọc response từ sever
            //tạo background thread , đọc response liên tục
            listenerLoop = CompletableFuture.runAsync(() -> listenForResponses(listenerConnectionVersion));
        } catch (IOException exception) { //bắt lỗi mạng
            throw new ClientConnectionException("Cannot connect to auction server at " + host + ":" + port, exception);
        }
    }

    //UI update realtime như giá bid đổi , winner đổi...
    private void listenForResponses(int listenerConnectionVersion) {
        try {
            while (socket != null && !socket.isClosed()) {  //nếu socket còn chưa bị đóng và tồn tại thì lặp lại vô hạn
                Object payload = inputStream.readObject();  //client chờ sever gửi dữ liệu
                if (!(payload instanceof ServerResponse<?> response)) { //nếu khong phải sever response thì bỏ qua
                    continue;
                }
                //event khác response là client không cần request trước , sever tự push
                if (response.getStatus() == ResponseStatus.EVENT) {
                    eventListener.accept(response); // nếu là event thì cập nhật giao diện ngay lập tức
                } else {
                    responses.offer(response);
                }
            }
        } catch (Exception ignored) {
            if (!closingConnection && listenerConnectionVersion == connectionVersion) {
                responses.offer(ServerResponse.error("Connection to auction server was closed."));
            }
        } finally {
            if (listenerConnectionVersion == connectionVersion) {
                closeResourcesQuietly();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T expectPayload(ServerResponse<?> response, Class<T> type) {
        Object payload = response.getPayload(); //lấy dữ liệu sever gửi về
        if (!type.isInstance(payload)) {
            throw new ClientProtocolException("Unexpected response payload: " + payload); //sai kiểu thì ném ra lỗi
        }
        return (T) payload;
    }

    @Override
    public synchronized void close() throws IOException { //chỉ cho phép 1 thread dùng close() tại 1 thời điểm
        logout();
    }

    private synchronized void closeResourcesQuietly() {
        try {
            if (inputStream != null) {  //ngừng đọc dữ liệu từ sever
                inputStream.close();
            }
        } catch (IOException ignored) { //có lỗi thì bỏ qua
        }
        try {
            if (outputStream != null) { //ngừng gửi dữ liệu cho sever
                outputStream.close();
            }
        } catch (IOException ignored) {
        }
        try {
            if (socket != null) {       //ngắt kết nối socket
                socket.close();
            }
        } catch (IOException ignored) {
        }
        inputStream = null;             //set lại trạng thái sau khi logout
        outputStream = null;
        socket = null;
    }
}

