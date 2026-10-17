package com.baiyv.passwordmanagementapplication1.utils;

import java.util.Base64;


import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class CryptoUtils {

    private static final String TAG = "CryptoUtils";
    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_IV_LENGTH = 12;

    /**
     * 对主密码进行哈希（用于验证）
     * 修复：使用 Base64.NO_WRAP 避免换行符问题
     */
    public static String hashPassword(String password) {
        if (password == null || password.isEmpty()) {

            return null;
        }

        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes(StandardCharsets.UTF_8));
            // 关键修复：使用 NO_WRAP 避免换行符
            String result = Base64.getEncoder().encodeToString(hash);

            return result;
        } catch (Exception e) {


            return null;
        }
    }

    /**
     * 使用主密码派生密钥加密
     */
    public static String encrypt(String plainText, String masterPassword) {
        if (plainText == null || masterPassword == null) {
            return null;
        }

        try {
            SecretKeySpec secretKey = generateKeyFromPassword(masterPassword);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);

            byte[] iv = cipher.getIV();
            byte[] encryptedBytes = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            // 将IV和密文组合
            byte[] combined = new byte[iv.length + encryptedBytes.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encryptedBytes, 0, combined, iv.length, encryptedBytes.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {


            return null;
        }
    }

    /**
     * 使用主密码派生密钥解密
     */
    public static String decrypt(String encryptedText, String masterPassword) {
        return decryptInternal(encryptedText, masterPassword, false);
    }

    /** v1/v2 records always derived SHA-256, even if a raw password starts with dek1:. */
    public static String decryptLegacy(String encryptedText, String rawPassword) {
        return decryptInternal(encryptedText, rawPassword, true);
    }

    private static String decryptInternal(String encryptedText, String masterPassword, boolean legacy) {
        if (encryptedText == null || masterPassword == null) {
            return null;
        }

        try {
            SecretKeySpec secretKey = generateKeyFromPassword(masterPassword, legacy);

            byte[] combined = Base64.getDecoder().decode(encryptedText);

            // 分离IV和密文
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] encryptedBytes = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(combined, GCM_IV_LENGTH, encryptedBytes, 0, encryptedBytes.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);

            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {


            return null;
        }
    }

    /**
     * 从主密码生成AES密钥
     */
    private static SecretKeySpec generateKeyFromPassword(String password) {
        return generateKeyFromPassword(password, false);
    }
    private static SecretKeySpec generateKeyFromPassword(String password, boolean legacy) {
        try {
            // 使用SHA-256生成32字节密钥（AES-256）
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] key = !legacy && password.startsWith("dek1:") ? Base64.getDecoder().decode(password.substring(5)) : md.digest(password.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(key, ALGORITHM);
        } catch (Exception e) {


            return null;
        }
    }
}