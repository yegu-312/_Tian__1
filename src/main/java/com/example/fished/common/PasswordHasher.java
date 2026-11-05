package com.example.fished.common;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 基于 JDK 自带 PBKDF2 的密码散列工具，避免额外引入安全依赖。
 * 数据库只存 salt 与 hash，登录时用常量时间比较。
 */
public final class PasswordHasher {

    private static final int ITERATIONS = 120_000;
    private static final int KEY_LENGTH_BITS = 256;
    private static final int SALT_BYTES = 16;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordHasher() {
    }

    public static String newSalt() {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    public static String hash(String rawPassword, String saltBase64) {
        if (rawPassword == null || saltBase64 == null) {
            throw new IllegalArgumentException("密码与盐不能为空");
        }
        try {
            PBEKeySpec spec = new PBEKeySpec(
                    rawPassword.toCharArray(),
                    Base64.getDecoder().decode(saltBase64),
                    ITERATIONS,
                    KEY_LENGTH_BITS);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return Base64.getEncoder().encodeToString(factory.generateSecret(spec).getEncoded());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("密码散列不可用", e);
        }
    }

    public static boolean matches(String rawPassword, String saltBase64, String expectedHash) {
        if (rawPassword == null || saltBase64 == null || expectedHash == null) {
            return false;
        }
        try {
            byte[] actual = Base64.getDecoder().decode(hash(rawPassword, saltBase64));
            byte[] expected = Base64.getDecoder().decode(expectedHash);
            return MessageDigest.isEqual(actual, expected);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
