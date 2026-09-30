package com.lunacoreos.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.spec.KeySpec;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public class CryptoService {

    private static final String SALT = "lunacoreos-passwords-v1";
    private static final int PBKDF2_ITERATIONS = 200000;
    private static final int KEY_LENGTH = 256;
    private static final String CANARY_STORAGE_KEY = "lc_pwd_canary";
    private static final String CANARY_PLAINTEXT = "lunacoreos-canary-v1";
    
    // In-memory key store (cleared on app close/logout)
    private static SecretKey sessionKey = null;

    public static SecretKey deriveKeyFromMaster(String masterPassword) throws Exception {
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec spec = new PBEKeySpec(masterPassword.toCharArray(), SALT.getBytes(StandardCharsets.UTF_8), PBKDF2_ITERATIONS, KEY_LENGTH);
        SecretKey tmp = factory.generateSecret(spec);
        sessionKey = new SecretKeySpec(tmp.getEncoded(), "AES");
        return sessionKey;
    }

    public static boolean hasSessionKey() {
        return sessionKey != null;
    }

    public static void clearSessionKey() {
        sessionKey = null;
    }

    public static EncryptedData encryptPassword(String plaintext) throws Exception {
        if (sessionKey == null) throw new IllegalStateException("No session key loaded.");

        byte[] iv = new byte[12]; // 96-bit IV
        new SecureRandom().nextBytes(iv);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(128, iv);
        cipher.init(Cipher.ENCRYPT_MODE, sessionKey, spec);

        byte[] cipherText = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

        return new EncryptedData(
                Base64.encodeToString(cipherText, Base64.NO_WRAP),
                Base64.encodeToString(iv, Base64.NO_WRAP)
        );
    }

    public static String decryptPassword(String encPasswordB64, String encIvB64) throws Exception {
        if (sessionKey == null) throw new IllegalStateException("No session key loaded.");

        byte[] cipherText = Base64.decode(encPasswordB64, Base64.NO_WRAP);
        byte[] iv = Base64.decode(encIvB64, Base64.NO_WRAP);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(128, iv);
        cipher.init(Cipher.DECRYPT_MODE, sessionKey, spec);

        byte[] plainText = cipher.doFinal(cipherText);
        return new String(plainText, StandardCharsets.UTF_8);
    }

    public static void saveCanary(Context context) throws Exception {
        EncryptedData data = encryptPassword(CANARY_PLAINTEXT);
        SharedPreferences prefs = context.getSharedPreferences("LunaCoreCrypto", Context.MODE_PRIVATE);
        JSONObject json = new JSONObject();
        json.put("enc_password", data.encPassword);
        json.put("enc_iv", data.encIv);
        prefs.edit().putString(CANARY_STORAGE_KEY, json.toString()).apply();
    }

    public static boolean hasCanary(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("LunaCoreCrypto", Context.MODE_PRIVATE);
        return prefs.contains(CANARY_STORAGE_KEY);
    }

    public static void verifyCanary(Context context) throws Exception {
        SharedPreferences prefs = context.getSharedPreferences("LunaCoreCrypto", Context.MODE_PRIVATE);
        String raw = prefs.getString(CANARY_STORAGE_KEY, null);
        if (raw == null) throw new Exception("No canary found.");

        JSONObject json = new JSONObject(raw);
        String plain = decryptPassword(json.getString("enc_password"), json.getString("enc_iv"));
        
        if (!CANARY_PLAINTEXT.equals(plain)) {
            throw new Exception("Canary mismatch — wrong master key.");
        }
    }

    public static String scorePasswordStrength(String password) {
        if (password == null || password.isEmpty()) return "weak";
        int score = 0;
        if (password.length() >= 8) score++;
        if (password.length() >= 14) score++;
        if (password.matches(".*[A-Z].*")) score++;
        if (password.matches(".*[a-z].*")) score++;
        if (password.matches(".*[0-9].*")) score++;
        if (password.matches(".*[^A-Za-z0-9].*")) score++;

        if (score <= 2) return "weak";
        if (score <= 4) return "fair";
        return "strong";
    }

    public static class EncryptedData {
        public final String encPassword;
        public final String encIv;
        public EncryptedData(String encPassword, String encIv) {
            this.encPassword = encPassword;
            this.encIv = encIv;
        }
    }
}
