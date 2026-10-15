package org.pente.subscriptions.apple.test;

import java.util.UUID;

import junit.framework.TestCase;

import org.pente.subscriptions.apple.AppleAccountTokens;

/**
 * appAccountToken build / parse / tamper rejection (spec 2026-10-15 §4.3, §5).
 *
 * Run: ant test-apple-billing
 */
public class AppleAccountTokensTest extends TestCase {

    private static final long PID = 23000000016237L;

    public AppleAccountTokensTest(String name) {
        super(name);
    }

    private static UUID flip(UUID u, boolean msb, int bit) {
        long m = u.getMostSignificantBits();
        long l = u.getLeastSignificantBits();
        if (msb) {
            m ^= 1L << bit;
        } else {
            l ^= 1L << bit;
        }
        return new UUID(m, l);
    }

    public void testRoundTrip() {
        AppleAccountTokens tokens = AppleAccountTokens.fromSecret("s3cret");
        UUID token = tokens.create(PID);
        assertEquals(8, token.version());
        assertEquals(2, token.variant());
        assertEquals(PID, token.getMostSignificantBits() >>> 16);
        assertEquals(Long.valueOf(PID), tokens.parse(token));
        assertEquals(Long.valueOf(PID), tokens.parse(token.toString()));
        assertEquals(Long.valueOf(PID), tokens.parse(token.toString().toUpperCase()));
    }

    public void testDeterministicPerSecretAndApartFromOtherSecrets() {
        assertEquals(AppleAccountTokens.fromSecret("a").create(PID), AppleAccountTokens.fromSecret("a").create(PID));
        assertTrue(!AppleAccountTokens.fromSecret("a").create(PID).equals(AppleAccountTokens.fromSecret("b").create(PID)));
        assertNull(AppleAccountTokens.fromSecret("s3cret").parse(AppleAccountTokens.fromSecret("other").create(PID)));
    }

    public void testTokenForAnotherPidParsesToThatPid() {
        AppleAccountTokens tokens = AppleAccountTokens.fromSecret("s3cret");
        assertEquals(Long.valueOf(PID + 1), tokens.parse(tokens.create(PID + 1)));
    }

    public void testPidSwappedIntoAnotherTokenRejected() {
        AppleAccountTokens tokens = AppleAccountTokens.fromSecret("s3cret");
        UUID token = tokens.create(PID);
        UUID moved = new UUID(((PID + 1) << 16) | (token.getMostSignificantBits() & 0xFFFFL), token.getLeastSignificantBits());
        assertNull(tokens.parse(moved));
    }

    public void testEveryFlippedMacBitRejected() {
        AppleAccountTokens tokens = AppleAccountTokens.fromSecret("s3cret");
        UUID token = tokens.create(PID);
        for (int bit = 0; bit < 12; bit++) {
            assertNull("msb bit " + bit, tokens.parse(flip(token, true, bit)));
        }
        for (int bit = 0; bit < 62; bit++) {
            assertNull("lsb bit " + bit, tokens.parse(flip(token, false, bit)));
        }
    }

    public void testWrongVersionRejected() {
        AppleAccountTokens tokens = AppleAccountTokens.fromSecret("s3cret");
        UUID token = tokens.create(PID);
        UUID v4 = new UUID((token.getMostSignificantBits() & ~0xF000L) | 0x4000L, token.getLeastSignificantBits());
        assertEquals(4, v4.version());
        assertNull(tokens.parse(v4));
        assertNull(tokens.parse(UUID.randomUUID()));
    }

    public void testWrongVariantRejected() {
        AppleAccountTokens tokens = AppleAccountTokens.fromSecret("s3cret");
        UUID token = tokens.create(PID);
        UUID microsoft = new UUID(token.getMostSignificantBits(),
                (token.getLeastSignificantBits() & 0x3FFFFFFFFFFFFFFFL) | 0xC000000000000000L);
        assertEquals(6, microsoft.variant());
        assertNull(tokens.parse(microsoft));
    }

    public void testMalformedRejected() {
        AppleAccountTokens tokens = AppleAccountTokens.fromSecret("s3cret");
        assertNull(tokens.parse((UUID) null));
        assertNull(tokens.parse((String) null));
        assertNull(tokens.parse(""));
        assertNull(tokens.parse("not-a-uuid"));
        assertNull(tokens.parse(new UUID(0x8000L, 0x8000000000000000L)));
    }

    public void testPidRange() {
        AppleAccountTokens tokens = AppleAccountTokens.fromSecret("s3cret");
        long max = (1L << 48) - 1;
        assertEquals(Long.valueOf(max), tokens.parse(tokens.create(max)));
        try {
            tokens.create(max + 1);
            fail("49-bit pid accepted");
        } catch (IllegalArgumentException expected) {
            // a pid that does not fit is never silently truncated
        }
        try {
            tokens.create(0);
            fail("pid 0 accepted");
        } catch (IllegalArgumentException expected) {
            // pid 0 is the tombstone owner, never a player
        }
    }

    public void testBlankSecretFailsFast() {
        String[] blanks = {null, "", "   "};
        for (String blank : blanks) {
            try {
                AppleAccountTokens.fromSecret(blank);
                fail("blank secret accepted: [" + blank + "]");
            } catch (IllegalStateException expected) {
                assertTrue(expected.getMessage().contains("PLAY_ACCOUNT_ID_SECRET"));
            }
        }
    }
}
