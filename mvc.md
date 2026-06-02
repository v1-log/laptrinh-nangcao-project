# Kiến trúc MVC của dự án

## 1. MODEL 📊
- Vị trí: `auction-shared/src/main/java/com/auction/model`
- Các lớp model đại diện cho dữ liệu:
  - `User.java` — Đối tượng người dùng
  - `Admin.java` — Quản trị viên
  - `Bidder.java` — Người tham gia đấu giá
  - `Seller.java` — Người bán
  - `Auction.java` — Đối tượng phiên đấu giá (core)
  - `Bid.java` — Thông tin giá đặt cược
  - `BidTransaction.java` — Giao dịch đấu giá
  - `AuctionStatus.java` — Trạng thái phiên đấu giá
  - `Item.java` — Mặt hàng (base class)
  - `Art.java` — Nghệ thuật
  - `Clothing.java` — Quần áo
  - `Electronics.java` — Điện tử
  - `ItemFactory.java` — Factory Pattern cho tạo mặt hàng
- Pattern hỗ trợ:
  - `Observer.java`, `Subject.java`, `BidderObserver.java` — Observer Pattern

## 2. VIEW 🎨
- Vị trí: `auction-client/src/main/java/com/auctionhouse/client/view`
- Chức năng: quản lý giao diện người dùng và giao tiếp với Controller để hiển thị dữ liệu.

## 3. CONTROLLER 🎮
- Vị trí: `auction-client/src/main/java/com/auctionhouse/client/controller`
- Các Controller xử lý logic UI và điều hướng:
  - `LoginController.java` — Quản lý đăng nhập
  - `RegisterController.java` — Quản lý đăng ký
  - `DashboardController.java` — Bảng điều khiển chính
  - `AuctionDetailController.java` — Chi tiết phiên đấu giá
  - `AccountController.java` — Quản lý tài khoản
  - `SellerController.java` — Quản lý người bán
- Trách nhiệm chính:
  - Xử lý sự kiện từ View
  - Gọi Service
  - Cập nhật Model

## 4. INTERACTOR (Service Layer) ⚙️
- Đặt trong hai tầng chính:
  - Client side:
    - `auction-client/src/main/java/com/auctionhouse/client/service/AuctionClientService.java`
    - Nhiệm vụ: xử lý logic nghiệp vụ phía client và gọi network tới server.
  - Server side:
    - `auction-server/src/main/java/com/auction/server/service/`
    - `AuctionService.java` — Quản lý logic đấu giá chính
    - `AuctionManager.java` — Quản lý trạng thái đấu giá
    - `AuthenticationService.java` — Xác thực người dùng
    - `AuctionObserverBridge.java` — Cầu nối Observer Pattern

## 5. Các tầng hỗ trợ khác 🔧
- Cấu trúc server: `auction-server/src/main/java/com/auction/server/`
  - `app/` — Application entry point
  - `bootstrap/` — Khởi động ứng dụng
  - `dao/` — Data Access Object (truy cập DB)
  - `domain/` — Domain logic
  - `event/` — Event handling
  - `mapper/` — DTO mapping
  - `network/` — Network communication
  - `service/` — Business logic (Interactor)

## 6. Tổng quan kiến trúc
```
┌─────────────────────────────────────────────┐
│          AUCTION CLIENT (JavaFX UI)         │
├─────────────────────────────────────────────┤
│  VIEW (view package) ←→ CONTROLLER (control)│
│        ↓ (gọi)                              │
│  INTERACTOR (AuctionClientService)          │
│        ↓ (network call)                     │
├─────────────────────────────────────────────┤
│      AUCTION SERVER (Backend)               │
├─────────────────────────────────────────────┤
│  INTERACTOR (Service Layer)                 │
│  ├─ AuctionService                          │
│  ├─ AuctionManager                          │
│  └─ AuthenticationService                   │
│        ↓ (sử dụng)                          │
│  DAO (Data Access) → Database               │
│        ↓ (ánh xạ)                           │
│  MODEL (Shared) ← Domain Objects            │
└─────────────────────────────────────────────┘
```
