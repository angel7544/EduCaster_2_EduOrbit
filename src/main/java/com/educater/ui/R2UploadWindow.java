package com.educater.ui;

import com.educater.r2.R2Service;
import com.educater.config.ConfigService;
import com.google.gson.Gson;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class R2UploadWindow {

    private final R2Service r2Service;
    private final Runnable onComplete;
    private final Gson gson = new Gson();

    public R2UploadWindow(R2Service r2Service, Runnable onComplete) {
        this.r2Service = r2Service;
        this.onComplete = onComplete;
    }

    public void show() {
        Stage stage = new Stage();
        stage.setTitle("R2 Upload Manager");
        stage.initModality(Modality.APPLICATION_MODAL);

        // UI Components
        ToggleGroup typeGroup = new ToggleGroup();
        RadioButton rbFile = new RadioButton("Upload File");
        rbFile.setToggleGroup(typeGroup);
        rbFile.setSelected(true);
        RadioButton rbFolder = new RadioButton("Upload Folder");
        rbFolder.setToggleGroup(typeGroup);

        TextField pathField = new TextField();
        pathField.setPromptText("Select file or folder...");
        pathField.setEditable(false);
        pathField.setPrefWidth(300);

        Button browseBtn = new Button("Browse...");

        TextField prefixField = new TextField();
        prefixField.setPromptText("Target Prefix (optional, e.g. 'videos/')");
        
        CheckBox chkTranscode = new CheckBox("Optimize for Streaming (HLS) via EduCaster");
        chkTranscode.setTooltip(new Tooltip("Converts video to HLS and uploads to R2 using local encoder"));
        
        VBox hlsSettingsBox = new VBox(10);
        hlsSettingsBox.setVisible(false);
        hlsSettingsBox.setManaged(false);
        hlsSettingsBox.setPadding(new Insets(0, 0, 0, 20));

        // Advanced HLS Controls
        HBox hlsTopRow = new HBox(20);
        hlsTopRow.setAlignment(Pos.CENTER_LEFT);
        
        CheckBox cbHwAccel = new CheckBox("Hardware Acceleration (NVENC)");
        cbHwAccel.setTooltip(new Tooltip("Use NVIDIA GPU for faster encoding"));
        
        Label lblSeg = new Label("Segment Duration (s):");
        TextField tfSegment = new TextField("6");
        tfSegment.setPrefWidth(50);
        
        hlsTopRow.getChildren().addAll(cbHwAccel, lblSeg, tfSegment);
        
        Label lblQualities = new Label("Select Resolutions & Bitrates:");
        GridPane gridQualities = new GridPane();
        gridQualities.setHgap(10);
        gridQualities.setVgap(5);
        
        // Quality Helpers
        Map<String, CheckBox> qualityChecks = new HashMap<>();
        Map<String, TextField> bitrateFields = new HashMap<>();
        
        String[][] qualities = {
            {"1080p", "5000k"},
            {"720p", "2800k"},
            {"480p", "1400k"},
            {"360p", "800k"},
            {"240p", "400k"}
        };
        
        for (int i = 0; i < qualities.length; i++) {
            String q = qualities[i][0];
            String b = qualities[i][1];
            
            CheckBox cb = new CheckBox(q);
            cb.setSelected(true);
            TextField tf = new TextField(b);
            tf.setPrefWidth(80);
            
            cb.selectedProperty().addListener((obs, old, val) -> tf.setDisable(!val));
            
            qualityChecks.put(q, cb);
            bitrateFields.put(q, tf);
            
            gridQualities.add(cb, 0, i);
            gridQualities.add(tf, 1, i);
        }
        
        hlsSettingsBox.getChildren().addAll(hlsTopRow, lblQualities, gridQualities);

        final VBox hlsSettingsBoxRef = hlsSettingsBox;
        chkTranscode.selectedProperty().addListener((obs, oldVal, newVal) -> {
            hlsSettingsBoxRef.setVisible(newVal);
            hlsSettingsBoxRef.setManaged(newVal);
        });

        ComboBox<String> existingFoldersBox = new ComboBox<>();
        existingFoldersBox.setPromptText("Select existing folder...");
        existingFoldersBox.setEditable(true); // Allow typing new ones too
        existingFoldersBox.setPrefWidth(300);
        existingFoldersBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.isEmpty()) {
                prefixField.setText(newVal);
            }
        });

        // Fetch folders in background
        new Thread(() -> {
            try {
                java.util.List<String> folders = r2Service.listFolders();
                Platform.runLater(() -> existingFoldersBox.getItems().setAll(folders));
            } catch (Exception e) {
                // ignore
            }
        }).start();

        Button startBtn = new Button("Start Upload");
        startBtn.setStyle("-fx-font-weight: bold; -fx-base: #4CAF50;");
        Button stopUploadBtn = new Button("Cancel Upload");
        stopUploadBtn.setDisable(true);
        Button cancelBtn = new Button("Close");

        // Progress Components
        VBox progressBox = new VBox(8);
        progressBox.setVisible(false);
        progressBox.setStyle("-fx-background-color: #f4f4f4; -fx-padding: 10; -fx-background-radius: 5;");

        Label statusLabel = new Label("Ready");
        statusLabel.setStyle("-fx-font-weight: bold;");
        
        ProgressBar progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(400);

        Label detailsLabel = new Label("Waiting...");
        Label transferredLabel = new Label("- / -");
        
        // Console Output
        TextArea consoleArea = new TextArea();
        consoleArea.setEditable(false);
        consoleArea.setPrefHeight(150);
        consoleArea.setStyle("-fx-font-family: 'Consolas', monospace; -fx-font-size: 11px;");
        consoleArea.setWrapText(true);
        TitledPane consolePane = new TitledPane("Processing Console", consoleArea);
        consolePane.setExpanded(false);

        progressBox.getChildren().addAll(statusLabel, progressBar, new HBox(20, detailsLabel, transferredLabel), consolePane);

        // Layout
        GridPane inputGrid = new GridPane();
        inputGrid.setHgap(10);
        inputGrid.setVgap(10);
        inputGrid.add(new Label("Type:"), 0, 0);
        inputGrid.add(new HBox(10, rbFile, rbFolder), 1, 0);
        inputGrid.add(new Label("Source:"), 0, 1);
        inputGrid.add(new HBox(10, pathField, browseBtn), 1, 1);
        inputGrid.add(new Label("Prefix:"), 0, 2);
        inputGrid.add(new VBox(5, existingFoldersBox, prefixField), 1, 2);
        inputGrid.add(chkTranscode, 1, 3);
        inputGrid.add(hlsSettingsBox, 1, 4);

        HBox actions = new HBox(10, startBtn, stopUploadBtn, cancelBtn);
        actions.setAlignment(Pos.CENTER_RIGHT);

        VBox root = new VBox(15,
                new Label("Upload to Cloudflare R2"),
                inputGrid,
                new Separator(),
                progressBox,
                actions
        );
        root.setPadding(new Insets(15));

        // Event Handlers
        browseBtn.setOnAction(e -> {
            if (rbFile.isSelected()) {
                FileChooser fc = new FileChooser();
                fc.setTitle("Select File");
                File f = fc.showOpenDialog(stage);
                if (f != null) {
                    pathField.setText(f.getAbsolutePath());
                    transferredLabel.setText("File Size: " + humanReadableByteCount(f.length()));
                }
            } else {
                DirectoryChooser dc = new DirectoryChooser();
                dc.setTitle("Select Folder");
                File f = dc.showDialog(stage);
                if (f != null) pathField.setText(f.getAbsolutePath());
            }
        });

        // Reset path when toggling type
        typeGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
            pathField.clear();
            chkTranscode.setDisable(rbFolder.isSelected());
            if (rbFolder.isSelected()) {
                chkTranscode.setSelected(false);
                hlsSettingsBoxRef.setVisible(false);
                hlsSettingsBoxRef.setManaged(false);
            }
        });

        AtomicBoolean isUploading = new AtomicBoolean(false);
        final Thread[] uploadThread = new Thread[1];
        final Process[] pythonProcess = new Process[1];

        startBtn.setOnAction(e -> {
            if (isUploading.get()) return;

            String path = pathField.getText();
            if (path == null || path.isEmpty()) {
                new Alert(Alert.AlertType.WARNING, "Please select a file or folder.").showAndWait();
                return;
            }

            File file = new File(path);
            if (!file.exists()) {
                new Alert(Alert.AlertType.ERROR, "Selected path does not exist.").showAndWait();
                return;
            }

            String prefix = prefixField.getText().trim();
            boolean isFolder = rbFolder.isSelected();
            boolean useTranscode = chkTranscode.isSelected();
            
            // Capture HLS settings
            boolean useHwAccel = cbHwAccel.isSelected();
            String segDuration = tfSegment.getText().trim();
            java.util.List<String> cmdArgs = new java.util.ArrayList<>();
            
            if (useTranscode) {
                // Qualities
                java.util.List<String> selectedQualities = new java.util.ArrayList<>();
                qualityChecks.forEach((q, cb) -> {
                    if (cb.isSelected()) selectedQualities.add(q);
                });
                
                if (selectedQualities.isEmpty()) {
                    new Alert(Alert.AlertType.WARNING, "Please select at least one resolution.").showAndWait();
                    return;
                }
                
                cmdArgs.add("--qualities");
                cmdArgs.addAll(selectedQualities);
                
                // Bitrates
                bitrateFields.forEach((q, tf) -> {
                    if (qualityChecks.get(q).isSelected()) {
                         cmdArgs.add("--bitrate_" + q);
                         cmdArgs.add(tf.getText().trim());
                    }
                });
                
                if (useHwAccel) cmdArgs.add("--hwaccel");
                if (!segDuration.isEmpty()) {
                    cmdArgs.add("--segment_time");
                    cmdArgs.add(segDuration);
                }
            }

            // UI State Update
            isUploading.set(true);
            startBtn.setDisable(true);
            stopUploadBtn.setDisable(false);
            cancelBtn.setDisable(true); // Disable close during upload
            progressBox.setVisible(true);
            progressBar.setProgress(0);
            statusLabel.setText(useTranscode ? "Initializing Encoder..." : "Preparing upload...");
            consoleArea.clear();
            if (useTranscode) consolePane.setExpanded(true);

            // Background Thread
            Thread t = new Thread(() -> {
                try {
                    if (useTranscode) {
                        // === PYTHON TRANSCODE FLOW ===
                        
                        // 1. Prepare Config
                        Map<String, String> r2Config = new HashMap<>();
                        r2Config.put("r2_endpoint_url", "https://" + ConfigService.getR2AccountId() + ".r2.cloudflarestorage.com");
                        r2Config.put("r2_access_key_id", ConfigService.getR2AccessKey());
                        r2Config.put("r2_secret_access_key", ConfigService.getR2SecretKey());
                        r2Config.put("r2_bucket_name", ConfigService.getR2BucketName());
                        r2Config.put("r2_public_domain", ConfigService.getR2PublicUrl());
                        
                        Path videoEncoderDir = Paths.get("videoEncoder");
                        if (!Files.exists(videoEncoderDir)) {
                            throw new Exception("videoEncoder directory not found. Please ensure it exists in the app root.");
                        }
                        
                        Path configPath = videoEncoderDir.resolve("r2_config.json");
                        try (FileWriter writer = new FileWriter(configPath.toFile())) {
                            gson.toJson(r2Config, writer);
                        }
                        
                        // 2. Prepare Output Dir (Temp)
                        Path tempDir = Files.createTempDirectory("educaster_hls_");
                        
                        // 3. Run Python Script
                        // Command: python convert.py <input> <output> --parallel [options]
                        java.util.List<String> command = new java.util.ArrayList<>();
                        command.add("python");
                        command.add("convert.py");
                        command.add(file.getAbsolutePath());
                        command.add(tempDir.toAbsolutePath().toString());
                        command.add("--parallel");
                        // command.add("--single_folder"); // Removed to support structured HLS output (folders per quality)
                        command.addAll(cmdArgs);
                        
                        ProcessBuilder pb = new ProcessBuilder(command);
                        pb.directory(videoEncoderDir.toFile());
                        pb.redirectErrorStream(true);
                        
                        Process p = pb.start();
                        pythonProcess[0] = p;
                        
                        // Monitor Output
                        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                                new java.io.InputStreamReader(p.getInputStream()))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                if (Thread.currentThread().isInterrupted()) {
                                    p.destroy();
                                    break;
                                }
                                String finalLine = line;
                                Platform.runLater(() -> {
                                    detailsLabel.setText(finalLine);
                                    if (finalLine.contains("Starting encoding")) progressBar.setProgress(0.1);
                                    if (finalLine.contains("Finished processing")) progressBar.setProgress(0.9);
                                });
                            }
                        }
                        
                        int exitCode = p.waitFor();
                        if (exitCode != 0) {
                            throw new Exception("Encoder exited with code " + exitCode + ". Check logs.");
                        }
                        
                        Platform.runLater(() -> {
                            statusLabel.setText("Transcoding & Upload Complete!");
                            progressBar.setProgress(1.0);
                            new Alert(Alert.AlertType.INFORMATION, "Video Transcoded & Uploaded via EduCaster!").showAndWait();
                            if (onComplete != null) onComplete.run();
                            stage.close();
                        });
                        
                    } else {
                        // === STANDARD UPLOAD FLOW ===
                        R2Service.R2ProgressListener listener = (bytesTransferred, totalBytes, currentFile, speedMBps, timeRemainingSeconds) -> {
                            Platform.runLater(() -> {
                                double progress = (double) bytesTransferred / totalBytes;
                                progressBar.setProgress(progress);
                                statusLabel.setText("Uploading: " + currentFile);
                                
                                String timeStr = formatTime(timeRemainingSeconds);
                                detailsLabel.setText(String.format("%.1f%% - %.2f MB/s - %s remaining", progress * 100, speedMBps, timeStr));
                                
                                transferredLabel.setText(String.format("%s / %s", 
                                    humanReadableByteCount(bytesTransferred), 
                                    humanReadableByteCount(totalBytes)));
                            });
                        };
    
                        if (isFolder) {
                            r2Service.uploadFolder(file.toPath(), prefix, listener);
                        } else {
                            String key = prefix;
                            if (!key.endsWith("/") && !key.isEmpty()) key += "/";
                            key += file.getName();
                            
                            String contentType = Files.probeContentType(file.toPath());
                            if (contentType == null) contentType = "application/octet-stream";
                            
                            r2Service.uploadFile(key, file.toPath(), contentType, listener);
                        }
                        
                        Platform.runLater(() -> {
                            statusLabel.setText("Upload Complete!");
                            progressBar.setProgress(1.0);
                            detailsLabel.setText("Finished.");
                            new Alert(Alert.AlertType.INFORMATION, "Upload successful!").showAndWait();
                            if (onComplete != null) onComplete.run();
                            stage.close();
                        });
                    }

                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        String msg = ex.getMessage();
                        if (msg != null && (msg.contains("Upload cancelled") || ex.getCause() instanceof InterruptedException)) {
                            statusLabel.setText("Cancelled");
                            detailsLabel.setText("Upload cancelled by user.");
                        } else {
                            statusLabel.setText("Error: " + msg);
                            new Alert(Alert.AlertType.ERROR, "Process failed: " + msg).showAndWait();
                        }
                        isUploading.set(false);
                        startBtn.setDisable(false);
                        stopUploadBtn.setDisable(true);
                        cancelBtn.setDisable(false);
                    });
                }
            }, "r2-upload-worker");
            t.setDaemon(true);
            uploadThread[0] = t;
            t.start();
        });

        stopUploadBtn.setOnAction(e -> {
            if (uploadThread[0] != null && uploadThread[0].isAlive()) {
                statusLabel.setText("Cancelling...");
                uploadThread[0].interrupt();
                if (pythonProcess[0] != null && pythonProcess[0].isAlive()) {
                    pythonProcess[0].destroy();
                }
            }
        });

        // Close logic with warning
        Runnable closeLogic = () -> {
            if (isUploading.get()) {
                Alert alert = new Alert(Alert.AlertType.CONFIRMATION, 
                    "An upload/transcode is currently in progress.\nClosing this window will cancel it.\nAre you sure?", 
                    ButtonType.YES, ButtonType.NO);
                alert.setTitle("Cancel Process?");
                alert.setHeaderText("Process in Progress");
                
                alert.showAndWait().ifPresent(response -> {
                    if (response == ButtonType.YES) {
                        if (uploadThread[0] != null && uploadThread[0].isAlive()) {
                            uploadThread[0].interrupt();
                        }
                        if (pythonProcess[0] != null && pythonProcess[0].isAlive()) {
                            pythonProcess[0].destroy();
                        }
                        stage.close();
                    }
                });
            } else {
                stage.close();
            }
        };

        cancelBtn.setOnAction(e -> closeLogic.run());
        stage.setOnCloseRequest(e -> {
            e.consume(); // Always consume first, decide manually
            closeLogic.run();
        });

        stage.setScene(new Scene(root, 500, 350));
        stage.showAndWait();
    }

    private String formatTime(long seconds) {
        if (seconds < 60) return seconds + "s";
        long mins = seconds / 60;
        long secs = seconds % 60;
        if (mins < 60) return mins + "m " + secs + "s";
        long hours = mins / 60;
        mins = mins % 60;
        return hours + "h " + mins + "m";
    }

    private String humanReadableByteCount(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp-1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }
}
