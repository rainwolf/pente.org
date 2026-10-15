package org.pente.subscriptions.apple.test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.UUID;

import junit.framework.TestCase;

import com.apple.itunes.storekit.client.APIError;
import com.apple.itunes.storekit.client.APIException;
import com.apple.itunes.storekit.model.Environment;
import com.apple.itunes.storekit.model.InAppOwnershipType;
import com.apple.itunes.storekit.model.JWSRenewalInfoDecodedPayload;
import com.apple.itunes.storekit.model.JWSTransactionDecodedPayload;
import com.apple.itunes.storekit.model.ResponseBodyV2DecodedPayload;
import com.apple.itunes.storekit.verification.SignedDataVerifier;
import com.apple.itunes.storekit.verification.VerificationException;
import com.apple.itunes.storekit.verification.VerificationStatus;

import org.pente.subscriptions.apple.AppStoreApi;
import org.pente.subscriptions.apple.AppStoreVerifier;
import org.pente.subscriptions.apple.AppleApiException;
import org.pente.subscriptions.apple.AppleEnvironment;
import org.pente.subscriptions.apple.AppleNotification;
import org.pente.subscriptions.apple.AppleRenewal;
import org.pente.subscriptions.apple.AppleRootCertificates;
import org.pente.subscriptions.apple.AppleTransaction;
import org.pente.subscriptions.apple.AppleVerificationException;

/**
 * The real adapters without network (spec 2026-10-15 §4.1, §4.4): payload mapping, which failures are retryable,
 * and that the production wiring builds from the embedded roots and a .p8 key.
 *
 * Run: ant test-apple-billing
 */
public class AppStoreMappingTest extends TestCase {

    public AppStoreMappingTest(String name) {
        super(name);
    }

    public void testTransactionMapping() {
        UUID token = UUID.randomUUID();
        JWSTransactionDecodedPayload p = new JWSTransactionDecodedPayload()
                .transactionId("2000000100000002").originalTransactionId("2000000100000001")
                .productId("1YRNOADSORLIMITS").environment(Environment.SANDBOX)
                .purchaseDate(Long.valueOf(1000L)).expiresDate(Long.valueOf(2000L)).revocationDate(Long.valueOf(1500L))
                .appAccountToken(token).inAppOwnershipType(InAppOwnershipType.PURCHASED);
        AppleTransaction t = AppStoreVerifier.toTransaction(p);
        assertEquals("2000000100000002", t.transactionId);
        assertEquals("2000000100000001", t.originalTransactionId);
        assertEquals("1YRNOADSORLIMITS", t.productId);
        assertEquals(AppleEnvironment.SANDBOX, t.environment);
        assertEquals(Long.valueOf(1000L), t.purchaseDateMs);
        assertEquals(Long.valueOf(2000L), t.expiresDateMs);
        assertEquals(Long.valueOf(1500L), t.revocationDateMs);
        assertEquals(token, t.appAccountToken);
        assertTrue(!t.familyShared);
        assertTrue(AppStoreVerifier.toTransaction(p.inAppOwnershipType(InAppOwnershipType.FAMILY_SHARED)).familyShared);
        assertEquals(AppleEnvironment.PRODUCTION,
                AppStoreVerifier.toTransaction(new JWSTransactionDecodedPayload().environment(Environment.PRODUCTION)).environment);
    }

    public void testRenewalMapping() {
        AppleRenewal r = AppStoreVerifier.toRenewal(new JWSRenewalInfoDecodedPayload()
                .originalTransactionId("2000000100000001").gracePeriodExpiresDate(Long.valueOf(3000L)));
        assertEquals("2000000100000001", r.originalTransactionId);
        assertEquals(Long.valueOf(3000L), r.gracePeriodExpiresDateMs);
        assertNull(r.appAccountToken);
    }

    public void testApiFailuresThatAreRetryable() {
        assertTrue(map(new IOException("connection reset")).isRetryable());
        assertTrue(map(new APIException(500)).isRetryable());
        assertTrue(map(new APIException(503)).isRetryable());
        assertTrue(map(new APIException(429, APIError.RATE_LIMIT_EXCEEDED, "slow down")).isRetryable());
        assertTrue(map(new APIException(404, APIError.ORIGINAL_TRANSACTION_ID_NOT_FOUND_RETRYABLE, "later")).isRetryable());
        assertTrue(map(new APIException(404, APIError.ACCOUNT_NOT_FOUND_RETRYABLE, "later")).isRetryable());
        assertTrue(map(new AppleVerificationException("RETRYABLE_VERIFICATION_FAILURE", true)).isRetryable());
    }

    public void testApiFailuresThatAreFinal() {
        assertTrue(!map(new APIException(404, APIError.TRANSACTION_ID_NOT_FOUND, "no such transaction")).isRetryable());
        assertTrue(!map(new APIException(400, APIError.INVALID_TRANSACTION_ID, "bad id")).isRetryable());
        assertTrue(!map(new APIException(401)).isRetryable());
        assertTrue(!map(new AppleVerificationException("INVALID_CHAIN", false)).isRetryable());
    }

