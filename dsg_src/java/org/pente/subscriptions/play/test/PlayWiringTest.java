package org.pente.subscriptions.play.test;

import junit.framework.TestCase;

import org.pente.subscriptions.play.GoogleJwtVerifier;
import org.pente.subscriptions.play.PlayApiException;
import org.pente.subscriptions.play.RtdnAuth;
import org.pente.subscriptions.play.UnconfiguredPlayApi;

/**
 * Production wiring that needs no network: the real JWT verifier rejects a non-JWT before any key
 * fetch, and a missing service-account key turns every Play call into a retryable failure.
 *
 * Run: ant test-play-billing
 */
public class PlayWiringTest extends TestCase {

    private static final String AUDIENCE = "https://www.pente.org/gameServer/googlePlayRTDN";

    public PlayWiringTest(String name) {
        super(name);
    }

    public void testGoogleJwtVerifierRejectsGarbage() {
        GoogleJwtVerifier verifier = new GoogleJwtVerifier(AUDIENCE);
        try {
            verifier.verify("not.a.jwt");
            fail("garbage token verified");
        } catch (Exception expected) {
            // VerificationException or IllegalArgumentException, both mean 401
        }
        assertTrue(!RtdnAuth.authorized("Bearer not.a.jwt", verifier, AUDIENCE,
                "play-rtdn-push@pente-live.iam.gserviceaccount.com"));
    }

    public void testUnconfiguredApiFailsTransiently() {
        UnconfiguredPlayApi api = new UnconfiguredPlayApi("no key");
        try {
            api.getSubscription("t");
            fail("expected PlayApiException");
        } catch (PlayApiException expected) {
            assertTrue(expected.getMessage().contains("no key"));
        }
        try {
            api.listVoided(0L, null);
            fail("expected PlayApiException");
        } catch (PlayApiException expected) {
            // pollers log and retry next run
        }
    }
}
