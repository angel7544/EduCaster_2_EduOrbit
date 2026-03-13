package com.educater.auth;

import com.educater.config.ConfigService;
import com.educater.db.MongoService;
import com.educater.model.User;
import java.util.Base64;

public class AuthService {
    private final MongoService mongo;

    public AuthService(MongoService mongo) {
        this.mongo = mongo;
    }

    public boolean isAdminLogin(String email, char[] password) {
        String adminEmail = ConfigService.getAdminEmail();
        String adminPass = ConfigService.getAdminPassword();
        boolean ok = adminEmail.equals(email) && new String(password).equals(adminPass);
        java.util.Arrays.fill(password, '\0');
        return ok;
    }

    public boolean signupTeacher(String email, char[] password) throws Exception {
        if (email == null || email.isBlank() || password == null || password.length == 0) return false;
        // Check if user exists
        if (mongo.findUserByEmail(email) != null) return false;

        byte[] salt = PasswordUtil.generateSalt();
        byte[] hash = PasswordUtil.hashPassword(password, salt);
        String saltB = Base64.getEncoder().encodeToString(salt);
        String hashB = Base64.getEncoder().encodeToString(hash);
        User u = new User();
        u.email = email;
        u.passwordHashBase64 = hashB;
        u.saltBase64 = saltB;
        return mongo.createUser(u);
    }

    public boolean resetTeacherPassword(String email, char[] newPassword) throws Exception {
        if (email == null || email.isBlank() || newPassword == null || newPassword.length == 0) return false;
        User u = mongo.findUserByEmail(email);
        if (u == null) return false;

        byte[] salt = PasswordUtil.generateSalt();
        byte[] hash = PasswordUtil.hashPassword(newPassword, salt);
        String saltB = Base64.getEncoder().encodeToString(salt);
        String hashB = Base64.getEncoder().encodeToString(hash);
        return mongo.updateUserPassword(email, hashB, saltB);
    }

    public boolean loginTeacher(String email, char[] password) throws Exception {
        User u = mongo.findUserByEmail(email);
        if (u == null) return false;
        return PasswordUtil.verifyPassword(password, u.passwordHashBase64, u.saltBase64);
    }
}