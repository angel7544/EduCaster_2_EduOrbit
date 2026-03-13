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

public class TeacherLoginWindow {
    private final AuthService auth;
    private final Consumer<String> onLoginSuccess;

    public TeacherLoginWindow(AuthService auth, Consumer<String> onLoginSuccess) {
        this.auth = auth;
        this.onLoginSuccess = onLoginSuccess;
    }

    public void show() {
        Stage stage = new Stage();
        stage.setTitle("Teacher Login");
        stage.initModality(Modality.APPLICATION_MODAL);
        try {
            stage.getIcons().add(new javafx.scene.image.Image(getClass().getResource("/images/educaster.png").toExternalForm()));
        } catch (Exception ignored) { }

        TextField emailField = new TextField();
        emailField.setPromptText("Email");
        PasswordField passField = new PasswordField();
        passField.setPromptText("Password");

        Button loginBtn = new Button("Login");
        Button cancelBtn = new Button("Cancel");
        HBox buttons = new HBox(8, loginBtn, cancelBtn);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        VBox root = new VBox(10, new Label("Teacher Login"), emailField, passField, buttons);
        root.setPadding(new Insets(12));

        loginBtn.setOnAction(e -> {
            try {
                String email = emailField.getText().trim();
                char[] pass = passField.getText().toCharArray();
                boolean ok = auth.loginTeacher(email, pass);
                if (ok) {
                    new Alert(Alert.AlertType.INFORMATION, "Login successful!").showAndWait();
                    if (onLoginSuccess != null) onLoginSuccess.accept(email);
                    stage.close();
                } else {
                    new Alert(Alert.AlertType.ERROR, "Invalid credentials").showAndWait();
                }
            } catch (Exception ex) {
                new Alert(Alert.AlertType.ERROR, "Error: " + ex.getMessage()).showAndWait();
            }
        });

        cancelBtn.setOnAction(e -> stage.close());

        stage.setScene(new Scene(root, 380, 200));
        stage.showAndWait();
    }
}