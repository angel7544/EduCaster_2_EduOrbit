package com.educater.net;

import java.net.HttpURLConnection;
import java.net.URL;

public class NetUtil {
    // Lightweight online check using a 204 endpoint; falls back to api.mux.com
    public static boolean isOnline() {
        return isReachable("https://www.google.com/generate_204", 3000)
                || isReachable("https://api.mux.com", 4000);
    }

    private static boolean isReachable(String urlStr, int timeoutMs) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection c = (HttpURLConnection) url.openConnection();
            c.setRequestMethod("HEAD");
            c.setConnectTimeout(timeoutMs);
            c.setReadTimeout(timeoutMs);
            int code = c.getResponseCode();
            return code >= 200 && code < 400; // consider 2xx/3xx as reachable
        } catch (Exception ignored) {
            return false;
        }
    }
}