    public void testUnexpectedExceptionsAreFinalAndKeepTheCause() {
        RuntimeException bug = new IllegalStateException("bad input");
        AppleApiException mapped = map(bug);
        assertTrue(!mapped.isRetryable());
        assertTrue(mapped.getMessage(), mapped.getMessage().contains("bad input"));
        assertSame(bug, mapped.getCause());
        assertTrue(map(new IOException("connection reset")).isRetryable());
    }

    private static AppleApiException map(Exception e) {
        return AppStoreApi.map("test", e);
    }

    public void testVerificationStatusMapping() {
        assertTrue(verification(VerificationStatus.RETRYABLE_VERIFICATION_FAILURE).isRetryable());
        VerificationStatus[] finalStatuses = {VerificationStatus.VERIFICATION_FAILURE, VerificationStatus.INVALID_APP_IDENTIFIER,
                VerificationStatus.INVALID_ENVIRONMENT, VerificationStatus.INVALID_CERTIFICATE,
                VerificationStatus.INVALID_CHAIN_LENGTH, VerificationStatus.INVALID_CHAIN};
        for (VerificationStatus s : finalStatuses) {
            assertTrue(s.name(), !verification(s).isRetryable());
            assertEquals(s.name(), verification(s).getMessage());
        }
    }

    private static AppleVerificationException verification(VerificationStatus status) {
        return AppStoreVerifier.map(new VerificationException(status));
    }

    public void testRealVerifierRefusesGarbageOffline() {
        AppStoreVerifier verifier = AppStoreVerifier.create("be.submanifold.penteLive", Long.valueOf(595426592L), false);
        try {
            verifier.verifyTransaction("not.a.jws");
            fail("garbage verified");
        } catch (AppleVerificationException expected) {
            assertTrue(!expected.isRetryable());
        }
        try {
            verifier.verifyNotification("garbage");
            fail("garbage verified");
        } catch (AppleVerificationException expected) {
            assertTrue(!expected.isRetryable());
        }
    }

    /** a SignedDataVerifier for env whose notification check fails with failure, or (null) answers a payload */
    private static SignedDataVerifier stub(Environment env, final VerificationStatus failure) {
        return new SignedDataVerifier(AppleRootCertificates.streams(), "be.submanifold.penteLive",
                Long.valueOf(595426592L), env, false) {
            public ResponseBodyV2DecodedPayload verifyAndDecodeNotification(String signedPayload)
                    throws VerificationException {
                if (failure != null) {
                    throw new VerificationException(failure);
                }
                return new ResponseBodyV2DecodedPayload().notificationUUID("u-sandbox");
            }
        };
    }

    public void testSandboxNotificationWithoutAppAppleIdFallsBackToSandbox() throws Exception {
        // library 5.2.0 checks appAppleId before the environment, and sandbox notifications carry no appAppleId
        AppStoreVerifier verifier = new AppStoreVerifier(
                stub(Environment.PRODUCTION, VerificationStatus.INVALID_APP_IDENTIFIER), stub(Environment.SANDBOX, null));
        AppleNotification n = verifier.verifyNotification("payload");
        assertEquals("u-sandbox", n.notificationUuid);
        assertEquals(AppleEnvironment.SANDBOX, n.environment);
        AppStoreVerifier refusing = new AppStoreVerifier(
                stub(Environment.PRODUCTION, VerificationStatus.INVALID_CHAIN), stub(Environment.SANDBOX, null));
        try {
            refusing.verifyNotification("payload");
            fail("a broken chain fell back to sandbox");
        } catch (AppleVerificationException expected) {
            assertEquals("INVALID_CHAIN", expected.getMessage());
        }
    }

    public void testVerifierNeedsBundleAndAppId() {
        try {
            AppStoreVerifier.create("be.submanifold.penteLive", null, true);
            fail("missing appAppleId accepted");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("appleAppAppleId"));
        }
    }

    public void testApiBuildsFromAP8KeyWithoutNetwork() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        String pem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                .encodeToString(generator.generateKeyPair().getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
        File key = File.createTempFile("apple-iap-key", ".p8");
        try {
            Files.write(key.toPath(), pem.getBytes(StandardCharsets.US_ASCII));
            AppStoreVerifier verifier = AppStoreVerifier.create("be.submanifold.penteLive", Long.valueOf(595426592L), true);
            assertNotNull(AppStoreApi.fromKeyFile(key.getPath(), "ABC123DEFG", "57246542-96fe-1a63-e053-0824d011072a",
                    "be.submanifold.penteLive", verifier));
        } finally {
            key.delete();
        }
    }

    public void testApiNeedsItsKeyAndIds() throws Exception {
        AppStoreVerifier verifier = AppStoreVerifier.create("be.submanifold.penteLive", Long.valueOf(595426592L), true);
        try {
            AppStoreApi.fromKeyFile("/nonexistent/apple-iap-key.p8", "ABC123DEFG", "issuer", "be.submanifold.penteLive", verifier);
            fail("missing key file accepted");
        } catch (IOException expected) {
            // AppleBillingSetup falls back to UnconfiguredAppleApi
        }
        try {
            AppStoreApi.fromKeyFile("/etc/dsg/apple-iap-key.p8", " ", "issuer", "be.submanifold.penteLive", verifier);
            fail("blank key id accepted");
        } catch (IllegalArgumentException expected) {
            // AppleBillingSetup falls back to UnconfiguredAppleApi
        }
    }
}
