package com.baiyv.passwordmanagementapplication1.utils;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class TotpUtils {

    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final long TIME_STEP_MS = 30_000L;

    private TotpUtils() {
    }

    public static String generateSecretBase32(int bytes) {
        if (bytes <= 0) bytes = 20;
        byte[] raw = new byte[bytes];
        new SecureRandom().nextBytes(raw);
        return base32Encode(raw);
    }

    public static String generateCode(String base32Secret, long timeMillis, int digits) {
        if (digits <= 0) digits = 6;
        byte[] key = base32Decode(base32Secret);
        long counter = timeMillis / TIME_STEP_MS;
        return hotp(key, counter, digits);
    }

    public static long verifyCode(String base32Secret, String code, long timeMillis, int window, int digits, long lastCounter) {
        if (code == null) return -1L;
        String normalized = code.replace(" ", "").trim();
        if (normalized.isEmpty()) return -1L;

        long counter = timeMillis / TIME_STEP_MS;
        int w = Math.max(0, window);
        for (long c = counter - w; c <= counter + w; c++) {
            if (c <= lastCounter) continue;
            String expected = hotp(base32Decode(base32Secret), c, digits);
            if (normalized.equals(expected)) {
                return c;
            }
        }
        return -1L;
    }

    private static String hotp(byte[] key, long counter, int digits) {
        try {
            byte[] msg = ByteBuffer.allocate(8).putLong(counter).array();
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(msg);

            int offset = hash[hash.length - 1] & 0x0F;
            int binary =
                    ((hash[offset] & 0x7F) << 24)
                            | ((hash[offset + 1] & 0xFF) << 16)
                            | ((hash[offset + 2] & 0xFF) << 8)
                            | (hash[offset + 3] & 0xFF);

            int mod = 1;
            for (int i = 0; i < digits; i++) mod *= 10;
            int otp = binary % mod;

            String s = Integer.toString(otp);
            if (s.length() >= digits) return s;
            StringBuilder sb = new StringBuilder(digits);
            for (int i = s.length(); i < digits; i++) sb.append('0');
            sb.append(s);
            return sb.toString();
        } catch (GeneralSecurityException e) {
            return "";
        }
    }

    private static byte[] base32Decode(String input) {
        if (input == null) return new byte[0];
        String s = input.replace("=", "").replace(" ", "").trim().toUpperCase();
        if (s.isEmpty()) return new byte[0];

        int buffer = 0;
        int bitsLeft = 0;
        ByteBuffer out = ByteBuffer.allocate((s.length() * 5 + 7) / 8);

        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            int val = BASE32_ALPHABET.indexOf(ch);
            if (val < 0) continue;

            buffer = (buffer << 5) | val;
            bitsLeft += 5;

            if (bitsLeft >= 8) {
                bitsLeft -= 8;
                out.put((byte) ((buffer >> bitsLeft) & 0xFF));
            }
        }

        byte[] res = new byte[out.position()];
        out.flip();
        out.get(res);
        return res;
    }

    private static String base32Encode(byte[] data) {
        if (data == null || data.length == 0) return "";
        StringBuilder sb = new StringBuilder((data.length * 8 + 4) / 5);

        int buffer = data[0] & 0xFF;
        int next = 1;
        int bitsLeft = 8;

        while (bitsLeft > 0 || next < data.length) {
            if (bitsLeft < 5) {
                if (next < data.length) {
                    buffer = (buffer << 8) | (data[next++] & 0xFF);
                    bitsLeft += 8;
                } else {
                    int pad = 5 - bitsLeft;
                    buffer <<= pad;
                    bitsLeft += pad;
                }
            }

            int index = (buffer >> (bitsLeft - 5)) & 0x1F;
            bitsLeft -= 5;
            sb.append(BASE32_ALPHABET.charAt(index));
        }

        return sb.toString();
    }
}