package com.educater.mux;

public class LiveStreamInfo {
    public final String rtmpUrl;
    public final String streamKey;
    public final String playbackId;
    public final String liveId;

    public LiveStreamInfo(String r, String s, String p, String l) {
        this.rtmpUrl = r;
        this.streamKey = s;
        this.playbackId = p;
        this.liveId = l;
    }
}