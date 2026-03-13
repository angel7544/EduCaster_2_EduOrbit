package com.educater.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class HelpWindow {

    public void show() {
        Stage stage = new Stage();
        stage.setTitle("EduCaster Help & Guide");
        stage.initModality(Modality.APPLICATION_MODAL);

        // Add Icon
        try {
            stage.getIcons().add(new Image(getClass().getResource("/images/educaster.png").toExternalForm()));
        } catch (Exception ignored) { }

        TabPane tabs = new TabPane();
        tabs.getTabs().addAll(
                createTab("Getting Started", buildGettingStarted()),
                createTab("OBS Setup", buildObsSetup()),
                createTab("R2 Storage", buildR2Setup()),
                createTab("Troubleshooting", buildTroubleshooting()),
                createTab("About", buildAbout())
        );
        tabs.setStyle("-fx-font-size: 14px;");

        Button closeBtn = new Button("Close");
        closeBtn.setStyle("-fx-background-color: #2c3e50; -fx-text-fill: white; -fx-font-weight: bold;");
        closeBtn.setOnAction(e -> stage.close());
        
        HBox buttonBox = new HBox(closeBtn);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        buttonBox.setPadding(new Insets(10));
        buttonBox.setStyle("-fx-background-color: #ecf0f1;");

        VBox root = new VBox(tabs, buttonBox);
        
        Scene scene = new Scene(root, 700, 600);
        stage.setScene(scene);
        stage.showAndWait();
    }

    private Tab createTab(String title, VBox content) {
        Tab tab = new Tab(title, content);
        tab.setClosable(false);
        content.setPadding(new Insets(20));
        content.setSpacing(15);
        content.setStyle("-fx-background-color: white;");
        return tab;
    }

    private VBox buildGettingStarted() {
        VBox box = new VBox();
        ScrollPane scroll = new ScrollPane(box);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent;");

        box.getChildren().addAll(
                header("Welcome to EduCaster Live"),
                paragraph("EduCaster Live is your comprehensive tool for managing live streams and video assets. Whether you are a teacher broadcasting a lesson or an admin managing content, this guide will help you get started."),
                
                subheader("How to Start Streaming"),
                step("1. Login", "Log in using your Teacher or Admin credentials provided by your institution."),
                step("2. Create Stream", "Navigate to the 'Streams' tab. Click 'Create Stream' to generate a new live stream instance."),
                step("3. Get Stream Details", "Once created, your stream will appear in the list. Right-click the row or select it and click 'Copy Details' to get your Stream Key and RTMP URL."),
                step("4. Configure OBS", "Open OBS Studio. Go to Settings > Stream. Select 'Custom' service. Paste the Server (RTMP URL) and Stream Key."),
                step("5. Go Live", "Start Streaming in OBS. Your stream will appear in the 'Preview' panel in EduCaster within 10-30 seconds."),
                step("6. Share", "Use the 'Get Public URL' button to copy the link to share with your students.")
        );
        
        // Wrap in a VBox again to return VBox compatible structure if needed, or just return the box
        // But since createTab expects VBox, we can return box, but if content overflows, we need scroll.
        // Let's change createTab to accept Node.
        return box; 
    }

    private VBox buildObsSetup() {
        VBox box = new VBox();
        box.getChildren().addAll(
                header("OBS Studio Configuration"),
                paragraph("OBS Studio is recommended for broadcasting. Follow these settings for optimal performance."),
                
                subheader("Stream Settings"),
                step("Service", "Select 'Custom...'"),
                step("Server", "rtmps://global-live.mux.com:443/app"),
                step("Stream Key", "Paste the key generated in EduCaster."),
                
                subheader("Output Settings (Recommended)"),
                step("Video Bitrate", "2500 Kbps - 4500 Kbps (depending on your upload speed)."),
                step("Keyframe Interval", "2 seconds (Critical for HLS streaming)."),
                step("Rate Control", "CBR (Constant Bitrate)."),
                step("Profile", "High or Main."),
                step("Tune", "Zerolatency (optional, for lower latency).")
        );
        return box;
    }
    
    private VBox buildR2Setup() {
        VBox box = new VBox();
        box.getChildren().addAll(
            header("Cloudflare R2 Storage"),
            paragraph("Cloudflare R2 provides affordable, S3-compatible object storage for your video files and assets."),
            
            subheader("Setup Instructions"),
            step("1. Create Bucket", "Log in to Cloudflare Dashboard > R2. Create a new bucket (e.g., 'educater-videos')."),
            step("2. API Tokens", "Go to R2 > Manage R2 API Tokens. Create a token with 'Object Read & Write' permissions."),
            step("3. Copy Credentials", "You will need: Account ID, Access Key ID, and Secret Access Key."),
            step("4. Configure App", "In EduCaster, go to Admin Settings > Cloudflare R2 Storage. Enter these details."),
            step("5. Public Access", "For students to view videos, you must enable Public Access or connect a Custom Domain to your bucket in Cloudflare settings."),
            
            subheader("Using the Uploader"),
            paragraph("Use the 'R2 Storage' tab to upload files. You can upload single files or entire folders. The uploader supports resumable uploads and large files.")
        );
        return box;
    }

    private VBox buildTroubleshooting() {
        VBox box = new VBox();
        box.getChildren().addAll(
                header("Troubleshooting Guide"),
                
                subheader("Common Issues"),
                step("Stream not starting?", "Check your internet connection. Ensure OBS is actually streaming (green square icon). Verify the Stream Key is correct."),
                step("Black screen in Preview?", "HLS streams have a natural delay of 10-30 seconds. Wait a moment. If it persists, check if Keyframe Interval is set to 2s in OBS."),
                step("Upload Fails?", "Check your R2 credentials. Ensure your internet didn't drop. Large files may take time; do not close the window until complete."),
                step("Login Issues?", "Contact your system administrator if your credentials are not working.")
        );
        return box;
    }

    private VBox buildAbout() {
        VBox box = new VBox();
        box.setAlignment(Pos.CENTER);
        box.setSpacing(20);
        
        try {
            ImageView logo = new ImageView(new Image(getClass().getResource("/images/educaster.png").toExternalForm()));
            logo.setFitWidth(150);
            logo.setPreserveRatio(true);
            box.getChildren().add(logo);
        } catch (Exception ignored) {}

        Label appName = new Label("EduCaster Live Desktop");
        appName.setFont(Font.font("System", FontWeight.BOLD, 24));
        
        Label version = new Label("Version 6.0.0");
        version.setFont(Font.font("System", 16));
        
        Label desc = new Label("A professional streaming and asset management tool for educators.");
        desc.setWrapText(true);
        
        Label credit = new Label("Powered by BR-31 Technologies");
        credit.setStyle("-fx-text-fill: #7f8c8d; -fx-font-style: italic;");
        
        Hyperlink website = new Hyperlink("Visit Website");
        website.setOnAction(e -> {
            try {
                java.awt.Desktop.getDesktop().browse(new java.net.URI("https://br31tech.live"));
            } catch (Exception ex) { }
        });

        box.getChildren().addAll(appName, version, desc, new Separator(), credit, website);
        return box;
    }

    private Label header(String text) {
        Label lbl = new Label(text);
        lbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 22));
        lbl.setStyle("-fx-text-fill: #2980b9;");
        return lbl;
    }
    
    private Label subheader(String text) {
        Label lbl = new Label(text);
        lbl.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 16));
        lbl.setStyle("-fx-text-fill: #34495e; -fx-padding: 10 0 5 0;");
        return lbl;
    }

    private Label paragraph(String text) {
        Label lbl = new Label(text);
        lbl.setWrapText(true);
        lbl.setStyle("-fx-font-size: 14px; -fx-text-fill: #555;");
        return lbl;
    }

    private VBox step(String title, String desc) {
        Label t = new Label("• " + title);
        t.setFont(Font.font("System", FontWeight.BOLD, 14));
        t.setStyle("-fx-text-fill: #2c3e50;");
        
        Label d = new Label(desc);
        d.setWrapText(true);
        d.setStyle("-fx-font-size: 13px; -fx-text-fill: #555; -fx-padding: 0 0 0 15;");
        
        return new VBox(2, t, d);
    }
}
