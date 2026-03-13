package com.educater.mux;

import com.educater.config.ConfigService;
import com.educater.net.NetUtil;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class MuxApi {
    private final Gson gson = new Gson();

    public MuxApi() { }

    // ---------------------------------------------------
    // CREATE LIVE STREAM (FREE PLAN OK)
  public LiveStreamInfo createLiveStream() throws Exception {
    String url = "https://api.mux.com/video/v1/live-streams";

    JsonObject root = new JsonObject();

    // playback_policies: ["public"]
    JsonArray rootPolicies = new JsonArray();
    rootPolicies.add("public");
    root.add("playback_policies", rootPolicies);

    // new_asset_settings: { playback_policies: ["public"] }
    JsonObject newAsset = new JsonObject();
    JsonArray assetPolicies = new JsonArray();
    assetPolicies.add("public");
    newAsset.add("playback_policies", assetPolicies);

    root.add("new_asset_settings", newAsset);

    // Convert to JSON string
    String body = gson.toJson(root);
    System.out.println("[Mux] createLiveStream: POST " + url);
    System.out.println("[Mux] createLiveStream body: " + body);

    JsonObject response = doRequest("POST", url, body);
    System.out.println("[Mux] createLiveStream raw response: " + (response != null ? response.toString() : "<null>"));

    JsonObject data = requireData(response);
    System.out.println("[Mux] createLiveStream data: " + data);

    // Mux's create live stream response does not include an ingest URL.
    // Use the default RTMPS server URL from Mux docs.
    String ingestUrl = "rtmps://global-live.mux.com:443/app";

    if (!data.has("stream_key")) {
        throw new Exception("Mux createLiveStream: missing stream_key in response");
    }
    String streamKey = data.get("stream_key").getAsString();

    if (!data.has("id")) {
        throw new Exception("Mux createLiveStream: missing id in response");
    }
    String liveId = data.get("id").getAsString();

    String playbackId = "";
    if (data.has("playback_ids") && data.get("playback_ids").isJsonArray()) {
        System.out.println("[Mux] playback_ids count: " + data.get("playback_ids").getAsJsonArray().size());
        if (data.get("playback_ids").getAsJsonArray().size() > 0) {
            JsonObject first = data.get("playback_ids").getAsJsonArray().get(0).getAsJsonObject();
            if (first.has("id")) playbackId = first.get("id").getAsString();
        }
    }

    System.out.println("[Mux] Parsed createLiveStream: rtmpUrl=" + ingestUrl + ", streamKey=" + streamKey + ", playbackId=" + playbackId + ", liveId=" + liveId);
    return new LiveStreamInfo(ingestUrl, streamKey, playbackId, liveId);
  }

    // ---------------------------------------------------
    // LIVE STREAM MANAGEMENT
    // ---------------------------------------------------
    public JsonObject getLiveStream(String liveId) throws Exception {
        String url = "https://api.mux.com/video/v1/live-streams/" + liveId;
        return requireData(doRequest("GET", url, null));
    }

    public void disableLiveStream(String liveId) throws Exception {
        String url = "https://api.mux.com/video/v1/live-streams/" + liveId + "/disable";
        // Mux returns {data: {}} on success
        requireData(doRequest("POST", url, "{}"));
    }

    public void enableLiveStream(String liveId) throws Exception {
        String url = "https://api.mux.com/video/v1/live-streams/" + liveId + "/enable";
        requireData(doRequest("POST", url, "{}"));
    }

    public void completeLiveStream(String liveId) throws Exception {
        String url = "https://api.mux.com/video/v1/live-streams/" + liveId + "/complete";
        requireData(doRequest("PUT", url, "{}"));
    }

    public void deleteLiveStream(String liveId) throws Exception {
        String url = "https://api.mux.com/video/v1/live-streams/" + liveId;
        // DELETE returns 204 No Content; our wrapper expects JSON, so handle gracefully
        doRequest("DELETE", url, null);
    }

    // ---------------------------------------------------
    // DIRECT UPLOAD (FREE PLAN SAFE)
    // ---------------------------------------------------
    public UploadInfo createDirectUpload(String playbackPolicy, String titlePassthrough) throws Exception {
        String url = "https://api.mux.com/video/v1/uploads";

        String policy = (playbackPolicy == null || playbackPolicy.isBlank()) ? "public" : playbackPolicy;

        // Build clean JSON
        StringBuilder body = new StringBuilder();
        body.append("{");
        body.append("\"new_asset_settings\": {");
        body.append("\"playback_policies\": [\"").append(policy).append("\"]");

        if (titlePassthrough != null && !titlePassthrough.isBlank()) {
            body.append(", \"passthrough\": \"")
                    .append(titlePassthrough.replace("\"", "\\\""))
                    .append("\"");
        }

        body.append("}");   // closes new_asset_settings
        body.append("}");   // closes entire body

        JsonObject response = doRequest("POST", url, body.toString());
        JsonObject data = requireData(response);

        return new UploadInfo(
                data.get("id").getAsString(),
                data.get("url").getAsString()
        );
    }

    // ---------------------------------------------------
    // UPLOAD FILE TO SIGNED URL
    // ---------------------------------------------------
    public void uploadFileToUrl(String uploadUrl, java.io.File file) throws Exception {
        URL url = new URL(uploadUrl);
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setRequestMethod("PUT");
        c.setRequestProperty("Content-Type", "application/octet-stream");
        c.setDoOutput(true);
        c.setFixedLengthStreamingMode(file.length());

        try (java.io.OutputStream os = c.getOutputStream();
             java.io.InputStream is = new java.io.FileInputStream(file)) {

            byte[] buf = new byte[8192];
            int read;

            while ((read = is.read(buf)) != -1) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException("Upload cancelled by user");
                }
                os.write(buf, 0, read);
            }
        }

        int code = c.getResponseCode();
        if (code < 200 || code >= 300) {
            InputStream es = c.getErrorStream();
            String err = es != null ? new String(es.readAllBytes(), StandardCharsets.UTF_8)
                    : ("HTTP " + code);

            throw new Exception("Upload failed: " + err);
        }
    }

    // ---------------------------------------------------
    // GET ASSET ID FROM UPLOAD ID
    // ---------------------------------------------------
    public String getUploadAssetId(String uploadId) throws Exception {
        String url = "https://api.mux.com/video/v1/uploads/" + uploadId;

        JsonObject data = requireData(doRequest("GET", url, null));

        if (data.has("asset_id") && !data.get("asset_id").isJsonNull()) {
            return data.get("asset_id").getAsString();
        }
        return null;
    }

    // ---------------------------------------------------
    // GET PLAYBACK ID FROM ASSET ID
    // ---------------------------------------------------
    public String getAssetPlaybackId(String assetId) throws Exception {
        String url = "https://api.mux.com/video/v1/assets/" + assetId;

        JsonObject data = requireData(doRequest("GET", url, null));

        if (data.has("playback_ids")
                && data.get("playback_ids").isJsonArray()
                && data.get("playback_ids").getAsJsonArray().size() > 0) {

            return data.get("playback_ids").getAsJsonArray()
                    .get(0).getAsJsonObject().get("id").getAsString();
        }
        return null;
    }
