package org.pente.subscriptions.apple;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.pente.subscriptions.play.AccountIds;

/**
 * The StoreKit appAccountToken of a pente.org player (spec 2026-10-15 §4.3): an RFC 9562 version-8 UUID whose
 * top 48 bits are the pid and whose 74 other free bits are the first 74 bits of
 * HMAC-SHA256(PLAY_ACCOUNT_ID_SECRET, "apple:" + pid). Reversible, so notifications and pollers resolve the
 * pid on their own, and tamper-evident. The "apple:" label keeps these MACs apart from Play's account ids.
 *
 * Layout (most significant bit first): pid (48) | version 0b1000 (4) | mac bits 0-11 (12) |
 * variant 0b10 (2) | mac bits 12-73 (62).
 */
public final class AppleAccountTokens {

    /** pids must fit the 48-bit field (current pids are 45 bits) */
    static final long MAX_PID = (1L << 48) - 1;

    private final byte[] secret;

    private AppleAccountTokens(byte[] secret) {
        this.secret = secret;
    }

    /** @throws IllegalStateException when the secret is null or blank */
    public static AppleAccountTokens fromSecret(String secret) {
        if (secret == null || secret.trim().isEmpty()) {
            throw new IllegalStateException(AccountIds.ENV_VAR + " is not set; App Store purchases cannot be bound to players");
        }
        return new AppleAccountTokens(secret.getBytes(StandardCharsets.UTF_8));
    }

    /** the same PLAY_ACCOUNT_ID_SECRET Play uses; no new secret */
    public static AppleAccountTokens fromEnvironment() {
        return fromSecret(System.getenv(AccountIds.ENV_VAR));
    }

    /** @throws IllegalArgumentException when pid does not fit 48 bits or is not positive */
    public UUID create(long pid) {
        if (pid <= 0 || pid > MAX_PID) {
            throw new IllegalArgumentException("pid out of range for an appAccountToken: " + pid);
        }
        byte[] mac = mac(pid);
        long first12 = ((mac[0] & 0xFFL) << 4) | ((mac[1] & 0xFFL) >>> 4);
        long from12 = (ByteBuffer.wrap(mac, 1, 8).getLong() << 4) | ((mac[9] & 0xFFL) >>> 4);
        long next62 = from12 >>> 2;
        long msb = (pid << 16) | 0x8000L | first12;
        long lsb = 0x8000000000000000L | next62;
        return new UUID(msb, lsb);
    }

    /** the pid, or null when the token is absent, not version 8 / RFC variant, or its MAC does not match */
    public Long parse(UUID token) {
        if (token == null || token.version() != 8 || token.variant() != 2) {
            return null;
        }
        long pid = token.getMostSignificantBits() >>> 16;
        if (pid <= 0) {
            return null;
        }
        UUID expected = create(pid);
        return MessageDigest.isEqual(bytes(expected), bytes(token)) ? Long.valueOf(pid) : null;
    }

    /** parse of a string token; null when it is not a UUID */
    public Long parse(String token) {
        if (token == null) {
            return null;
        }
        try {
            return parse(UUID.fromString(token));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static byte[] bytes(UUID u) {
        return ByteBuffer.allocate(16).putLong(u.getMostSignificantBits()).putLong(u.getLeastSignificantBits()).array();
    }

    private byte[] mac(long pid) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(("apple:" + pid).getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }
}
