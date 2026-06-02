package com.auctionhouse.client.service;

import com.auction.model.exception.ClientConnectionException;
import com.auction.model.exception.ClientProtocolException;
import com.auction.model.exception.ServerResponseException;
import com.auction.shared.enums.CommandType;
import com.auction.shared.protocol.ServerResponse;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
// test này kiểm tra xem client có ném ra exception thích hợp khi server trả về response lỗi, trả về payload không đúng định dạng, 
// hoặc khi không thể kết nối đến server hay không
class AuctionClientServiceErrorTest extends AuctionClientServiceTestBase {
    @Test
    void serverErrorResponseThrowsServerResponseException() throws Exception {
        try (FakeAuctionServer server = new FakeAuctionServer(
                List.of(ServerResponse.error("invalid credentials")))) {
            AuctionClientService client = new AuctionClientService(LOCALHOST, server.port());

            ServerResponseException exception = assertThrows(
                    ServerResponseException.class,
                    () -> client.login("bidder", "wrong"));

            assertEquals("invalid credentials", exception.getMessage());
            assertEquals(CommandType.LOGIN, server.awaitRequest().getCommandType());

            client.logout();
        }
    }

    @Test
    void unexpectedPayloadTypeThrowsClientProtocolException() throws Exception {
        try (FakeAuctionServer server = new FakeAuctionServer(
                List.of(ServerResponse.success("wrong payload", "not a user")))) {
            AuctionClientService client = new AuctionClientService(LOCALHOST, server.port());

            ClientProtocolException exception = assertThrows(
                    ClientProtocolException.class,
                    () -> client.login("bidder", "secret"));

            assertTrue(exception.getMessage().contains("Unexpected response payload"));
            assertEquals(CommandType.LOGIN, server.awaitRequest().getCommandType());

            client.logout();
        }
    }

    @Test
    void cannotConnectThrowsClientConnectionException() throws Exception {
        int unusedPort;
        try (ServerSocket serverSocket = new ServerSocket(0, 50, InetAddress.getByName(LOCALHOST))) {
            unusedPort = serverSocket.getLocalPort();
        }
        AuctionClientService client = new AuctionClientService(LOCALHOST, unusedPort);

        ClientConnectionException exception = assertThrows(
                ClientConnectionException.class,
                () -> client.login("bidder", "secret"));

        assertTrue(exception.getMessage().contains("Cannot connect to auction server"));
    }
}
