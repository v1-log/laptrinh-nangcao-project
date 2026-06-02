package com.auctionhouse.client.service;

import com.auction.shared.dto.UserView;
import com.auction.shared.enums.AuctionEventType;
import com.auction.shared.enums.CommandType;
import com.auction.shared.enums.UserRole;
import com.auction.shared.protocol.ClientRequest;
import com.auction.shared.protocol.DepositFundsRequest;
import com.auction.shared.protocol.ServerResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
// test này kiểm tra xem client có nhận được event từ server mà không bị block khi đang chờ response cho request hay không, 
// và sau khi logout thì client có reset authenticated user về default user hay không
class AuctionClientServiceLifecycleTest extends AuctionClientServiceTestBase {
    private static final UserView LOGIN_USER = new UserView("user-1", "Login User", UserRole.BIDDER, 125.0);

    @Test
    void eventResponseIsDeliveredToListenerWithoutBlockingRequestResponse() throws Exception {
        ServerResponse<String> event = ServerResponse.event(
                AuctionEventType.BID_PLACED,
                "bid placed",
                "auction-1");
        CountDownLatch eventReceived = new CountDownLatch(1);

        try (FakeAuctionServer server = new FakeAuctionServer(
                List.of(event, ServerResponse.success("logged in", LOGIN_USER)))) {
            AuctionClientService client = new AuctionClientService(LOCALHOST, server.port());
            client.setEventListener(response -> {
                assertEquals(AuctionEventType.BID_PLACED, response.getEventType());
                assertEquals("auction-1", response.getPayload());
                eventReceived.countDown();
            });

            UserView user = client.login("bidder", "secret");

            assertEquals(LOGIN_USER, user);
            assertTrue(eventReceived.await(2, TimeUnit.SECONDS));
            assertEquals(CommandType.LOGIN, server.awaitRequest().getCommandType());

            client.logout();
        }
    }

    @Test
    void logoutResetsAuthenticatedUserForNewConnection() throws Exception {
        UserView defaultUser = new UserView("client", "Guest", UserRole.BIDDER, 10.0);
        try (FakeAuctionServer server = new FakeAuctionServer(
                List.of(ServerResponse.success("logged in", LOGIN_USER)),
                List.of(ServerResponse.success("deposited", defaultUser)))) {
            AuctionClientService client = new AuctionClientService(LOCALHOST, server.port());

            client.login("bidder", "secret");
            client.logout();
            client.depositFunds(10.0);

            assertEquals(CommandType.LOGIN, server.awaitRequest().getCommandType());
            assertEquals(CommandType.LOGOUT, server.awaitRequest().getCommandType());

            ClientRequest<?> depositRequest = server.awaitRequest();
            assertEquals(CommandType.DEPOSIT_FUNDS, depositRequest.getCommandType());
            DepositFundsRequest payload = assertInstanceOf(DepositFundsRequest.class, depositRequest.getPayload());
            assertEquals("client", payload.getUserId());

            client.logout();
        }
    }
}
