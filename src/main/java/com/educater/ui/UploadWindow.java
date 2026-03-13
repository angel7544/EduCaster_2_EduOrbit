package com.educater.ui;

import com.educater.model.UploadRecord;
import com.educater.mux.MuxApi;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.time.Instant;
import java.util.function.Consumer;

public class UploadWindow {
    private final MuxApi mux;
    private final Consumer<UploadRecord> onUploadHistory;

    public UploadWindow(MuxApi mux, Consumer<UploadRecord> onUploadHistory) {
        this.mux = mux;
        this.onUploadHistory = onUploadHistory;
    }

    public void show() {
        Stage stage = new Stage();
        stage.setTitle("Direct Upload");
        stage.initModality(Modality.APPLICATION_MODAL);

        TextField titleField = new TextField();
        titleField.setPromptText("Title (passthrough)");

        ComboBox<String> policyBox = new ComboBox<>();
        policyBox.getItems().addAll("public", "signed");
        policyBox.getSelectionModel().selectFirst();

        TextField fileField = new TextField();
        fileField.setEditable(false);
        Button browseBtn = new Button("Browse...");
        HBox fileRow = new HBox(8, fileField, browseBtn);
        fileRow.setAlignment(Pos.CENTER_LEFT);

        Label status = new Label("Ready");
        ProgressBar progress = new ProgressBar(0);
        progress.setPrefWidth(280);
        progress.setVisible(false);

        Button startBtn = new Button("Start Upload");
        Button stopUploadBtn = new Button("Cancel Upload");
        stopUploadBtn.setDisable(true);
        Button cancelBtn = new Button("Close");
        HBox actions = new HBox(10, startBtn, stopUploadBtn, cancelBtn);
        actions.setAlignment(Pos.CENTER_RIGHT);

        VBox root = new VBox(10,
                new Label("Upload a video to Mux"),
                new HBox(8, new Label("Policy:"), policyBox),
                titleField,
                new Label("File:"),
                fileRow,
                status,
                progress,
                actions);
        root.setPadding(new Insets(12));

        FileChooser fc = new FileChooser();
        fc.setTitle("Select Video File");
        
        // Use an array to hold the current thread reference
        final Thread[] uploadThread = new Thread[1];

        browseBtn.setOnAction(e -> {
            File f = fc.showOpenDialog(stage);
            if (f != null) fileField.setText(f.getAbsolutePath());
        });

        startBtn.setOnAction(e -> {
            String path = fileField.getText();
            if (path == null || path.isBlank()) {
                new Alert(Alert.AlertType.WARNING, "Choose a file first").showAndWait();
                return;
            }
            File file = new File(path);
            if (!file.exists()) {
                new Alert(Alert.AlertType.ERROR, "File not found").showAndWait();
                return;
            }
            startBtn.setDisable(true);
            stopUploadBtn.setDisable(false);
            cancelBtn.setDisable(true); // Disable close while uploading
            progress.setVisible(true);
            status.setText("Creating upload...");

            Thread t = new Thread(() -> {
                try {
                    String policy = policyBox.getSelectionModel().getSelectedItem();
                    String title = titleField.getText();
                    var info = mux.createDirectUpload(policy, title);
                    
                    if (Thread.currentThread().isInterrupted()) throw new InterruptedException("Cancelled");
                    
                    Platform.runLater(() -> status.setText("Uploading to Mux..."));
                    mux.uploadFileToUrl(info.getUploadUrl(), file);

                    Platform.runLater(() -> status.setText("Processing asset (mux)..."));

                    String assetId = null;
                    long start = System.currentTimeMillis();
                    while (assetId == null && System.currentTimeMillis() - start < 120_000) { // up to 2 minutes
                        if (Thread.currentThread().isInterrupted()) throw new InterruptedException("Cancelled");
                        assetId = mux.getUploadAssetId(info.getUploadId());
                        if (assetId == null) Thread.sleep(3000);
                    }
                    if (assetId == null) throw new Exception("Timed out waiting for asset_id");

                    String playbackId = mux.getAssetPlaybackId(assetId);
                    if (playbackId == null) throw new Exception("No playback id available yet");

                    // Build record here so the lambda captures an effectively-final object
                    UploadRecord rec = new UploadRecord();
                    rec.title = title;
                    rec.policy = policy;
                    rec.fileName = file.getName();
                    rec.assetId = assetId;
                    rec.playbackId = playbackId;
                    rec.createdAtIso = Instant.now().toString();

                    Platform.runLater(() -> {
                        new Alert(Alert.AlertType.INFORMATION,
                                "Upload complete!\nPlayback ID: " + rec.playbackId).showAndWait();
                        if (onUploadHistory != null) {
                            onUploadHistory.accept(rec);
                        }
                        stage.close();
                    });
                } catch (Exception ex2) {
                    Platform.runLater(() -> {
                        String msg = ex2.getMessage();
                        if (ex2 instanceof InterruptedException || "Upload cancelled by user".equals(msg)) {
                             status.setText("Cancelled");
                        } else {
                             new Alert(Alert.AlertType.ERROR, "Error: " + msg).showAndWait();
                             status.setText("Error");
                        }
                        startBtn.setDisable(false);
                        stopUploadBtn.setDisable(true);
                        cancelBtn.setDisable(false);
                        progress.setVisible(false);
                    });
                }
            }, "mux-upload");
            t.setDaemon(true);
            uploadThread[0] = t;
            t.start();
        });
        
        stopUploadBtn.setOnAction(e -> {
            if (uploadThread[0] != null && uploadThread[0].isAlive()) {
                uploadThread[0].interrupt();
                status.setText("Cancelling...");
            }
        });

        cancelBtn.setOnAction(e -> {
            if (uploadThread[0] != null && uploadThread[0].isAlive()) {
                Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                        "An upload is currently in progress.\nClosing this window will cancel the upload.\nAre you sure?",
                        ButtonType.YES, ButtonType.NO);
                alert.setTitle("Cancel Upload?");
                alert.setHeaderText("Upload in Progress");

                alert.showAndWait().ifPresent(response -> {
                    if (response == ButtonType.YES) {
                        uploadThread[0].interrupt();
                        stage.close();
                    }
                });
            } else {
                stage.close();
            }
        });

        stage.setOnCloseRequest(e -> {
            if (uploadThread[0] != null && uploadThread[0].isAlive()) {
                e.consume();
                Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                        "An upload is currently in progress.\nClosing this window will cancel the upload.\nAre you sure?",
                        ButtonType.YES, ButtonType.NO);
                alert.setTitle("Cancel Upload?");
                alert.setHeaderText("Upload in Progress");

                alert.showAndWait().ifPresent(response -> {
                    if (response == ButtonType.YES) {
                        uploadThread[0].interrupt();
                        stage.close();
                    }
                });
            }
        });

        stage.setScene(new Scene(root, 520, 240));
        stage.showAndWait();
    }
}