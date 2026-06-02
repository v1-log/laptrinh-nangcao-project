package com.auction.shared.protocol;

import com.auction.model.AuctionStatus;
import com.auction.shared.dto.AuctionView;
import com.auction.shared.dto.BidTransactionView;
import com.auction.shared.dto.BidView;
import com.auction.shared.dto.ItemView;
import com.auction.shared.enums.AuctionEventType;
import com.auction.shared.enums.CommandType;
import com.auction.shared.enums.ResponseStatus;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
class ProtocolSerializationRoundTripTest {
    // test này đảm bảo rằng các lớp giao thức phức tạp, như ClientRequest với payload là một BidRequest, 
    // có thể được tuần tự hóa và giải tuần tự hóa mà không mất dữ liệu hoặc cấu trúc nào. 
    @Test
    void clientRequest_withBidRequest_roundTripPreservesPayload() throws Exception {
    
        ClientRequest<BidRequest> request = new ClientRequest<>(
                CommandType.PLACE_BID,
                new BidRequest("A1001", "bidder-1", 250.5));

        Object restored = roundTrip(request);
        ClientRequest<?> restoredRequest = assertInstanceOf(ClientRequest.class, restored);

        assertEquals(CommandType.PLACE_BID, restoredRequest.getCommandType());
        BidRequest payload = assertInstanceOf(BidRequest.class, restoredRequest.getPayload());
        assertEquals("A1001", payload.auctionId());
        assertEquals("bidder-1", payload.bidderId());
        assertEquals(250.5, payload.amount());
    }
    // test này đảm bảo rằng các lớp phản hồi phức tạp, như ServerResponse với payload là một AuctionView, 
    // có thể được tuần tự hóa và giải tuần tự hóa mà không mất dữ liệu hoặc cấu trúc nào. 
    @Test
    void serverResponse_withNestedAuctionView_roundTripPreservesFields() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        AuctionView auctionView = new AuctionView(
                "A1001",
                "seller-1",
                "Seller One",
                AuctionStatus.RUNNING,
                now.minusMinutes(3),
                now.plusMinutes(12),
                50.0,
                1250.0,
                new ItemView("I2001", "Laptop", "Gaming laptop", 1000, 1200, "Electronics", "Warranty: 24 months"),
                new BidView("bidder-1", "Bidder One", 1200, now.minusMinutes(1)),
                List.of(new BidTransactionView("bidder-1", "Bidder One", 1200, now.minusMinutes(1))),
                "No winner yet");

        ServerResponse<AuctionView> response = ServerResponse.event(
            AuctionEventType.BID_PLACED,
                "New bid received",
                auctionView);

        Object restored = roundTrip(response);
        ServerResponse<?> restoredResponse = assertInstanceOf(ServerResponse.class, restored);

        assertEquals(ResponseStatus.EVENT, restoredResponse.getStatus());
        assertEquals(AuctionEventType.BID_PLACED, restoredResponse.getEventType());
        assertEquals("New bid received", restoredResponse.getMessage());

        AuctionView payload = assertInstanceOf(AuctionView.class, restoredResponse.getPayload());
        assertEquals("A1001", payload.auctionId());
        assertEquals("Laptop", payload.item().name());
        assertEquals(1, payload.bidHistory().size());
    }
    // test này đảm bảo rằng các lớp yêu cầu phức tạp, như RegisterRequest với nhiều trường khác nhau,
    // có thể được tuần tự hóa và giải tuần tự hóa mà không mất dữ liệu hoặc cấu trúc nào.
    @Test
    void registerRequest_roundTripPreservesData() throws Exception {
        RegisterRequest request = new RegisterRequest("new-user", "pw1234", "New User", null, null);

        Object restored = roundTrip(request);
        RegisterRequest restoredRequest = assertInstanceOf(RegisterRequest.class, restored);

        assertEquals("new-user", restoredRequest.username());
        assertEquals("pw1234", restoredRequest.password());
        assertEquals("New User", restoredRequest.displayName());
        assertEquals(null, restoredRequest.role());
    }
    private Object roundTrip(Serializable value) throws IOException, ClassNotFoundException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(output)) {
            objectOutputStream.writeObject(value);
        }

        try (ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(output.toByteArray()))) {
            return objectInputStream.readObject();
        }
    }
}
