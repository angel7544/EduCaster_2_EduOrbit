package com.educater.ui;

import com.educater.auth.AuthService;
import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class AdminLoginWindow {
    private final AuthService auth;
    private final Consumer<String> onLoginSuccess;

    public AdminLoginWindow(AuthService auth, Consumer<String> onLoginSuccess) {
        this.auth = auth;
        this.onLoginSuccess = onLoginSuccess;
    }

    public void show() {
        Stage stage = new Stage();
        stage.setTitle("Admin Login");
        stage.initModality(Modality.APPLICATION_MODAL);
        try {
            stage.getIcons().add(new javafx.scene.image.Image(getClass().getResource("/images/educaster.png").toExternalForm()));
        } catch (Exception ignored) { }

        TextField emailField = new TextField();
        emailField.setPromptText("Admin Email");
        PasswordField passField = new PasswordField();
        passField.setPromptText("Admin Password");

        Button loginBtn = new Button("Login");
        Button cancelBtn = new Button("Cancel");
        HBox buttons = new HBox(8, loginBtn, cancelBtn);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        VBox root = new VBox(10, new Label("Admin Login"), emailField, passField, buttons);
        root.setPadding(new Insets(12));

        loginBtn.setOnAction(e -> {
            String email = emailField.getText().trim();
            char[] pass = passField.getText().toCharArray();
            if (auth.isAdminLogin(email, pass)) {
                new Alert(Alert.AlertType.INFORMATION, "Admin login successful.").showAndWait();
                if (onLoginSuccess != null) onLoginSuccess.accept(email);
                stage.close();
            } else {
                new Alert(Alert.AlertType.ERROR, "Admin login failed").showAndWait();
            }
        });

        cancelBtn.setOnAction(e -> stage.close());

        stage.setScene(new Scene(root, 360, 180));
        stage.showAndWait();
    }
}