public int getConcurrentViewersByLiveId(String liveId) throws Exception {
    String url = "https://api.mux.com/data/v1/metrics/concurrent-viewers?filters[]=live_stream_id:" + liveId;

    JsonObject obj = doRequest("GET", url, null);

    if (obj.has("data") && obj.get("data").isJsonObject()) {
        JsonObject data = obj.getAsJsonObject("data");
        if (data.has("value") && !data.get("value").isJsonNull()) {
            return data.get("value").getAsInt();
        }
    }

    return 0;
}
    // ---------------------------------------------------
    // LOW-LEVEL HTTP REQUEST WRAPPER
    // ---------------------------------------------------
    private JsonObject doRequest(String method, String urlStr, String body) throws Exception {

        if (!NetUtil.isOnline()) {
            throw new Exception("No internet connection.");
        }

        URL url = new URL(urlStr);
        HttpURLConnection c = (HttpURLConnection) url.openConnection();

        c.setRequestMethod(method);
        c.setRequestProperty("Content-Type", "application/json");
        // Be explicit with timeouts to avoid hanging sockets
        c.setConnectTimeout(8000);
        c.setReadTimeout(15000);
        c.setUseCaches(false);

        // Basic Auth
        String tokenId = ConfigService.getMuxTokenId();
        String tokenSecret = ConfigService.getMuxTokenSecret();
        String basic = Base64.getEncoder()
                .encodeToString((tokenId + ":" + tokenSecret).getBytes(StandardCharsets.UTF_8));

        c.setRequestProperty("Authorization", "Basic " + basic);

        if (body != null) {
            c.setDoOutput(true);
            try (OutputStream os = c.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }
        }

        int code = c.getResponseCode();

        InputStream is = (code >= 200 && code < 300)
                ? c.getInputStream()
                : c.getErrorStream();

        String response = "";
        if (is != null) {
            response = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }

        // Console diagnostics
        try {
            String bodySnippet = body == null ? "" : body.substring(0, Math.min(300, body.length())).replace('\n', ' ');
            String respSnippet = response == null ? "" : response.substring(0, Math.min(400, response.length())).replace('\n', ' ');
            System.out.println("[Mux] " + method + " " + urlStr + " -> HTTP " + code);
            if (!bodySnippet.isBlank()) System.out.println("[Mux] request body: " + bodySnippet);
            if (!respSnippet.isBlank()) System.out.println("[Mux] response: " + respSnippet);
        } catch (Throwable ignored) { }

        JsonObject obj;
        try {
            obj = gson.fromJson(response.isBlank() ? "{}" : response, JsonObject.class);
        } catch (Exception parseEx) {
            // Non-JSON response (e.g., HTML error page); preserve raw for diagnostics
            obj = new JsonObject();
            if (!response.isBlank()) {
                obj.addProperty("_raw", response);
            }
        }

        if (code < 200 || code >= 300) {
            String msg = "HTTP " + code;

            JsonObject err = (obj.has("error") && obj.get("error").isJsonObject())
                    ? obj.getAsJsonObject("error")
                    : null;

            if (err != null) {
                if (err.has("message") && !err.get("message").isJsonNull())
                    msg += ": " + err.get("message").getAsString();

                if (err.has("type") && !err.get("type").isJsonNull())
                    msg += " (" + err.get("type").getAsString() + ")";
            } else if (obj.has("_raw")) {
                String raw = obj.get("_raw").getAsString();
                String snippet = raw.substring(0, Math.min(200, raw.length())).replace('\n', ' ');
                msg += ": " + snippet;
            }

            System.err.println("[Mux] ERROR: " + msg);
            throw new Exception("Mux API error: " + msg);
        }

        // Some endpoints (e.g., DELETE) may return no JSON body.
        return obj == null ? new JsonObject() : obj;
    }

    // Helper: Ensure successful response contains a data object; otherwise throw a diagnostic error
    private JsonObject requireData(JsonObject response) throws Exception {
        if (response != null && response.has("data") && response.get("data").isJsonObject()) {
            return response.getAsJsonObject("data");
        }
        String raw = (response != null && response.has("_raw")) ? response.get("_raw").getAsString() : "";
        String snippet = raw == null ? "" : raw.substring(0, Math.min(200, raw.length())).replace('\n', ' ');
        throw new Exception("Invalid Mux response: missing 'data' object" + (snippet.isEmpty() ? "" : (". Raw: " + snippet)));
    }
}
