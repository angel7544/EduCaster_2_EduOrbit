package com.educater.r2;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.transfer.s3.S3TransferManager;
import software.amazon.awssdk.transfer.s3.model.CompletedFileUpload;
import software.amazon.awssdk.transfer.s3.model.UploadFileRequest;
import software.amazon.awssdk.transfer.s3.model.UploadFileRequest.Builder;
import software.amazon.awssdk.transfer.s3.progress.TransferListener;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class R2Service {

    private S3Client s3Client;
    private S3AsyncClient s3AsyncClient;
    private S3Presigner presigner;
    private S3TransferManager transferManager;
    private String bucketName;
    private String publicUrl;
    private String accountId;

    public interface R2ProgressListener {
        void onProgress(long bytesTransferred, long totalBytes, String currentFile, double speedMBps, long timeRemainingSeconds);
    }

    public R2Service(String accountId, String accessKey, String secretKey, String bucketName, String publicUrl) {
        this.accountId = accountId;
        this.bucketName = bucketName;
        this.publicUrl = publicUrl;
        
        // R2 endpoint: https://<accountid>.r2.cloudflarestorage.com
        String endpoint = String.format("https://%s.r2.cloudflarestorage.com", accountId);

        AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);
        StaticCredentialsProvider credsProvider = StaticCredentialsProvider.create(credentials);
        
        this.s3Client = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.US_EAST_1)
                .credentialsProvider(credsProvider)
                .build();
                
        this.s3AsyncClient = S3AsyncClient.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.US_EAST_1)
                .credentialsProvider(credsProvider)
                .multipartEnabled(true) // Enable high-level multipart
                .build();
        
        this.transferManager = S3TransferManager.builder()
                .s3Client(s3AsyncClient)
                .build();

        this.presigner = S3Presigner.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.US_EAST_1)
                .credentialsProvider(credsProvider)
                .build();
    }

    /**
     * Generate a pre-signed URL for uploading large files directly from client.
     */
    public String generatePresignedUploadUrl(String key, String contentType) {
        PutObjectRequest objectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(contentType)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(60))
                .putObjectRequest(objectRequest)
                .build();

        PresignedPutObjectRequest presignedRequest = presigner.presignPutObject(presignRequest);
        return presignedRequest.url().toString();
    }

    /**
     * Upload a file using S3TransferManager with progress tracking.
     */
    public String uploadFile(String key, Path filePath, String contentType, R2ProgressListener listener) {
        try {
            long totalBytes = Files.size(filePath);
            final long startTime = System.currentTimeMillis();
            
            UploadFileRequest.Builder requestBuilder = UploadFileRequest.builder()
                .putObjectRequest(req -> req.bucket(bucketName).key(key).contentType(contentType))
                .source(filePath);

            if (listener != null) {
                requestBuilder.addTransferListener(new TransferListener() {
                    @Override
                    public void bytesTransferred(Context.BytesTransferred context) {
                        long transferred = context.progressSnapshot().transferredBytes();
                        // Calculate speed and time remaining
                        long elapsed = System.currentTimeMillis() - startTime;
                        if (elapsed > 0) {
                            double speed = (double) transferred / elapsed * 1000.0 / (1024 * 1024); // MB/s
                            long remainingBytes = totalBytes - transferred;
                            long timeRemaining = (long) (remainingBytes / ((double) transferred / elapsed)); // ms
                            listener.onProgress(transferred, totalBytes, key, speed, timeRemaining / 1000);
                        }
                    }
                });
            }

            CompletedFileUpload upload = transferManager.uploadFile(requestBuilder.build()).completionFuture().get();
            return getPublicUrl(key);
        } catch (InterruptedException ie) {
             throw new RuntimeException("Upload cancelled", ie);
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload file: " + e.getMessage(), e);
        }
    }

    public String uploadFile(String key, Path filePath, String contentType) {
        return uploadFile(key, filePath, contentType, null);
    }

    /**
     * Upload a folder recursively.
     */
    public void uploadFolder(Path folderPath, String prefix, R2ProgressListener listener) {
        try {
            if (!prefix.endsWith("/") && !prefix.isEmpty()) {
                prefix += "/";
            }
            final String basePrefix = prefix;
            
            // 1. Calculate total size
            final AtomicLong totalBytes = new AtomicLong(0);
            final List<Path> filesToUpload = new ArrayList<>();
            try (Stream<Path> stream = Files.walk(folderPath)) {
                stream.filter(p -> !Files.isDirectory(p)).forEach(p -> {
                    try {
                        totalBytes.addAndGet(Files.size(p));
                        filesToUpload.add(p);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
            }

            long totalSize = totalBytes.get();
            final AtomicLong totalTransferred = new AtomicLong(0);
            final long startTime = System.currentTimeMillis();

            // 2. Upload files
            for (Path p : filesToUpload) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException("Upload cancelled");
                }
                String relativePath = folderPath.relativize(p).toString().replace("\\", "/");
                String key = basePrefix + relativePath;
                String probedType = Files.probeContentType(p);
                String contentType = probedType != null ? probedType : "application/octet-stream";

                long fileSize = Files.size(p);
                
                // We use a custom listener for each file to aggregate progress
                UploadFileRequest request = UploadFileRequest.builder()
                    .putObjectRequest(req -> req.bucket(bucketName).key(key).contentType(contentType))
                    .source(p)
                    .addTransferListener(new TransferListener() {
                        long fileTransferred = 0;
                        @Override
                        public void bytesTransferred(Context.BytesTransferred context) {
                            long delta = context.progressSnapshot().transferredBytes() - fileTransferred;
                            fileTransferred += delta;
                            long globalTransferred = totalTransferred.addAndGet(delta);
                            
                            if (listener != null) {
                                long elapsed = System.currentTimeMillis() - startTime;
                                if (elapsed > 0) {
                                    double speed = (double) globalTransferred / elapsed * 1000.0 / (1024 * 1024); // MB/s
                                    long remainingBytes = totalSize - globalTransferred;
                                    long timeRemaining = (long) (remainingBytes / ((double) globalTransferred / elapsed)); // ms
                                    listener.onProgress(globalTransferred, totalSize, relativePath, speed, timeRemaining / 1000);
                                }
                            }
                        }
                    })
                    .build();
                
                transferManager.uploadFile(request).completionFuture().get();
            }

        } catch (InterruptedException ie) {
             throw new RuntimeException("Upload cancelled", ie);
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload folder: " + e.getMessage(), e);
        }
    }

    /**
     * Delete a file.
     */
    public void deleteFile(String key) {
        DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();

        s3Client.deleteObject(deleteObjectRequest);
    }

    /**
     * Delete a folder (prefix). Note: S3/R2 doesn't have real folders, just prefixes.
     * This deletes all objects with the given prefix.
     */
    public void deleteFolder(String prefix) {
        if (!prefix.endsWith("/")) {
            prefix += "/";
        }
        
        ListObjectsV2Request listReq = ListObjectsV2Request.builder()
                .bucket(bucketName)
                .prefix(prefix)
                .build();

        ListObjectsV2Response listRes;
        do {
            listRes = s3Client.listObjectsV2(listReq);
            
            if (listRes.contents().isEmpty()) break;

            List<ObjectIdentifier> objectsToDelete = listRes.contents().stream()
                    .map(s3Object -> ObjectIdentifier.builder().key(s3Object.key()).build())
                    .collect(Collectors.toList());

            DeleteObjectsRequest deleteReq = DeleteObjectsRequest.builder()
                    .bucket(bucketName)
                    .delete(Delete.builder().objects(objectsToDelete).build())
                    .build();

            s3Client.deleteObjects(deleteReq);

            listReq = listReq.toBuilder().continuationToken(listRes.nextContinuationToken()).build();
        } while (listRes.isTruncated());
    }

    /**
     * Get storage usage (total size and file count).
     */
    public StorageUsage getStorageUsage() {
        long totalSize = 0;
        long fileCount = 0;

        ListObjectsV2Request listReq = ListObjectsV2Request.builder()
                .bucket(bucketName)
                .build();

        ListObjectsV2Response listRes;
        do {
            listRes = s3Client.listObjectsV2(listReq);
            for (S3Object s3Object : listRes.contents()) {
                totalSize += s3Object.size();
                fileCount++;
            }
            listReq = listReq.toBuilder().continuationToken(listRes.nextContinuationToken()).build();
        } while (listRes.isTruncated());

        return new StorageUsage(totalSize, fileCount);
    }

    public String getPublicUrl(String key) {
        if (publicUrl != null && !publicUrl.isEmpty()) {
             if (publicUrl.endsWith("/")) {
                 return publicUrl + key;
             }
             return publicUrl + "/" + key;
        }
        // Fallback to R2 dev URL if no custom domain set (though typically R2 requires custom domain for public access or worker)
        return String.format("https://%s.r2.dev/%s", bucketName, key); 
    }
    
    /**
     * List all folders (common prefixes) recursively.
     * Note: This can be expensive for very large buckets.
     */
    public List<String> listFolders() {
        List<String> folders = new ArrayList<>();
        // Root folders
        listFoldersRecursive("", folders);
        return folders;
    }

    private void listFoldersRecursive(String prefix, List<String> folders) {
        ListObjectsV2Request req = ListObjectsV2Request.builder()
                .bucket(bucketName)
                .delimiter("/")
                .prefix(prefix)
                .build();
        
        ListObjectsV2Response res;
        do {
            res = s3Client.listObjectsV2(req);
            for (CommonPrefix cp : res.commonPrefixes()) {
                folders.add(cp.prefix());
                listFoldersRecursive(cp.prefix(), folders);
            }
            req = req.toBuilder().continuationToken(res.nextContinuationToken()).build();
        } while (res.isTruncated());
    }

    /**
     * List files in the bucket with directory support.
     */
    public List<R2File> listFiles(String prefix) {
        ListObjectsV2Request.Builder reqBuilder = ListObjectsV2Request.builder()
                .bucket(bucketName)
                .delimiter("/"); // Use delimiter to group by folder
        
        if (prefix != null && !prefix.isEmpty()) {
            if (!prefix.endsWith("/")) prefix += "/";
            reqBuilder.prefix(prefix);
        }
        
        ListObjectsV2Request listReq = reqBuilder.build();
        List<R2File> files = new ArrayList<>();
        
        ListObjectsV2Response listRes;
        do {
            listRes = s3Client.listObjectsV2(listReq);
            
            // 1. Add Folders (CommonPrefixes)
            for (CommonPrefix prefixObj : listRes.commonPrefixes()) {
                // Strip the parent prefix to show just the folder name if desired, 
                // but usually we keep full key for navigation. 
                // However, for UI display we might want just the name. 
                // Let's store the full key (prefix) and let UI decide display.
                files.add(new R2File(prefixObj.prefix(), 0, "-", true));
            }

            // 2. Add Files
            for (S3Object s3Object : listRes.contents()) {
                // Skip the folder placeholder object itself if it exists (key ending in /)
                if (s3Object.key().equals(prefix)) continue;
                files.add(new R2File(s3Object.key(), s3Object.size(), s3Object.lastModified().toString(), false));
            }
            
            if (listRes.isTruncated()) {
                listReq = listReq.toBuilder().continuationToken(listRes.nextContinuationToken()).build();
            } else {
                break;
            }
        } while (listRes.isTruncated());
        
        return files;
    }

    public static class R2File {
        private final String key;
        private final long size;
        private final String lastModified;
        private final boolean isFolder;
        
        public R2File(String key, long size, String lastModified) {
            this(key, size, lastModified, false);
        }

        public R2File(String key, long size, String lastModified, boolean isFolder) {
            this.key = key;
            this.size = size;
            this.lastModified = lastModified;
            this.isFolder = isFolder;
        }
        
        public String getKey() { return key; }
        public long getSize() { return size; }
        public String getLastModified() { return lastModified; }
        public boolean isFolder() { return isFolder; }
    }

    public static class StorageUsage {
        private final long totalSizeBytes;
        private final long fileCount;

        public StorageUsage(long totalSizeBytes, long fileCount) {
            this.totalSizeBytes = totalSizeBytes;
            this.fileCount = fileCount;
        }

        public long getTotalSizeBytes() { return totalSizeBytes; }
        public long getFileCount() { return fileCount; }
    }
}
