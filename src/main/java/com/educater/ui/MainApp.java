package com.educater.ui;

import com.educater.auth.AuthService;
import com.educater.db.MongoService;
import com.educater.config.ConfigService;
import com.educater.net.NetUtil;
import com.educater.mux.JwtUtil;
import com.educater.mux.LiveStreamInfo;
import com.educater.mux.MuxApi;
import com.educater.model.StreamRecord;
import com.educater.model.UploadRecord;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import javafx.scene.control.Hyperlink;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;



import java.awt.Desktop;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainApp extends Application {
    private MongoService mongo;
    private AuthService auth;
    private MuxApi mux;

    private String currentEmail = null;
    private String currentPlaybackId = null;
    private String currentLiveId = null;

    private ObservableList<StreamRecord> streamItems = FXCollections.observableArrayList();
    private TableView<StreamRecord> streamTable = new TableView<>();
    private ObservableList<UploadRecord> uploadItems = FXCollections.observableArrayList();
    private ListView<UploadRecord> uploadList = new ListView<>();
    private WebView webView; // lazily initialized after JavaFX runtime starts
    private Label viewerCountLabel = new Label("Viewers: -");

    private boolean darkMode = false;
    private Button adminSettingsBtn;

    private TabPane tabs;
    private Tab r2Tab;
    private VBox r2Container;
    private VBox r2LoginView;
    private Pane r2MainView;
    
    private com.educater.r2.R2Service r2Service;

    @Override
    public void start(Stage stage) {
        try {
            mongo = new MongoService();
        } catch (Exception ex) {
            mongo = null;
        }
        auth = new com.educater.auth.AuthService(mongo);
        mux = new MuxApi();

        BorderPane root = new BorderPane();
        root.setTop(buildLoginPanel());
        root.setCenter(buildCenterPanel());
        root.setRight(buildRightPanel());

        Scene scene = new Scene(root, 1100, 700);
        stage.setTitle("EduCaster Live – Mux Desktop");
        try {
            stage.getIcons().add(new Image(getClass().getResource("/images/educaster.png").toExternalForm()));
        } catch (Exception ignored) { }
        stage.setScene(scene);
        stage.show();
        // Show login window on startup
        new StartupLoginWindow(auth, email -> {
            currentEmail = email;
            refreshStreams();
            refreshUploads();
            if (adminSettingsBtn != null && email.equals(ConfigService.getAdminEmail())) {
                adminSettingsBtn.setDisable(false);
            }
            updateAccessControl();
        }).showAtStartup(stage);

        if (mongo == null) {
            new Alert(Alert.AlertType.WARNING, "MongoDB not reachable. Login/Signup may fail until DB is available.").showAndWait();
        }
        if (!NetUtil.isOnline()) {
            new Alert(Alert.AlertType.WARNING, "No internet connection detected. Streaming and uploads will not work until you are online.").showAndWait();
        }
    }

    private Pane buildLoginPanel() {
        HBox top = new HBox(10);
        top.setPadding(new Insets(8));
        top.setAlignment(Pos.CENTER_LEFT);

        Button teacherLoginBtn = new Button("Teacher Login");
        teacherLoginBtn.setOnAction(e -> {
            new TeacherLoginWindow(auth, email -> {
                currentEmail = email;
                refreshStreams();
                refreshUploads();
                updateAccessControl();
            }).show();
        });

        Button adminLoginBtn = new Button("Admin Login");
        adminLoginBtn.setOnAction(e -> {
            new AdminLoginWindow(auth, email -> {
                currentEmail = email;
                refreshStreams();
                refreshUploads();
                if (adminSettingsBtn != null) adminSettingsBtn.setDisable(false);
                updateAccessControl();
            }).show();
        });

        adminSettingsBtn = new Button("Admin Settings");
        adminSettingsBtn.setDisable(true);
        adminSettingsBtn.setOnAction(e -> new AdminSettingsWindow().show());

        Button darkBtn = new Button("Toggle Dark");
        darkBtn.setOnAction(e -> toggleDark(top.getScene()));

        Button infoBtn = new Button("App Info");
        infoBtn.setOnAction(e -> showInfo());

        Button helpBtn = new Button("Help");
        helpBtn.setOnAction(e -> new HelpWindow().show());

        top.getChildren().addAll(new Label("Access:"), teacherLoginBtn, adminLoginBtn, adminSettingsBtn, darkBtn, infoBtn, helpBtn);
        return top;
    }

    private Tab streamsTab;
    private Tab uploadsTab;
    private VBox streamsContainer;
    private VBox uploadsContainer;
    private VBox streamsContent;
    private VBox uploadsContent;

    private TabPane buildCenterPanel() {
        TabPane tabs = new TabPane();

        // Streams tab
        streamsContent = new VBox(10);
        streamsContent.setPadding(new Insets(8));
        Button createBtn = new Button("Create Stream");
        Button previewBtn = new Button("Preview Stream");
        Button tokenBtn = new Button("Generate Token");
        Button copyBtn = new Button("Copy Details");
        Button urlBtn = new Button("Get Public URL");
        Button disableBtn = new Button("Disable Stream");
        Button completeBtn = new Button("Complete Stream");
        Button deleteBtn = new Button("Delete Stream");
        Button obsBtn = new Button("Launch OBS");
        // TableView with proper columns for stream details
        streamTable.setItems(streamItems);
        TableColumn<StreamRecord, String> colPlayback = new TableColumn<>("Playback ID");
        colPlayback.setCellValueFactory(cd -> new javafx.beans.property.ReadOnlyStringWrapper(cd.getValue().playbackId));
        colPlayback.setPrefWidth(180);

        TableColumn<StreamRecord, String> colLive = new TableColumn<>("Live ID");
        colLive.setCellValueFactory(cd -> new javafx.beans.property.ReadOnlyStringWrapper(cd.getValue().liveStreamId));
        colLive.setPrefWidth(180);

        TableColumn<StreamRecord, String> colKey = new TableColumn<>("Stream Key");
        colKey.setCellValueFactory(cd -> new javafx.beans.property.ReadOnlyStringWrapper(cd.getValue().streamKey));
        colKey.setPrefWidth(220);

        TableColumn<StreamRecord, String> colRtmp = new TableColumn<>("RTMP URL");
        colRtmp.setCellValueFactory(cd -> new javafx.beans.property.ReadOnlyStringWrapper(cd.getValue().rtmpUrl));
        colRtmp.setPrefWidth(260);

        TableColumn<StreamRecord, String> colCreated = new TableColumn<>("Created");
        colCreated.setCellValueFactory(cd -> new javafx.beans.property.ReadOnlyStringWrapper(cd.getValue().createdAtIso));
        colCreated.setPrefWidth(200);

        streamTable.getColumns().setAll(colPlayback, colLive, colKey, colRtmp, colCreated);
        streamTable.setPrefHeight(320);

        // Add Context Menu for Copying
        ContextMenu contextMenu = new ContextMenu();
        MenuItem copyRtmpItem = new MenuItem("Copy RTMP URL");
        copyRtmpItem.setOnAction(e -> {
            StreamRecord sel = streamTable.getSelectionModel().getSelectedItem();
            if (sel != null) copyToClipboard(sel.rtmpUrl);
        });
        MenuItem copyKeyItem = new MenuItem("Copy Stream Key");
        copyKeyItem.setOnAction(e -> {
            StreamRecord sel = streamTable.getSelectionModel().getSelectedItem();
            if (sel != null) copyToClipboard(sel.streamKey);
        });
        MenuItem copyPlaybackItem = new MenuItem("Copy Playback ID");
        copyPlaybackItem.setOnAction(e -> {
            StreamRecord sel = streamTable.getSelectionModel().getSelectedItem();
            if (sel != null) copyToClipboard(sel.playbackId);
        });
        contextMenu.getItems().addAll(copyRtmpItem, copyKeyItem, copyPlaybackItem);
        streamTable.setContextMenu(contextMenu);

        createBtn.setOnAction(e -> doCreateStream());
        previewBtn.setOnAction(e -> doPreviewStream());
        tokenBtn.setOnAction(e -> doGenerateToken());
        copyBtn.setOnAction(e -> doCopyStreamDetails());
        urlBtn.setOnAction(e -> doGetPublicUrl());
        disableBtn.setOnAction(e -> doDisableStream());
        completeBtn.setOnAction(e -> doCompleteStream());
        deleteBtn.setOnAction(e -> doDeleteStream());
        obsBtn.setOnAction(e -> doLaunchObs());
        streamsContent.getChildren().addAll(
                new HBox(10, createBtn, previewBtn, tokenBtn, copyBtn, urlBtn, disableBtn, completeBtn, deleteBtn, obsBtn),
                new Label("Stream History"),
                streamTable
        );
        
        streamsContainer = new VBox();
        streamsContainer.setFillWidth(true);
        streamsTab = new Tab("Streams", streamsContainer);
        streamsTab.setClosable(false);

        // Uploads tab
        uploadsContent = new VBox(10);
        uploadsContent.setPadding(new Insets(8));
        Button uploadBtn = new Button("Upload Video");
        uploadList.setItems(uploadItems);
        uploadList.setPrefHeight(320);
        uploadBtn.setOnAction(e -> doUploadVideo());
        uploadsContent.getChildren().addAll(
                new HBox(10, uploadBtn),
                new Label("Upload History"),
                uploadList
        );
        
        uploadsContainer = new VBox();
        uploadsContainer.setFillWidth(true);
        uploadsTab = new Tab("Uploads", uploadsContainer);
        uploadsTab.setClosable(false);
        
        // R2 Storage Tab
        r2Tab = new Tab("Cloudflare R2");
        r2Tab.setClosable(false);
        
        // Create container and views
        r2Container = new VBox();
        r2Container.setFillWidth(true);
        
        r2MainView = buildR2Panel();
        
        r2LoginView = new VBox(20);
        r2LoginView.setAlignment(Pos.CENTER);
        r2LoginView.setPadding(new Insets(40));
        
        Label r2LockLabel = new Label("🔒 Restricted Access");
        r2LockLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: bold;");
        Label r2LockMsg = new Label("You must be logged in as an Administrator to manage R2 storage.");
        r2LockMsg.setStyle("-fx-font-size: 14px;");
        
        Button r2AdminLoginBtn = new Button("Admin Login");
        r2AdminLoginBtn.setStyle("-fx-font-size: 14px; -fx-padding: 10 20;");
        r2AdminLoginBtn.setOnAction(e -> {
            new AdminLoginWindow(auth, email -> {
                currentEmail = email;
                refreshStreams();
                refreshUploads();
                if (adminSettingsBtn != null) adminSettingsBtn.setDisable(false);
                updateAccessControl();
            }).show();
        });
        
        r2LoginView.getChildren().addAll(r2LockLabel, r2LockMsg, r2AdminLoginBtn);
        
        r2Tab.setContent(r2Container);

        tabs.getTabs().addAll(streamsTab, uploadsTab, r2Tab);
        
        // Initial state update
        updateAccessControl();
        
        return tabs;
    }

    private void updateAccessControl() {
        boolean isLoggedIn = currentEmail != null;
        String adminEmail = ConfigService.getAdminEmail();
        boolean isAdmin = isLoggedIn && currentEmail.equals(adminEmail);
        
        // Update Streams Tab
        if (streamsContainer != null) {
            streamsContainer.getChildren().clear();
            if (isLoggedIn) {
                streamsContainer.getChildren().add(streamsContent);
            } else {
                streamsContainer.getChildren().add(buildLoginRequiredView("Log in to manage streams"));
            }
        }
        
        // Update Uploads Tab
        if (uploadsContainer != null) {
            uploadsContainer.getChildren().clear();
            if (isLoggedIn) {
                uploadsContainer.getChildren().add(uploadsContent);
            } else {
                uploadsContainer.getChildren().add(buildLoginRequiredView("Log in to view uploads"));
            }
        }

        // Update R2 Tab
        if (r2Container != null) {
            r2Container.getChildren().clear();
            if (isAdmin) {
                r2Container.getChildren().add(r2MainView);
                refreshR2Files();
            } else {
                r2Container.getChildren().add(r2LoginView);
            }
        }
    }
    
    private VBox buildLoginRequiredView(String message) {
        VBox box = new VBox(20);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(40));
        
        Label lockLabel = new Label("🔒 Login Required");
        lockLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: bold;");
        Label msgLabel = new Label(message);
        msgLabel.setStyle("-fx-font-size: 14px;");
        
        box.getChildren().addAll(lockLabel, msgLabel);
        return box;
    }

    // Removed old updateR2Access as it is merged into updateAccessControl
    private ObservableList<com.educater.r2.R2Service.R2File> r2Files = FXCollections.observableArrayList();
    private TableView<com.educater.r2.R2Service.R2File> r2Table = new TableView<>();
    private Label r2StatusLabel = new Label("Ready");
    private Label totalStorageLabel = new Label("Total: -");
    private Label fileCountLabel = new Label("Files: -");
    private Label currentPathLabel = new Label("/");
    private String currentPrefix = "";

    private Pane buildR2Panel() {
        VBox root = new VBox(10);
        root.setPadding(new Insets(10));
        
        // --- Dashboard ---
        HBox dashboard = new HBox(20);
        dashboard.setAlignment(Pos.CENTER_LEFT);
        dashboard.setPadding(new Insets(10));
        dashboard.setStyle("-fx-background-color: #f4f4f4; -fx-background-radius: 5; -fx-border-color: #ddd; -fx-border-radius: 5;");
        
        VBox storageCard = new VBox(5, new Label("Storage Used"), totalStorageLabel);
        totalStorageLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        
        VBox countCard = new VBox(5, new Label("Total Objects"), fileCountLabel);
        fileCountLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        
        dashboard.getChildren().addAll(storageCard, countCard);
        
        // --- Navigation ---
        HBox navBar = new HBox(10);
        navBar.setAlignment(Pos.CENTER_LEFT);
        Button upBtn = new Button("⬆ Up");
        upBtn.setOnAction(e -> doR2NavigateUp());
        
        currentPathLabel.setStyle("-fx-font-weight: bold; -fx-padding: 0 0 0 10;");
        
        navBar.getChildren().addAll(upBtn, new Label("Path: "), currentPathLabel);
        
        // --- Toolbar ---
        HBox toolbar = new HBox(10);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        
        Button refreshBtn = new Button("Refresh");
        Button uploadFileBtn = new Button("Upload");
        Button deleteFileBtn = new Button("Delete");
        Button copyLinkBtn = new Button("Copy Link");
        
        toolbar.getChildren().addAll(refreshBtn, uploadFileBtn, deleteFileBtn, copyLinkBtn);
        
        // --- Table ---
        r2Table.setItems(r2Files);
        
        // Icon/Type Column
        TableColumn<com.educater.r2.R2Service.R2File, String> colType = new TableColumn<>("Type");
        colType.setCellValueFactory(cd -> new javafx.beans.property.ReadOnlyStringWrapper(cd.getValue().isFolder() ? "📁 Folder" : "📄 File"));
        colType.setPrefWidth(80);

        TableColumn<com.educater.r2.R2Service.R2File, String> colName = new TableColumn<>("Name");
        colName.setCellValueFactory(cd -> {
            String name = cd.getValue().getKey();
            // Show only relative name in current folder
            if (name.startsWith(currentPrefix)) {
                name = name.substring(currentPrefix.length());
            }
            if (name.endsWith("/")) name = name.substring(0, name.length() - 1);
            return new javafx.beans.property.ReadOnlyStringWrapper(name);
        });
        colName.setPrefWidth(300);
        
        TableColumn<com.educater.r2.R2Service.R2File, String> colSize = new TableColumn<>("Size");
        colSize.setCellValueFactory(cd -> new javafx.beans.property.ReadOnlyStringWrapper(
            cd.getValue().isFolder() ? "-" : humanReadableByteCountBin(cd.getValue().getSize())
        ));
        colSize.setPrefWidth(100);
        
        TableColumn<com.educater.r2.R2Service.R2File, String> colDate = new TableColumn<>("Last Modified");
        colDate.setCellValueFactory(cd -> new javafx.beans.property.ReadOnlyStringWrapper(cd.getValue().getLastModified()));
        colDate.setPrefWidth(200);
        
        r2Table.getColumns().setAll(colType, colName, colSize, colDate);
        VBox.setVgrow(r2Table, Priority.ALWAYS);
        
        // Double click to navigate
        r2Table.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                com.educater.r2.R2Service.R2File sel = r2Table.getSelectionModel().getSelectedItem();
                if (sel != null && sel.isFolder()) {
                    doR2NavigateInto(sel.getKey());
                }
            }
        });
        
        // Actions
        refreshBtn.setOnAction(e -> refreshR2Files());
        uploadFileBtn.setOnAction(e -> doR2Upload());
        deleteFileBtn.setOnAction(e -> doR2Delete());
        copyLinkBtn.setOnAction(e -> doR2CopyLink());
        
        root.getChildren().addAll(dashboard, navBar, toolbar, r2Table, r2StatusLabel);
        
        // Initial Load if possible
        Platform.runLater(this::refreshR2Files);
        
        return root;
    }
    
    private void doR2NavigateInto(String folderKey) {
        currentPrefix = folderKey;
        refreshR2Files();
    }
    
    private void doR2NavigateUp() {
        if (currentPrefix.isEmpty()) return;
        
        // Remove trailing slash
        String p = currentPrefix.substring(0, currentPrefix.length() - 1);
        int lastSlash = p.lastIndexOf('/');
        if (lastSlash >= 0) {
            currentPrefix = p.substring(0, lastSlash + 1);
        } else {
            currentPrefix = "";
        }
        refreshR2Files();
    }
    
    private void initR2Service() {
        if (r2Service == null) {
            String accId = ConfigService.getR2AccountId();
            String accessKey = ConfigService.getR2AccessKey();
            String secretKey = ConfigService.getR2SecretKey();
            String bucket = ConfigService.getR2BucketName();
            String pubUrl = ConfigService.getR2PublicUrl();
            
            if (!accId.isEmpty() && !accessKey.isEmpty() && !secretKey.isEmpty() && !bucket.isEmpty()) {
                try {
                    r2Service = new com.educater.r2.R2Service(accId, accessKey, secretKey, bucket, pubUrl);
                } catch (Exception e) {
                    r2StatusLabel.setText("Error initializing R2: " + e.getMessage());
                }
            } else {
                r2StatusLabel.setText("R2 Credentials missing in Admin Settings");
            }
        }
    }
    
    private void refreshR2Files() {
        initR2Service();
        if (r2Service == null) return;
        
        currentPathLabel.setText(currentPrefix.isEmpty() ? "/" : "/" + currentPrefix);
        
        new Thread(() -> {
            try {
                Platform.runLater(() -> r2StatusLabel.setText("Loading..."));
                List<com.educater.r2.R2Service.R2File> files = r2Service.listFiles(currentPrefix);
                
                // Fetch usage separately (could be slow, maybe cache or optimize)
                com.educater.r2.R2Service.StorageUsage usage = r2Service.getStorageUsage();
                
                Platform.runLater(() -> {
                    r2Files.setAll(files);
                    totalStorageLabel.setText(humanReadableByteCountBin(usage.getTotalSizeBytes()));
                    fileCountLabel.setText(String.valueOf(usage.getFileCount()));
                    r2StatusLabel.setText("Ready");
                });
            } catch (Exception e) {
                Platform.runLater(() -> r2StatusLabel.setText("Error loading files: " + e.getMessage()));
            }
        }).start();
    }
    
    private void doR2Upload() {
        initR2Service();
        if (r2Service == null) {
            new Alert(Alert.AlertType.WARNING, "R2 not configured.").showAndWait();
            return;
        }
        
        // Use the new robust Upload Window
        new R2UploadWindow(r2Service, this::refreshR2Files).show();
    }
    
    private void doR2Delete() {
        com.educater.r2.R2Service.R2File sel = r2Table.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Delete " + (sel.isFolder() ? "FOLDER: " : "") + sel.getKey() + "?", ButtonType.YES, ButtonType.NO);
        alert.showAndWait();
        if (alert.getResult() == ButtonType.YES) {
             new Thread(() -> {
                 try {
                     if (sel.isFolder()) {
                         r2Service.deleteFolder(sel.getKey());
                     } else {
                         r2Service.deleteFile(sel.getKey());
                     }
                     Platform.runLater(this::refreshR2Files);
                 } catch (Exception e) {
                     Platform.runLater(() -> new Alert(Alert.AlertType.ERROR, "Delete failed: " + e.getMessage()).showAndWait());
                 }
             }).start();
        }
    }
    
    private void doR2CopyLink() {
        initR2Service();
        com.educater.r2.R2Service.R2File sel = r2Table.getSelectionModel().getSelectedItem();
        if (sel != null && r2Service != null && !sel.isFolder()) {
            String url = r2Service.getPublicUrl(sel.getKey());
            copyToClipboard(url);
            r2StatusLabel.setText("Link copied: " + url);
        }
    }

    private static String humanReadableByteCountBin(long bytes) {
        long absB = bytes == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(bytes);
        if (absB < 1024) {
            return bytes + " B";
        }
        long value = absB;
        java.text.CharacterIterator ci = new java.text.StringCharacterIterator("KMGTPE");
        for (int i = 40; i >= 0 && absB > 0xfffccccccccccccL >> i; i -= 10) {
            value >>= 10;
            ci.next();
        }
        value *= Long.signum(bytes);
        return String.format("%.1f %ciB", value / 1024.0, ci.current());
    }

    private Pane buildRightPanel() {
        VBox right = new VBox(10);
        right.setPadding(new Insets(8));
        if (webView == null) {
            try {
                webView = new WebView();
                webView.setPrefSize(480, 360);
            } catch (Throwable ex) {
                // If WebView fails to initialize, omit the preview UI and only show viewer count.
                right.getChildren().addAll(viewerCountLabel);
                return right;
            }
        }
        right.getChildren().addAll(new Label("Preview"), webView, viewerCountLabel);
        VBox.setVgrow(webView, Priority.ALWAYS);
        return right;
    }

    private void toggleDark(Scene scene) {
        darkMode = !darkMode;
        if (darkMode) {
            scene.getStylesheets().add(resourceToUrl("/style.css"));
        } else {
            scene.getStylesheets().remove(resourceToUrl("/style.css"));
        }
    }

    private void showInfo() {
        Stage stage = new Stage();
        stage.setTitle("EduCaster Live – About & Help");
        stage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        stage.setResizable(true); // Made responsive

        // Branding
        ImageView brandingLogo = new ImageView(new Image(getClass().getResource("/images/educaster.png").toExternalForm()));
        brandingLogo.setFitWidth(160);
        brandingLogo.setPreserveRatio(true);

        Label appTitle = new Label("EduCaster Live Desktop");
        appTitle.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        Label developer = new Label("Developer: Angel Singh • BR-31 Technologies");
        developer.setStyle("-fx-font-size: 13px; -fx-opacity: 0.9;");

        // Website link
        Hyperlink site = new Hyperlink("https://br31tech.live");
        site.setOnAction(e -> getHostServices().showDocument("https://br31tech.live"));

        // Features
        Label featuresTitle = new Label("Features");
        featuresTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        VBox features = new VBox(6,
                new Label("• Login & Signup (MongoDB)"),
                new Label("• Create Mux Live Streams"),
                new Label("• View & Copy RTMP URL / Stream Key"),
                new Label("• Table of Previous Streams with columns"),
                new Label("• Preview Player (WebView) or Browser"),
                new Label("• Signed Playback Token Generator"),
                new Label("• Live Viewer Count"),
                new Label("• Launch OBS from the app"),
                new Label("• Dark Mode toggle")
        );

        // How it works
        Label howTitle = new Label("How It Works");
        howTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        VBox how = new VBox(6,
                new Label("1) Login (Teacher/Admin)"),
                new Label("2) Click 'Create Stream' to generate RTMP URL & Stream Key"),
                new Label("3) Use 'Copy Details' or open the per-field dialog to copy"),
                new Label("4) Paste into OBS/Encoder and start streaming"),
                new Label("5) Use 'Preview Stream' to watch and track viewer count"),
                new Label("6) Use 'Get Public URL' to share the HLS playback link")
        );

        // Actions
        Button close = new Button("Close");
        close.setOnAction(e -> stage.close());
        Button openSite = new Button("Open Website");
        openSite.setOnAction(e -> getHostServices().showDocument("https://br31tech.live"));
        HBox actions = new HBox(10, openSite, close);
        actions.setAlignment(Pos.CENTER_RIGHT);

        VBox content = new VBox(12,
                brandingLogo,
                appTitle,
                developer,
                site,
                new Separator(),
                featuresTitle,
                features,
                new Separator(),
                howTitle,
                how,
                new Separator(),
                actions
        );
        content.setPadding(new Insets(16));
        content.setAlignment(Pos.TOP_CENTER);

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setPadding(new Insets(0));

        stage.setScene(new Scene(scroll, 560, 600));
        stage.showAndWait();
    }

    private void refreshStreams() {
        if (currentEmail == null) return;
        List<StreamRecord> records = mongo.getStreamsByEmail(currentEmail);
        streamItems.setAll(records);
    }

    private void refreshUploads() {
        if (currentEmail == null || mongo == null) return;
        List<UploadRecord> records = mongo.getUploadsByEmail(currentEmail);
        uploadItems.setAll(records);
    }

    private void doCreateStream() {
        if (currentEmail == null) { new Alert(Alert.AlertType.ERROR, "Login first").showAndWait(); return; }
        if (!NetUtil.isOnline()) { new Alert(Alert.AlertType.ERROR, "No internet connection").showAndWait(); return; }
        // Console log when user initiates create stream
        System.out.println("[Create Stream] Clicked by: " + currentEmail);
        System.out.println("[Create Stream] Calling mux.createLiveStream()...");
        try {
            LiveStreamInfo info = mux.createLiveStream();
            System.out.println("[Create Stream] Returned LiveStreamInfo: " + (info == null ? "<null>" : ("rtmpUrl=" + info.rtmpUrl + ", streamKey=" + info.streamKey + ", playbackId=" + info.playbackId + ", liveId=" + info.liveId)));
            currentPlaybackId = info.playbackId;
            currentLiveId = info.liveId;

            // Console log on successful creation
            System.out.println("[Create Stream] Success\n" +
                    " RTMP URL: " + info.rtmpUrl + "\n" +
                    " Stream Key: " + info.streamKey + "\n" +
                    " Playback ID: " + info.playbackId + "\n" +
                    " Live ID: " + info.liveId);

            StreamRecord r = new StreamRecord();
            r.email = currentEmail;
            r.rtmpUrl = info.rtmpUrl;
            r.streamKey = info.streamKey;
            r.playbackId = info.playbackId;
            r.liveStreamId = info.liveId;
            mongo.saveStream(currentEmail, r);
            refreshStreams();

            String msg = buildDetailsText(info.rtmpUrl, info.streamKey, info.playbackId, info.liveId);
            copyToClipboard(msg);
            new Alert(Alert.AlertType.INFORMATION, msg + "\n\n(Copied to clipboard)").showAndWait();
        } catch (Exception ex) {
            // Console error log and stack trace for diagnostics
            System.err.println("[Create Stream] Error: " + ex.getMessage());
            ex.printStackTrace();
            new Alert(Alert.AlertType.ERROR, "Error: " + ex.getMessage()).showAndWait();
        }
    }

    private void doPreviewStream() {
        StreamRecord sel = getSelectedStreamRecord();
        String playbackId = sel != null ? sel.playbackId : currentPlaybackId;
        if (playbackId == null || playbackId.isBlank()) { new Alert(Alert.AlertType.ERROR, "No playback ID").showAndWait(); return; }
        String token = null; // preview can work public; token optional
        loadMuxWebView(playbackId, token);
        // start polling viewers if live id available
        String liveId = sel != null ? sel.liveStreamId : currentLiveId;
        if (liveId != null && !liveId.isBlank()) startViewerPolling(liveId);
    }

    private void doGetPublicUrl() {
        StreamRecord sel = getSelectedStreamRecord();
        String playbackId = sel != null ? sel.playbackId : currentPlaybackId;
        if (playbackId == null || playbackId.isBlank()) {
            new Alert(Alert.AlertType.ERROR, "No playback ID").showAndWait();
            return;
        }
        String publicUrl = "https://stream.mux.com/" + playbackId + ".m3u8";
        try {
            javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            content.putString(publicUrl);
            javafx.scene.input.Clipboard.getSystemClipboard().setContent(content);
        } catch (Throwable ignored) { }
        showPublicUrlDialog(publicUrl);
    }

    private void doCopyStreamDetails() {
        StreamRecord sel = getSelectedStreamRecord();
        if (sel == null && currentPlaybackId == null && currentLiveId == null) {
            new Alert(Alert.AlertType.ERROR, "No stream selected or details available").showAndWait();
            return;
        }
        // Prefer selected record; fallback to current in-memory values
        String rtmp = sel != null ? sel.rtmpUrl : null;
        String key = sel != null ? sel.streamKey : null;
        String playbackId = sel != null ? sel.playbackId : currentPlaybackId;
        String liveId = sel != null ? sel.liveStreamId : currentLiveId;

        showCopyDetailsDialog(rtmp, key, playbackId, liveId);
    }

    private String buildDetailsText(String rtmpUrl, String streamKey, String playbackId, String liveId) {
        StringBuilder sb = new StringBuilder();
        sb.append("RTMP URL: ").append(rtmpUrl == null ? "-" : rtmpUrl).append('\n');
        sb.append("Stream Key: ").append(streamKey == null ? "-" : streamKey).append('\n');
        sb.append("Playback ID: ").append(playbackId == null ? "-" : playbackId).append('\n');
        sb.append("Live ID: ").append(liveId == null ? "-" : liveId);
        return sb.toString();
    }

    private void copyToClipboard(String text) {
        try {
            javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            content.putString(text);
            javafx.scene.input.Clipboard.getSystemClipboard().setContent(content);
        } catch (Throwable ignored) { }
    }

    private void doDisableStream() {
        StreamRecord sel = getSelectedStreamRecord();
        String liveId = sel != null ? sel.liveStreamId : currentLiveId;
        if (liveId == null || liveId.isBlank()) {
            new Alert(Alert.AlertType.ERROR, "No live stream ID").showAndWait();
            return;
        }
        try {
            mux.disableLiveStream(liveId);
            new Alert(Alert.AlertType.INFORMATION, "Live stream disabled on Mux").showAndWait();
        } catch (Exception ex) {
            new Alert(Alert.AlertType.ERROR, "Error: " + ex.getMessage()).showAndWait();
        }
    }

    private void doCompleteStream() {
        StreamRecord sel = getSelectedStreamRecord();
        String liveId = sel != null ? sel.liveStreamId : currentLiveId;
        if (liveId == null || liveId.isBlank()) {
            new Alert(Alert.AlertType.ERROR, "No live stream ID").showAndWait();
            return;
        }
        try {
            mux.completeLiveStream(liveId);
            new Alert(Alert.AlertType.INFORMATION, "Live stream marked as complete on Mux").showAndWait();
        } catch (Exception ex) {
            new Alert(Alert.AlertType.ERROR, "Error: " + ex.getMessage()).showAndWait();
        }
    }

    private void doDeleteStream() {
        StreamRecord sel = getSelectedStreamRecord();
        if (sel == null) {
            new Alert(Alert.AlertType.ERROR, "Select a stream to delete").showAndWait();
            return;
        }
        String liveId = sel.liveStreamId;
        if (liveId == null || liveId.isBlank()) {
            new Alert(Alert.AlertType.ERROR, "Selected stream has no live ID").showAndWait();
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Delete live stream on Mux and remove from history?", ButtonType.OK, ButtonType.CANCEL);
        confirm.setHeaderText(null);
        confirm.showAndWait();
        if (confirm.getResult() != ButtonType.OK) return;
        try {
            mux.deleteLiveStream(liveId);
            if (mongo != null && sel.id != null) {
                mongo.deleteStreamById(sel.id);
            }
            refreshStreams();
            new Alert(Alert.AlertType.INFORMATION, "Live stream deleted").showAndWait();
        } catch (Exception ex) {
            new Alert(Alert.AlertType.ERROR, "Error: " + ex.getMessage()).showAndWait();
        }
    }

    private void doGenerateToken() {
        StreamRecord sel = getSelectedStreamRecord();
        String playbackId = sel != null ? sel.playbackId : currentPlaybackId;
        if (playbackId == null || playbackId.isBlank()) { new Alert(Alert.AlertType.ERROR, "No playback ID").showAndWait(); return; }
        try {
            Map<String, Object> claims = new HashMap<>();
            claims.put("aud", "v");
            claims.put("playback_id", playbackId);
            String jwt = JwtUtil.signHs256(claims, 3600);
            new Alert(Alert.AlertType.INFORMATION, "Token generated:\n" + jwt).showAndWait();
            loadMuxWebView(playbackId, jwt);
        } catch (Exception ex) {
            new Alert(Alert.AlertType.ERROR, "Error: " + ex.getMessage()).showAndWait();
        }
    }

    private StreamRecord getSelectedStreamRecord() {
        return streamTable.getSelectionModel().getSelectedItem();
    }

    private void showCopyDetailsDialog(String rtmpUrl, String streamKey, String playbackId, String liveId) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Copy Stream Details");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CLOSE);

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.setPadding(new Insets(10));

        // Helper to add a labeled field with a copy button
        java.util.function.BiConsumer<Integer, String[]> addRow = (row, data) -> {
            String label = data[0];
            String value = data[1];
            Label lbl = new Label(label);
            TextField tf = new TextField(value == null ? "" : value);
            tf.setEditable(false);
            tf.setPrefWidth(420);
            Button copy = new Button("Copy");
            copy.setOnAction(e -> {
                javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
                content.putString(tf.getText());
                javafx.scene.input.Clipboard.getSystemClipboard().setContent(content);
            });
            grid.add(lbl, 0, row);
            grid.add(tf, 1, row);
            grid.add(copy, 2, row);
        };

        addRow.accept(0, new String[]{"RTMP URL", rtmpUrl});
        addRow.accept(1, new String[]{"Stream Key", streamKey});
        addRow.accept(2, new String[]{"Playback ID", playbackId});
        addRow.accept(3, new String[]{"Live ID", liveId});

        // Copy All button
        Button copyAll = new Button("Copy All");
        copyAll.setOnAction(e -> {
            String msg = buildDetailsText(rtmpUrl, streamKey, playbackId, liveId);
            copyToClipboard(msg);
        });
        HBox actions = new HBox(10, copyAll);
        actions.setAlignment(Pos.CENTER_RIGHT);

        VBox content = new VBox(12, grid, actions);
        dialog.getDialogPane().setContent(content);
        dialog.showAndWait();
    }

    private void showPublicUrlDialog(String publicUrl) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Public URL");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CLOSE);

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.setPadding(new Insets(10));

        Label lbl = new Label("HLS URL");
        TextField tf = new TextField(publicUrl);
        tf.setEditable(false);
        tf.setPrefWidth(520);
        Button copy = new Button("Copy");
        copy.setOnAction(e -> {
            javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            content.putString(tf.getText());
            javafx.scene.input.Clipboard.getSystemClipboard().setContent(content);
        });

        grid.add(lbl, 0, 0);
        grid.add(tf, 1, 0);
        grid.add(copy, 2, 0);

        VBox content = new VBox(12, grid);
        dialog.getDialogPane().setContent(content);
        dialog.showAndWait();
    }

    private void doLaunchObs() {
        // 1. Try configured path first
        String customPath = ConfigService.getObsPath();
        if (customPath != null && !customPath.isBlank()) {
            java.io.File file = new java.io.File(customPath);
            if (file.exists()) {
                try {
                    new ProcessBuilder(customPath).directory(file.getParentFile()).start();
                    return;
                } catch (Exception ex) {
                    new Alert(Alert.AlertType.WARNING, "Failed to launch custom OBS path: " + ex.getMessage() + "\nTrying standard paths...").showAndWait();
                }
            }
        }

        // 2. Try common OBS paths
        String[] paths = {
            "C:/Program Files/obs-studio/bin/64bit/obs64.exe",
            "C:/Program Files (x86)/obs-studio/bin/64bit/obs64.exe",
            "C:/Program Files/obs-studio/bin/32bit/obs32.exe",
            System.getenv("ProgramFiles") + "/obs-studio/bin/64bit/obs64.exe"
        };

        for (String path : paths) {
            java.io.File file = new java.io.File(path);
            if (file.exists()) {
                try {
                    // Set working directory to the bin folder so OBS can find its DLLs
                    new ProcessBuilder(path).directory(file.getParentFile()).start();
                    return;
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }

        // Fallback: try just "obs" command
        try {
            new ProcessBuilder("obs").start();
        } catch (Exception ex) {
            new Alert(Alert.AlertType.ERROR, "OBS Studio not found in standard locations.\nPlease install OBS or add it to your PATH.").showAndWait();
        }
    }

    private void doUploadVideo() {
        if (!NetUtil.isOnline()) { new Alert(Alert.AlertType.ERROR, "No internet connection").showAndWait(); return; }
        UploadWindow w = new UploadWindow(mux, rec -> {
            if (rec != null) {
                rec.email = currentEmail;
                if (mongo != null) mongo.saveUpload(currentEmail, rec);
                refreshUploads();
                if (rec.playbackId != null && !rec.playbackId.isBlank()) {
                    currentPlaybackId = rec.playbackId;
                    loadMuxWebView(rec.playbackId, null);
                }
            }
        });
        w.show();
    }

    private void loadMuxWebView(String playbackId, String token) {
        try {
            if (webView == null) {
                try {
                    webView = new WebView();
                } catch (Throwable initEx) {
                    new Alert(Alert.AlertType.ERROR, "WebView not available: " + initEx.getMessage() + "\nOpening preview in your browser.").showAndWait();
                    openMuxInBrowser(playbackId, token);
                    return;
                }
            }
            InputStream is = getClass().getResourceAsStream("/web/mux_player_template.html");
            String html = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            html = html.replace("{{PLAYBACK_ID}}", playbackId);
            html = html.replace("{{PLAYBACK_TOKEN}}", token == null ? "" : token);
            webView.getEngine().loadContent(html);
        } catch (Exception ex) {
            new Alert(Alert.AlertType.ERROR, "WebView error: " + ex.getMessage()).showAndWait();
        }
    }

    // Fallback: open the same preview HTML in the user's default browser
    private void openMuxInBrowser(String playbackId, String token) {
        try {
            InputStream is = getClass().getResourceAsStream("/web/mux_player_template.html");
            String html = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            html = html.replace("{{PLAYBACK_ID}}", playbackId);
            html = html.replace("{{PLAYBACK_TOKEN}}", token == null ? "" : token);
            Path tmp = Files.createTempFile("mux-preview-", ".html");
            Files.writeString(tmp, html, StandardCharsets.UTF_8);
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(tmp.toUri());
            } else {
                new Alert(Alert.AlertType.INFORMATION, "Preview file created at: " + tmp.toAbsolutePath()).showAndWait();
            }
        } catch (Exception ex) {
            new Alert(Alert.AlertType.ERROR, "Cannot open browser preview: " + ex.getMessage()).showAndWait();
        }
    }

    private void startViewerPolling(String liveId) {
        Thread t = new Thread(() -> {
            while (true) {
                try {
                    int viewers = mux.getConcurrentViewersByLiveId(liveId);
                    Platform.runLater(() -> viewerCountLabel.setText("Viewers: " + viewers));
                    Thread.sleep(5000);
                } catch (InterruptedException ie) {
                    return;
                } catch (Exception ex) {
                    Platform.runLater(() -> viewerCountLabel.setText("Viewers: -"));
                }
            }
        }, "viewer-poll");
        t.setDaemon(true);
        t.start();
    }

    private String resourceToUrl(String path) {
        return getClass().getResource(path).toExternalForm();
    }

    @Override
    public void stop() throws Exception {
        super.stop();
        if (mongo != null) mongo.close();
    }

    public static void main(String[] args) {
        launch(args);
    }
}