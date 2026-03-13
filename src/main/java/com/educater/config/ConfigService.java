package com.educater.config;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Simple configuration service backed by a properties file in the user's home directory.
 * Keys supported: ADMIN_EMAIL, ADMIN_PASSWORD, MUX_TOKEN_ID, MUX_TOKEN_SECRET,
 * MUX_SIGNING_SECRET, MUX_SIGNING_KEY_ID, MONGO_URI, MONGO_DB.
 */
public class ConfigService {
    private static final Path CONFIG_DIR = Paths.get(System.getProperty("user.home"), ".educater");
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("config.properties");

    private static final Properties props = new Properties();
    private static boolean loaded = false;

    private static synchronized void ensureLoaded() {
        if (loaded) return;
        try {
            if (!Files.exists(CONFIG_DIR)) {
                Files.createDirectories(CONFIG_DIR);
            }
            if (Files.exists(CONFIG_FILE)) {
                try (FileInputStream fis = new FileInputStream(CONFIG_FILE.toFile())) {
                    props.load(fis);
                }
            }
        } catch (IOException ignored) {}
        loaded = true;
    }

    private static synchronized void persist() {
        try {
            if (!Files.exists(CONFIG_DIR)) {
                Files.createDirectories(CONFIG_DIR);
            }
            try (FileOutputStream fos = new FileOutputStream(CONFIG_FILE.toFile())) {
                props.store(fos, "Educater configuration");
            }
        } catch (IOException ignored) {}
    }

    private static String get(String key, String fallback) {
        ensureLoaded();
        String v = props.getProperty(key);
        return v != null && !v.isBlank() ? v : fallback;
    }

    private static void set(String key, String value) {
        ensureLoaded();
        if (value == null) value = "";
        props.setProperty(key, value);
        persist();
    }

    // Admin
    public static String getAdminEmail() {
        return get("ADMIN_EMAIL", HardcodedConfig.ADMIN_EMAIL);
    }

    public static String getAdminPassword() {
        return get("ADMIN_PASSWORD", HardcodedConfig.ADMIN_PASSWORD);
    }

    public static void setAdminEmail(String email) { set("ADMIN_EMAIL", email); }
    public static void setAdminPassword(String password) { set("ADMIN_PASSWORD", password); }

    // Mongo
    public static String getMongoUri() {
        return get("MONGO_URI", HardcodedConfig.MONGO_URI);
    }
    public static String getMongoDb() {
        return get("MONGO_DB", HardcodedConfig.MONGO_DB);
    }

    public static void setMongoUri(String uri) { set("MONGO_URI", uri); }
    public static void setMongoDb(String db) { set("MONGO_DB", db); }

    // Mux credentials
    public static String getMuxTokenId() { return get("MUX_TOKEN_ID", HardcodedConfig.MUX_TOKEN_ID); }
    public static String getMuxTokenSecret() { return get("MUX_TOKEN_SECRET", HardcodedConfig.MUX_TOKEN_SECRET); }
    public static String getMuxSigningSecret() { return get("MUX_SIGNING_SECRET", HardcodedConfig.MUX_SIGNING_SECRET); }
    public static String getMuxSigningKeyId() { return get("MUX_SIGNING_KEY_ID", HardcodedConfig.MUX_SIGNING_KEY_ID); }

    public static void setMuxTokenId(String v) { set("MUX_TOKEN_ID", v); }
    public static void setMuxTokenSecret(String v) { set("MUX_TOKEN_SECRET", v); }
    public static void setMuxSigningSecret(String v) { set("MUX_SIGNING_SECRET", v); }
    public static void setMuxSigningKeyId(String v) { set("MUX_SIGNING_KEY_ID", v); }

    // OBS Path
    public static String getObsPath() { return get("OBS_PATH", HardcodedConfig.OBS_PATH); }
    public static void setObsPath(String path) { set("OBS_PATH", path); }

    // R2 Credentials
    public static String getR2AccountId() { return get("R2_ACCOUNT_ID", ""); }
    public static void setR2AccountId(String v) { set("R2_ACCOUNT_ID", v); }

    public static String getR2AccessKey() { return get("R2_ACCESS_KEY", ""); }
    public static void setR2AccessKey(String v) { set("R2_ACCESS_KEY", v); }

    public static String getR2SecretKey() { 
        String enc = get("R2_SECRET_KEY", "");
        return decrypt(enc);
    }
    public static void setR2SecretKey(String v) { set("R2_SECRET_KEY", encrypt(v)); }

    public static String getR2BucketName() { return get("R2_BUCKET_NAME", ""); }
    public static void setR2BucketName(String v) { set("R2_BUCKET_NAME", v); }

    public static String getR2PublicUrl() { return get("R2_PUBLIC_URL", ""); }
    public static void setR2PublicUrl(String v) { set("R2_PUBLIC_URL", v); }

    // Simple encryption/obfuscation helper
    private static String encrypt(String value) {
        if (value == null || value.isEmpty()) return "";
        return java.util.Base64.getEncoder().encodeToString(value.getBytes());
    }

    private static String decrypt(String value) {
        if (value == null || value.isEmpty()) return "";
        try {
            return new String(java.util.Base64.getDecoder().decode(value));
        } catch (IllegalArgumentException e) {
            return "";
        }
    }
}