package com.educater.mux;

public class UploadInfo {
    private final String uploadId;
    private final String uploadUrl;

    public UploadInfo(String uploadId, String uploadUrl) {
        this.uploadId = uploadId;
        this.uploadUrl = uploadUrl;
    }

    public String getUploadId() {
        return uploadId;
    }

    public String getUploadUrl() {
        return uploadUrl;
    }
}
