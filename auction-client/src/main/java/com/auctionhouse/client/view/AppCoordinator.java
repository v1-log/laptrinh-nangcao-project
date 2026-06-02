package com.auctionhouse.client.view;

import com.auctionhouse.client.controller.AccountController;
import com.auctionhouse.client.controller.AuctionDetailController;
import com.auctionhouse.client.controller.DashboardController;
import com.auctionhouse.client.controller.LoginController;
import com.auctionhouse.client.controller.RegisterController;
import com.auctionhouse.client.controller.SellerController;
import com.auctionhouse.client.service.AuctionClientService;
import com.auction.shared.dto.AuctionView;
import com.auction.shared.dto.UserView;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
// là nơi điều phối việc chuyển màn hình
public final class AppCoordinator {
    private final Stage stage;
    private final AuctionClientService clientService;

    public AppCoordinator(Stage stage, AuctionClientService clientService) {
        this.stage = stage;
        this.clientService = clientService;
    }

    public void showLogin() throws IOException {
        FXMLLoader loader = new FXMLLoader(         //tạo máy đọc file
                getClass().getResource("/com/auctionhouse/client/LoginView.fxml")   //dẫn tới file fxml
        );
        Parent root = loader.load();//load file để hiển thị giao diện
        LoginController controller = loader.getController();    //lấy controller của login
        controller.init(this, clientService); // truyền coordinator và sever cho controller để xử lí logic

        showAuthScene(root);    //hiện màn hình
    }

    public void showRegister() throws IOException {
        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/com/auctionhouse/client/RegisterView.fxml")
        );
        Parent root = loader.load();
        RegisterController controller = loader.getController();
        controller.init(this, clientService);

        showAuthScene(root);
    }

    public void showDashboard(UserView user) throws IOException {
        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/com/auctionhouse/client/DashboardView.fxml")
        );
        Parent root = loader.load();
        DashboardController controller = loader.getController();
        controller.init(this, clientService, user);

        stage.setScene(new Scene(root, 1100, 750));          //set scene hiển thị
        stage.show(); //hiển thị màn hình theo scene vừa set
    }

    public void showAuctionDetail(UserView user, AuctionView auction) throws IOException {
        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/com/auctionhouse/client/AuctionDetailView.fxml")
        );
        Parent root = loader.load();
        AuctionDetailController controller = loader.getController();
        controller.init(this, clientService, user, auction);

        stage.setScene(new Scene(root, 1100, 750));
        stage.show();
    }

    public void showSeller(UserView user) throws IOException {
        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/com/auctionhouse/client/SellerView.fxml")
        );
        Parent root = loader.load();
        SellerController controller = loader.getController();
        controller.init(this, clientService, user);

        stage.setScene(new Scene(root, 1100, 750));
        stage.show();
    }

    public void showAccount(UserView user) throws IOException {
        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/com/auctionhouse/client/AccountView.fxml")
        );
        Parent root = loader.load();
        AccountController controller = loader.getController();
        controller.init(this, clientService, user);

        stage.setScene(new Scene(root, 1100, 750));
        stage.show();
    }

    private void showAuthScene(Parent root) {
        stage.setScene(new Scene(root, 920, 640));
        stage.setTitle("AuctionHub Client");
        stage.setMinWidth(900);
        stage.setMinHeight(620);
        stage.show();
    }
}

