package com.educater.config;

public class HardcodedConfig {
    // Admin credentials
    public static final String ADMIN_EMAIL = "admin@br31tech.live";
    public static final String ADMIN_PASSWORD = "admin@angel";

    // Database (PostgreSQL-style variables requested; project uses MongoDB)
    public static final String DB_URL = "jdbc:postgresql://localhost:5432/muxapp";
    public static final String DB_USER = "muxuser";
    public static final String DB_PASS = "muxpass";

    // MongoDB URI (added to support MongoDB usage)
    public static final String MONGO_URI = "mongodb://localhost:27017";
    public static final String MONGO_DB = "educater";

    // Mux credentials
    public static final String MUX_TOKEN_ID = ""; // set yours or via env
    public static final String MUX_TOKEN_SECRET = ""; // set yours or via env
    public static final String MUX_SIGNING_SECRET = ""; // HS256 shared secret
    // Optional: signing key id if available for Mux playback tokens
    public static final String MUX_SIGNING_KEY_ID = ""; // optional

    // OBS Path
    public static final String OBS_PATH = "";

    // Force to use these hardcoded values instead of environment variables
    public static final boolean FORCE_USE_HARDCODED = true;

    public static String getOrEnv(String hardcoded, String envName) {
        if (FORCE_USE_HARDCODED) return hardcoded;
        String env = System.getenv(envName);
        return env != null && !env.isEmpty() ? env : hardcoded;
    }
}