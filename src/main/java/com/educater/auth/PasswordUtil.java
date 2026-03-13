package com.educater.auth;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

public class PasswordUtil {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int SALT_LEN = 16;
    private static final int ITERATIONS = 65536;
    private static final int KEY_LEN = 256; // bits

    public static byte[] generateSalt() {
        byte[] s = new byte[SALT_LEN];
        RANDOM.nextBytes(s);
        return s;
    }

    public static byte[] hashPassword(char[] password, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, KEY_LEN);
        SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        byte[] key = skf.generateSecret(spec).getEncoded();
        spec.clearPassword();
        return key;
    }

    public static boolean verifyPassword(char[] attempted, String storedHashBase64, String storedSaltBase64) throws Exception {
        byte[] salt = Base64.getDecoder().decode(storedSaltBase64);
        byte[] expected = Base64.getDecoder().decode(storedHashBase64);
        byte[] attemptedHash = hashPassword(attempted, salt);
        boolean ok = slowEquals(attemptedHash, expected);
        java.util.Arrays.fill(attempted, '\0');
        java.util.Arrays.fill(attemptedHash, (byte)0);
        return ok;
    }

    private static boolean slowEquals(byte[] a, byte[] b) {
        if (a.length != b.length) return false;
        int diff = 0;
        for (int i = 0; i < a.length; i++) diff |= a[i] ^ b[i];
        return diff == 0;
    }
}