package org.pente.subscriptions.play;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Keys derived from Google Play purchase tokens (spec 2026-10-11 §4.2, §4.5).
 */
public final class PlayTokens {

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private PlayTokens() {
    }

    /** lowercase hex of the given bytes */
    public static String hex(byte[] bytes) {
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            out[2 * i] = HEX[(bytes[i] >> 4) & 0xF];
            out[2 * i + 1] = HEX[bytes[i] & 0xF];
        }
        return new String(out);
    }

    /** dsg_subscribers_android.purchase_token_hash: SHA-256 of the token, 64 lowercase hex chars */
    public static String sha256Hex(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return hex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /**
     * dsg_subscribers.transactionid of a one-time purchase: the Play orderId, or
     * "GP." + the first 61 hex chars of the token hash when Play gives no orderId (promo codes).
     * Both fit transactionid VARCHAR(64).
     */
    public static String orderKey(String orderId, String tokenHash) {
        if (orderId != null && !orderId.isEmpty()) {
            return orderId;
        }
        return "GP." + tokenHash.substring(0, 61);
    }
}
