package com.bjuc.datadict.util;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 本地密码加密工具：AES/GCM，密钥由本机信息派生，仅用于“记住密码”的本地存储。
 */
public final class CryptoUtil {
    private static final String SALT = "BJUC-DATADICT::NC65::2026";
    private static final int IV_LEN = 12;
    private static final int TAG_BITS = 128;

    private CryptoUtil() {
    }

    private static SecretKeySpec deriveKey() throws Exception {
        String machine = System.getProperty("user.name")
                + "|" + System.getenv("COMPUTERNAME")
                + "|" + System.getenv("USERDOMAIN")
                + "|" + System.getProperty("os.name");
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] hash = md.digest((SALT + machine).getBytes(StandardCharsets.UTF_8));
        byte[] key = new byte[16];
        System.arraycopy(hash, 0, key, 0, 16);
        return new SecretKeySpec(key, "AES");
    }

    public static String encrypt(String plain) {
        if (plain == null || plain.isEmpty()) {
            return "";
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            byte[] iv = new byte[IV_LEN];
            new SecureRandom().nextBytes(iv);
            cipher.init(Cipher.ENCRYPT_MODE, deriveKey(), new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(iv) + ":" + Base64.getEncoder().encodeToString(ct);
        } catch (Exception e) {
            return "ENC_ERR";
        }
    }

    /** 解密失败返回 null */
    public static String decrypt(String encoded) {
        if (encoded == null || encoded.isEmpty() || encoded.equals("ENC_ERR")) {
            return "";
        }
        try {
            String[] parts = encoded.split(":", 2);
            if (parts.length != 2) {
                return "";
            }
            byte[] iv = Base64.getDecoder().decode(parts[0]);
            byte[] ct = Base64.getDecoder().decode(parts[1]);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(), new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }
}