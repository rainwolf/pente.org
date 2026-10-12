package org.pente.subscriptions.play.test;

import junit.framework.TestCase;

import org.pente.subscriptions.play.AccountIds;
import org.pente.subscriptions.play.PlayTokens;

/**
 * accountId create / parse / tamper rejection and token keys (spec 2026-10-11 §4.3, §5).
 *
 * Run: ant test-play-billing
 */
public class AccountIdsTest extends TestCase {

    private static final long PID = 23000000016237L;

    public AccountIdsTest(String name) {
        super(name);
    }

    public void testRoundTrip() {
        AccountIds ids = AccountIds.fromSecret("s3cret");
        String id = ids.create(PID);
        assertTrue(id, id.startsWith(PID + "."));
        assertEquals(String.valueOf(PID).length() + 1 + 40, id.length());
        assertTrue("at most 64 chars for Play", id.length() <= 64);
        assertEquals(Long.valueOf(PID), ids.parse(id));
    }

    public void testLongestPidFitsPlayLimit() {
        String id = AccountIds.fromSecret("s3cret").create(Long.MAX_VALUE);
        assertTrue(id, id.length() <= 64);
    }

    public void testDeterministicPerSecret() {
        assertEquals(AccountIds.fromSecret("a").create(PID), AccountIds.fromSecret("a").create(PID));
        assertTrue(!AccountIds.fromSecret("a").create(PID).equals(AccountIds.fromSecret("b").create(PID)));
    }

    public void testTamperedMacRejected() {
        AccountIds ids = AccountIds.fromSecret("s3cret");
        String id = ids.create(PID);
        char last = id.charAt(id.length() - 1);
        String tampered = id.substring(0, id.length() - 1) + (last == '0' ? '1' : '0');
        assertNull(ids.parse(tampered));
    }

    public void testOtherPidWithCopiedMacRejected() {
        AccountIds ids = AccountIds.fromSecret("s3cret");
        String id = ids.create(PID);
        String moved = (PID + 1) + id.substring(id.indexOf('.'));
        assertNull(ids.parse(moved));
    }

    public void testIdFromOtherSecretRejected() {
        String id = AccountIds.fromSecret("other").create(PID);
        assertNull(AccountIds.fromSecret("s3cret").parse(id));
    }

    public void testMalformedRejected() {
        AccountIds ids = AccountIds.fromSecret("s3cret");
        String mac = ids.create(PID).substring(String.valueOf(PID).length() + 1);
        assertNull(ids.parse(null));
        assertNull(ids.parse(""));
        assertNull(ids.parse("abc"));
        assertNull(ids.parse("." + mac));
        assertNull(ids.parse("x." + mac));
        assertNull(ids.parse("0" + PID + "." + mac));
        assertNull(ids.parse("-5." + mac));
        assertNull(ids.parse(PID + "." + mac + "."));
        assertNull(ids.parse(PID + "." + mac.substring(1)));
    }

    public void testBlankSecretFailsFast() {
        String[] blanks = {null, "", "   "};
        for (String blank : blanks) {
            try {
                AccountIds.fromSecret(blank);
                fail("blank secret accepted: [" + blank + "]");
            } catch (IllegalStateException expected) {
                assertTrue(expected.getMessage().contains("PLAY_ACCOUNT_ID_SECRET"));
            }
        }
    }

    public void testSha256Hex() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", PlayTokens.sha256Hex("abc"));
    }

    public void testOrderKeyPrefersOrderId() {
        String hash = PlayTokens.sha256Hex("token");
        assertEquals("GPA.1234-5678-9012-34567", PlayTokens.orderKey("GPA.1234-5678-9012-34567", hash));
    }

    public void testOrderKeyFallbackFitsTransactionId() {
        String hash = PlayTokens.sha256Hex("token");
        String key = PlayTokens.orderKey(null, hash);
        assertEquals("GP." + hash.substring(0, 61), key);
        assertEquals(64, key.length());
        assertEquals(key, PlayTokens.orderKey("", hash));
    }
}
