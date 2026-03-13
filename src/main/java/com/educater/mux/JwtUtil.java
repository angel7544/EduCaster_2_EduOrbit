package com.educater.mux;

import com.educater.config.ConfigService;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

public class JwtUtil {
    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String signHs256(Map<String, Object> payloadClaims, long ttlSeconds) throws Exception {
        String secret = ConfigService.getMuxSigningSecret();
        String kid = ConfigService.getMuxSigningKeyId();

        long iat = System.currentTimeMillis() / 1000L;
        long exp = iat + ttlSeconds;

        StringBuilder header = new StringBuilder("{\"alg\":\"HS256\",\"typ\":\"JWT\"");
        if (kid != null && !kid.isBlank()) header.append(",\"kid\":\"" + kid + "\"");
        header.append("}");

        StringBuilder payload = new StringBuilder("{");
        payload.append("\"iat\":").append(iat).append(",\"exp\":").append(exp);
        if (payloadClaims != null) {
            for (Map.Entry<String, Object> e : payloadClaims.entrySet()) {
                Object v = e.getValue();
                payload.append(",\"").append(e.getKey()).append("\":");
                if (v instanceof Number || v instanceof Boolean) {
                    payload.append(v.toString());
                } else {
                    payload.append("\"").append(v.toString()).append("\"");
                }
            }
        }
        payload.append("}");

        String header64 = base64Url(header.toString().getBytes(StandardCharsets.UTF_8));
        String payload64 = base64Url(payload.toString().getBytes(StandardCharsets.UTF_8));
        String signingInput = header64 + "." + payload64;

        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] sig = mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
        String sig64 = base64Url(sig);
        return signingInput + "." + sig64;
    }
}