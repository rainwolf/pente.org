package org.pente.subscriptions.play;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Binds a Play purchase to a pente.org player (spec 2026-10-11 §4.3).
 * accountId = "&lt;pid&gt;.&lt;first 40 hex chars of HMAC-SHA256(secret, pid)&gt;": reversible, so RTDN and
 * the pollers can resolve the pid on their own, and tamper-evident thanks to the HMAC.
 * The secret must never be rotated: existing purchases carry ids derived from it.
 */
public final class AccountIds {

    public static final String ENV_VAR = "PLAY_ACCOUNT_ID_SECRET";

    static final int MAC_HEX_CHARS = 40;

    private final byte[] secret;

    private AccountIds(byte[] secret) {
        this.secret = secret;
    }

    /** @throws IllegalStateException when the secret is null or blank (fail fast at startup) */
    public static AccountIds fromSecret(String secret) {
        if (secret == null || secret.trim().isEmpty()) {
            throw new IllegalStateException(ENV_VAR + " is not set; Google Play purchases cannot be bound to players");
        }
        return new AccountIds(secret.getBytes(StandardCharsets.UTF_8));
    }

    /** reads PLAY_ACCOUNT_ID_SECRET; Tomcat does not substitute ${...} in web.xml, so no context-param */
    public static AccountIds fromEnvironment() {
        return fromSecret(System.getenv(ENV_VAR));
    }

    public String create(long pid) {
        return pid + "." + mac(pid);
    }

    /** @return the pid, or null when the id is absent, malformed or its HMAC does not match */
    public Long parse(String accountId) {
        if (accountId == null) {
            return null;
        }
        int dot = accountId.indexOf('.');
        if (dot <= 0 || dot != accountId.lastIndexOf('.')) {
            return null;
        }
        String pidPart = accountId.substring(0, dot);
        String macPart = accountId.substring(dot + 1);
        if (macPart.length() != MAC_HEX_CHARS) {
            return null;
        }
        long pid;
        try {
            pid = Long.parseLong(pidPart);
        } catch (NumberFormatException e) {
            return null;
        }
        if (pid <= 0 || !Long.toString(pid).equals(pidPart)) {
            return null;
        }
        byte[] expected = mac(pid).getBytes(StandardCharsets.US_ASCII);
        byte[] actual = macPart.getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, actual) ? Long.valueOf(pid) : null;
    }

    private String mac(long pid) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] digest = mac.doFinal(Long.toString(pid).getBytes(StandardCharsets.UTF_8));
            return PlayTokens.hex(digest).substring(0, MAC_HEX_CHARS);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }
}
