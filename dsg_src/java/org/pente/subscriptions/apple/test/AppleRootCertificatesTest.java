package org.pente.subscriptions.apple.test;

import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Map;

import junit.framework.TestCase;

import com.apple.itunes.storekit.model.Environment;
import com.apple.itunes.storekit.verification.SignedDataVerifier;
import com.apple.itunes.storekit.verification.VerificationException;
import com.apple.itunes.storekit.verification.VerificationStatus;

import org.pente.subscriptions.apple.AppleRootCertificates;
import org.pente.subscriptions.play.PlayTokens;

/**
 * The vendored Apple roots and the vendored App Store Server Library jar set (spec 2026-10-15 §4.1):
 * fingerprints pinned, the library links against our jars (okhttp, okio, kotlin, jackson, java-jwt) and
 * a verifier built from the embedded roots rejects garbage without any network call.
 *
 * Run: ant test-apple-billing
 */
public class AppleRootCertificatesTest extends TestCase {

    static final String G3_SHA256 = "63343abfb89a6a03ebb57e9b3f5fa7be7c4f5c756f3017b3a8c488c3653e9179";
    static final String G2_SHA256 = "c2b9b042dd57830e7d117dac55ac8ae19407d38e41d88f3215bc3a890444a050";
    static final String INC_ROOT_SHA256 = "b0b1730ecbc7ff4505142c49f1295e6eda6bcaed7e2c68c5be91b5a11001f024";

    public AppleRootCertificatesTest(String name) {
        super(name);
    }

    private static String sha256(byte[] der) throws Exception {
        return PlayTokens.hex(MessageDigest.getInstance("SHA-256").digest(der));
    }

    public void testFingerprintsArePinned() throws Exception {
        byte[][] der = AppleRootCertificates.der();
        assertEquals(3, der.length);
        assertEquals(G3_SHA256, sha256(der[0]));
        assertEquals(G2_SHA256, sha256(der[1]));
        assertEquals(INC_ROOT_SHA256, sha256(der[2]));
    }

    public void testRootsParseAsAppleCertificates() throws Exception {
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        for (byte[] der : AppleRootCertificates.der()) {
            X509Certificate cert = (X509Certificate) factory.generateCertificate(new ByteArrayInputStream(der));
            String subject = cert.getSubjectX500Principal().getName();
            assertTrue(subject, subject.contains("O=Apple Inc."));
            assertTrue(subject, subject.contains("CN=Apple Root CA"));
        }
    }

    public void testStreamsAreFreshEachCall() {
        assertEquals(3, AppleRootCertificates.streams().size());
        assertTrue(AppleRootCertificates.streams() != AppleRootCertificates.streams());
    }

    public void testLibraryVerifierRejectsGarbageOffline() {
        SignedDataVerifier verifier = new SignedDataVerifier(AppleRootCertificates.streams(),
                "be.submanifold.penteLive", null, Environment.SANDBOX, false);
        try {
            verifier.verifyAndDecodeTransaction("not.a.jws");
            fail("garbage verified");
        } catch (VerificationException expected) {
            assertEquals(VerificationStatus.VERIFICATION_FAILURE, expected.getStatus());
        }
    }

    public void testJacksonLinksForTheExistingGoogleAdapter() throws Exception {
        String json = new com.google.api.client.json.jackson2.JacksonFactory().toString(Map.of("k", "v"));
        assertEquals("{\"k\":\"v\"}", json);
    }
}
