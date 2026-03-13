package com.educater.model;

import org.bson.types.ObjectId;

public class StreamRecord {
    public ObjectId id;
    public String email;
    public String rtmpUrl;
    public String streamKey;
    public String playbackId;
    public String liveStreamId;
    public String createdAtIso;

    @Override
    public String toString() {
        return (playbackId != null ? playbackId : "-") + " — " + (createdAtIso != null ? createdAtIso : "");
    }
}