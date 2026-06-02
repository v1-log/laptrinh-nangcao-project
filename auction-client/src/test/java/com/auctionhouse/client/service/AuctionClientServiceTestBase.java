package com.auctionhouse.client.service;

import com.auction.shared.enums.CommandType;
import com.auction.shared.protocol.ClientRequest;
import com.auction.shared.protocol.ServerResponse;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
// khởi tạo một fake server để test client, server này sẽ trả về các response đã được định nghĩa trước cho client khi nhận được request từ client, 
// đồng thời lưu lại các request mà nó nhận được để test sau này
abstract class AuctionClientServiceTestBase {
    static final String LOCALHOST = "127.0.0.1";

    static final class FakeAuctionServer implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final Queue<List<ServerResponse<? extends Serializable>>> responseScripts =
                new ConcurrentLinkedQueue<>();
        private final BlockingQueue<ClientRequest<?>> requests = new LinkedBlockingQueue<>();
        private final Thread thread;

        @SafeVarargs
        FakeAuctionServer(List<ServerResponse<? extends Serializable>>... responseScripts) throws IOException {
            this.serverSocket = new ServerSocket(0, 50, InetAddress.getByName(LOCALHOST));
            this.responseScripts.addAll(List.of(responseScripts));
            this.thread = new Thread(this::acceptConnections, "fake-auction-server");
            this.thread.setDaemon(true);
            this.thread.start();
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        ClientRequest<?> awaitRequest() throws InterruptedException {
            ClientRequest<?> request = requests.poll(2, TimeUnit.SECONDS);
            assertTrue(request != null, "Expected fake server to receive a client request");
            return request;
        }

        private void acceptConnections() {
            while (!serverSocket.isClosed()) {
                try (Socket socket = serverSocket.accept()) {
                    handleConnection(socket);
                } catch (SocketException exception) {
                    return;
                } catch (IOException exception) {
                    return;
                }
            }
        }

        private void handleConnection(Socket socket) throws IOException {
            ObjectOutputStream outputStream = new ObjectOutputStream(socket.getOutputStream());
            outputStream.flush();
            ObjectInputStream inputStream = new ObjectInputStream(socket.getInputStream());

            while (!socket.isClosed()) {
                Object payload;
                try {
                    payload = inputStream.readObject();
                } catch (EOFException exception) {
                    return;
                } catch (ClassNotFoundException exception) {
                    throw new IOException("Unable to read client request.", exception);
                }

                ClientRequest<?> request = assertInstanceOf(ClientRequest.class, payload);
                requests.offer(request);
                if (request.getCommandType() == CommandType.LOGOUT) {
                    continue;
                }

                List<ServerResponse<? extends Serializable>> responses = responseScripts.poll();
                if (responses == null) {
                    continue;
                }
                for (ServerResponse<? extends Serializable> response : responses) {
                    outputStream.writeObject(response);
                    outputStream.flush();
                    outputStream.reset();
                }
            }
        }

        @Override
        public void close() throws Exception {
            serverSocket.close();
            thread.join(2_000);
        }
    }
}
