package com.auctionhouse.client.service;

import com.auction.shared.dto.UserView;
import com.auction.shared.enums.CommandType;
import com.auction.shared.enums.UserRole;
import com.auction.shared.protocol.ClientRequest;
import com.auction.shared.protocol.DepositFundsRequest;
import com.auction.shared.protocol.RegisterRequest;
import com.auction.shared.protocol.ServerResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
// test này kiểm tra xem client có sử dụng authenticated user cho các request sau khi login hoặc register thành công hay không
class AuctionClientServiceAuthTest extends AuctionClientServiceTestBase {
    private static final UserView LOGIN_USER = new UserView("user-1", "Login User", UserRole.BIDDER, 125.0);
    private static final UserView REGISTERED_USER =
            new UserView("seller-1", "Registered Seller", UserRole.SELLER, 0.0);

    @Test
    void loginSuccessUsesAuthenticatedUserForLaterRequests() throws Exception {
        try (FakeAuctionServer server = new FakeAuctionServer(
                List.of(ServerResponse.success("logged in", LOGIN_USER)),
                List.of(ServerResponse.success("deposited", LOGIN_USER)))) {
            AuctionClientService client = new AuctionClientService(LOCALHOST, server.port());

            UserView user = client.login("bidder", "secret");
            client.depositFunds(25.0);

            assertEquals(LOGIN_USER, user);
            assertEquals(CommandType.LOGIN, server.awaitRequest().getCommandType());

            ClientRequest<?> depositRequest = server.awaitRequest();
            assertEquals(CommandType.DEPOSIT_FUNDS, depositRequest.getCommandType());
            DepositFundsRequest payload = assertInstanceOf(DepositFundsRequest.class, depositRequest.getPayload());
            assertEquals("user-1", payload.getUserId());

            client.logout();
        }
    }

    @Test
    void registerSuccessUsesAuthenticatedUserForLaterRequests() throws Exception {
        try (FakeAuctionServer server = new FakeAuctionServer(
                List.of(ServerResponse.success("registered", REGISTERED_USER)),
                List.of(ServerResponse.success("deposited", REGISTERED_USER)))) {
            AuctionClientService client = new AuctionClientService(LOCALHOST, server.port());
            RegisterRequest request = new RegisterRequest(
                    "seller",
                    "secret",
                    "Registered Seller",
                    UserRole.SELLER,
                    "Gallery");

            UserView user = client.register(request);
            client.depositFunds(40.0);

            assertEquals(REGISTERED_USER, user);
            assertEquals(CommandType.REGISTER, server.awaitRequest().getCommandType());

            ClientRequest<?> depositRequest = server.awaitRequest();
            DepositFundsRequest payload = assertInstanceOf(DepositFundsRequest.class, depositRequest.getPayload());
            assertEquals("seller-1", payload.getUserId());

            client.logout();
        }
    }
}
