package com.educater.r2;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.nio.file.Path;
import java.nio.file.Files;
import java.io.IOException;

// Example Controller for Spring Boot
// Note: This requires Spring Boot dependencies which may not be fully active in this desktop app,
// but serves as the requested example implementation.

@RestController
@RequestMapping("/api/r2")
@CrossOrigin(origins = "*") // Allow frontend access
public class R2Controller {

    private final R2Service r2Service;

    // Constructor injection
    public R2Controller() {
        // In a real Spring app, inject config via @Value or ConfigurationProperties
        String accountId = "your-account-id"; // or ConfigService.getR2AccountId()
        String accessKey = "your-access-key";
        String secretKey = "your-secret-key";
        String bucketName = "your-bucket-name";
        String publicUrl = "your-public-url";
        
        this.r2Service = new R2Service(accountId, accessKey, secretKey, bucketName, publicUrl);
    }

    @PostMapping("/presigned-url")
    public ResponseEntity<PresignedUrlResponse> getPresignedUrl(@RequestBody PresignedUrlRequest request) {
        try {
            String url = r2Service.generatePresignedUploadUrl(request.getFileName(), request.getContentType());
            String publicUrl = r2Service.getPublicUrl(request.getFileName());
            return ResponseEntity.ok(new PresignedUrlResponse(url, request.getFileName(), publicUrl));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @DeleteMapping("/file")
    public ResponseEntity<Void> deleteFile(@RequestParam String key) {
        r2Service.deleteFile(key);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/folder")
    public ResponseEntity<Void> deleteFolder(@RequestParam String prefix) {
        r2Service.deleteFolder(prefix);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/usage")
    public ResponseEntity<R2Service.StorageUsage> getUsage() {
        return ResponseEntity.ok(r2Service.getStorageUsage());
    }

    // DTOs
    public static class PresignedUrlRequest {
        private String fileName;
        private String contentType;

        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
        public String getContentType() { return contentType; }
        public void setContentType(String contentType) { this.contentType = contentType; }
    }

    public static class PresignedUrlResponse {
        private String uploadUrl;
        private String key;
        private String publicUrl;

        public PresignedUrlResponse(String uploadUrl, String key, String publicUrl) {
            this.uploadUrl = uploadUrl;
            this.key = key;
            this.publicUrl = publicUrl;
        }

        public String getUploadUrl() { return uploadUrl; }
        public String getKey() { return key; }
        public String getPublicUrl() { return publicUrl; }
    }
}
