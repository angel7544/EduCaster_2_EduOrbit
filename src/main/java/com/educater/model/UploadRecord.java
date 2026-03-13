package com.educater.model;

import org.bson.types.ObjectId;

public class UploadRecord {
    public ObjectId id;
    public String email;
    public String title;
    public String policy;
    public String fileName;
    public String assetId;
    public String playbackId;
    public String createdAtIso;

    @Override
    public String toString() {
        String t = title != null && !title.isBlank() ? title : "(untitled)";
        String p = policy != null ? policy : "-";
        String pid = playbackId != null ? playbackId : "-";
        String ts = createdAtIso != null ? createdAtIso : "";
        return t + " [" + p + "] — " + pid + " — " + ts;
    }
}