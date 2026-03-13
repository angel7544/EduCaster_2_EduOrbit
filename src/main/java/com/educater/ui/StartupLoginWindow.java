package com.educater.ui;

import com.educater.auth.AuthService;
import javafx.animation.FadeTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

import java.util.function.Consumer;

public class StartupLoginWindow {

    private final AuthService auth;
    private final Consumer<String> onLoginSuccess;

    public StartupLoginWindow(AuthService auth, Consumer<String> onLoginSuccess) {
        this.auth = auth;
        this.onLoginSuccess = onLoginSuccess;
    }

    public void showAtStartup(Window owner) {

        Stage stage = new Stage();
        stage.setTitle("EduCaster Live – Login");
        if (owner != null) stage.initOwner(owner);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setResizable(false);
        
        try {
            stage.getIcons().add(new Image(getClass().getResource("/images/educaster.png").toExternalForm()));
        } catch (Exception ignored) { }

        // ---- BRANDING LOGO (BR31Tech) ----
        ImageView brandingLogo = new ImageView();
        try {
            brandingLogo.setImage(new Image(getClass().getResource("/images/educaster.png").toExternalForm()));
        } catch (Exception ignored) {}
        brandingLogo.setFitWidth(120);
        brandingLogo.setPreserveRatio(true);
        
        // Add a subtle shadow to the logo
        brandingLogo.setEffect(new DropShadow(10, Color.rgb(0,0,0,0.2)));

        Label title = new Label("EduCaster Live");
        title.setStyle("-fx-font-family: 'Segoe UI', sans-serif; -fx-font-size: 28px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");

        Label subtitle = new Label("Streaming & Asset Management Suite");
        subtitle.setStyle("-fx-font-family: 'Segoe UI', sans-serif; -fx-font-size: 14px; -fx-text-fill: #7f8c8d;");

        // ---- LOGIN BUTTONS ----
        Button teacherBtn = createStyledButton("Teacher Login", "#3498db");
        Button adminBtn = createStyledButton("Admin Login", "#e74c3c");

        HBox buttons = new HBox(20, teacherBtn, adminBtn);
        buttons.setAlignment(Pos.CENTER);
        buttons.setPadding(new Insets(20, 0, 20, 0));

        // ---- PARTNER LOGOS ----
        Label partnerLabel = new Label("Powered by");
        partnerLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #bdc3c7;");
        
        javafx.scene.layout.FlowPane cdnFlow = new javafx.scene.layout.FlowPane();
        cdnFlow.setAlignment(Pos.CENTER);
        cdnFlow.setHgap(20);
        cdnFlow.setVgap(10);
        cdnFlow.getChildren().addAll(
            safeImage("/images/mux.png", 60),
            safeImage("/images/cloudflare_logo.png", 60),
            safeImage("/images/100ms.png", 60),
            safeImage("/images/cloudinary.png", 60)
        );
        cdnFlow.setPrefWrapLength(350);
        cdnFlow.setPadding(new Insets(10));

        // ---- FOOTER ----
        Label footerText = new Label("© 2026 BR-31 Technologies");
        footerText.setStyle("-fx-font-size: 11px; -fx-text-fill: #95a5a6;");
        
        Hyperlink footerLink = new Hyperlink("br31tech.live");
        footerLink.setStyle("-fx-font-size: 11px; -fx-border-color: transparent;");
        footerLink.setOnAction(e -> {
            try {
                if (java.awt.Desktop.isDesktopSupported()) {
                    java.awt.Desktop.getDesktop().browse(new java.net.URI("https://br31tech.live"));
                }
            } catch (Exception ignored) {}
        });
        
        HBox footer = new HBox(2, footerText, new Label("•"), footerLink);
        footer.setAlignment(Pos.CENTER);
        footer.setPadding(new Insets(10, 0, 0, 0));

        // ---- ROOT LAYOUT ----
        VBox mainContent = new VBox(15,
                brandingLogo,
                title,
                subtitle,
                buttons,
                partnerLabel,
                cdnFlow,
                footer
        );
        mainContent.setAlignment(Pos.CENTER);
        mainContent.setPadding(new Insets(40));
        mainContent.setStyle("-fx-background-color: white;");

        // Add fade in animation
        FadeTransition ft = new FadeTransition(Duration.millis(800), mainContent);
        ft.setFromValue(0.0);
        ft.setToValue(1.0);
        ft.play();

        Scene scene = new Scene(mainContent, 550, 500);
        stage.setScene(scene);

        // ---- BUTTON ACTIONS ----
        teacherBtn.setOnAction(e -> {
            new TeacherLoginWindow(auth, email -> {
                if (onLoginSuccess != null) onLoginSuccess.accept(email);
                stage.close();
            }).show();
        });

        adminBtn.setOnAction(e -> {
            new AdminLoginWindow(auth, email -> {
                if (onLoginSuccess != null) onLoginSuccess.accept(email);
                stage.close();
            }).show();
        });

        stage.showAndWait();
    }
    
    private Button createStyledButton(String text, String color) {
        Button btn = new Button(text);
        btn.setPrefWidth(140);
        btn.setPrefHeight(40);
        btn.setStyle(
            "-fx-background-color: " + color + ";" +
            "-fx-text-fill: white;" +
            "-fx-font-size: 14px;" +
            "-fx-font-weight: bold;" +
            "-fx-background-radius: 5;" +
            "-fx-cursor: hand;"
        );
        // Hover effect
        btn.setOnMouseEntered(e -> btn.setStyle(
            "-fx-background-color: derive(" + color + ", -10%);" +
            "-fx-text-fill: white;" +
            "-fx-font-size: 14px;" +
            "-fx-font-weight: bold;" +
            "-fx-background-radius: 5;" +
            "-fx-cursor: hand;"
        ));
        btn.setOnMouseExited(e -> btn.setStyle(
            "-fx-background-color: " + color + ";" +
            "-fx-text-fill: white;" +
            "-fx-font-size: 14px;" +
            "-fx-font-weight: bold;" +
            "-fx-background-radius: 5;" +
            "-fx-cursor: hand;"
        ));
        return btn;
    }

    private Node safeImage(String resourcePath, int size) {
        try {
            var url = getClass().getResource(resourcePath);
            if (url == null) {
                return new Label("");
            }
            ImageView iv = new ImageView(new Image(url.toExternalForm()));
            iv.setFitWidth(size);
            iv.setPreserveRatio(true);
            iv.setOpacity(0.8); // Slightly transparent logos
            return iv;
        } catch (Exception ex) {
            return new Label("");
        }
    }
}
