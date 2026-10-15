package org.pente.subscriptions.apple.test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import junit.framework.TestCase;

import org.pente.subscriptions.apple.AppStoreVerifier;
import org.pente.subscriptions.apple.AppleEnvironment;
import org.pente.subscriptions.apple.AppleProducts;
import org.pente.subscriptions.apple.AppleTransaction;

/**
 * A real sandbox jwsRepresentation, captured in sub-project 2's end-to-end run, checked by the real
 * SignedDataVerifier: SANDBOX, the embedded roots, online checks off (spec 2026-10-15 §5). Until the capture is
 * committed as test/fixtures/sandbox-transaction.jws (a text file with no secrets), the test is skipped with a
 * visible message.
 *
 * Run: ant test-apple-billing
 */
public class AppleRealVerifierTest extends TestCase {

    public AppleRealVerifierTest(String name) {
        super(name);
    }

    public void testCapturedSandboxTransactionVerifies() throws Exception {
        File fixture = new File(System.getProperty("appleFixtures", "fixtures"), "sandbox-transaction.jws");
        if (!fixture.isFile()) {
            System.out.println("AppleRealVerifierTest: SKIPPED, no " + fixture.getPath()
                    + " yet (captured in sub-project 2's end-to-end run)");
            return;
        }
        String jws = new String(Files.readAllBytes(fixture.toPath()), StandardCharsets.US_ASCII).trim();
        AppStoreVerifier verifier = AppStoreVerifier.create("be.submanifold.penteLive", Long.valueOf(595426592L), false);

        AppleTransaction t = verifier.verifyTransaction(jws);

        assertEquals(AppleEnvironment.SANDBOX, t.environment);
        assertTrue(t.productId, AppleProducts.isKnown(t.productId));
        assertNotNull(t.transactionId);
        assertNotNull(t.originalTransactionId);
    }
}
