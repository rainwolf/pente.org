package org.pente.subscriptions.apple;

import com.apple.itunes.storekit.model.Data;
import com.apple.itunes.storekit.model.Environment;
import com.apple.itunes.storekit.model.InAppOwnershipType;
import com.apple.itunes.storekit.model.JWSRenewalInfoDecodedPayload;
import com.apple.itunes.storekit.model.JWSTransactionDecodedPayload;
import com.apple.itunes.storekit.model.ResponseBodyV2DecodedPayload;
import com.apple.itunes.storekit.verification.SignedDataVerifier;
import com.apple.itunes.storekit.verification.VerificationException;
import com.apple.itunes.storekit.verification.VerificationStatus;

/**
 * AppleVerifier over Apple's SignedDataVerifier (spec 2026-10-15 §2 D4, §4.1): one verifier per environment, built
 * from the embedded Apple roots, production first and sandbox on INVALID_ENVIRONMENT (and, for notifications, on
 * INVALID_APP_IDENTIFIER: the library checks appAppleId before the environment, and sandbox notifications carry none).
 * RETRYABLE_VERIFICATION_FAILURE (OCSP unreachable) is the only retryable status.
 */
public class AppStoreVerifier implements AppleVerifier {

    private final SignedDataVerifier production;
    private final SignedDataVerifier sandbox;

    public AppStoreVerifier(SignedDataVerifier production, SignedDataVerifier sandbox) {
        this.production = production;
        this.sandbox = sandbox;
    }

    /**
     * @param appAppleId   the app's numeric Apple ID, required by the production verifier
     * @param onlineChecks OCSP and current-time expiry checks; true in production, false for recorded fixtures
     */
    public static AppStoreVerifier create(String bundleId, Long appAppleId, boolean onlineChecks) {
        if (bundleId == null || bundleId.trim().isEmpty() || appAppleId == null) {
            throw new IllegalArgumentException("appleBundleId and appleAppAppleId must be set");
        }
        return new AppStoreVerifier(
                new SignedDataVerifier(AppleRootCertificates.streams(), bundleId.trim(), appAppleId, Environment.PRODUCTION, onlineChecks),
                new SignedDataVerifier(AppleRootCertificates.streams(), bundleId.trim(), appAppleId, Environment.SANDBOX, onlineChecks));
    }

    private SignedDataVerifier forEnvironment(AppleEnvironment env) {
        return env == AppleEnvironment.SANDBOX ? sandbox : production;
    }

    /** RETRYABLE_VERIFICATION_FAILURE is retryable, every other status is final; public for AppStoreMappingTest */
    public static AppleVerificationException map(VerificationException e) {
        VerificationStatus status = e.getStatus();
        return new AppleVerificationException(String.valueOf(status), status == VerificationStatus.RETRYABLE_VERIFICATION_FAILURE, e);
    }

    static AppleVerificationException malformed(RuntimeException e) {
        return new AppleVerificationException("malformed signed data: " + e, false, e);
    }

    static AppleEnvironment environment(Environment e) {
        return e == Environment.SANDBOX ? AppleEnvironment.SANDBOX : AppleEnvironment.PRODUCTION;
    }

    /** the fields the service uses, from a verified payload */
    public static AppleTransaction toTransaction(JWSTransactionDecodedPayload p) {
        AppleTransaction t = new AppleTransaction();
        t.transactionId = p.getTransactionId();
        t.originalTransactionId = p.getOriginalTransactionId();
        t.productId = p.getProductId();
        t.environment = environment(p.getEnvironment());
        t.expiresDateMs = p.getExpiresDate();
        t.purchaseDateMs = p.getPurchaseDate();
        t.revocationDateMs = p.getRevocationDate();
        t.appAccountToken = p.getAppAccountToken();
        t.familyShared = p.getInAppOwnershipType() == InAppOwnershipType.FAMILY_SHARED;
        return t;
    }

    public static AppleRenewal toRenewal(JWSRenewalInfoDecodedPayload p) {
        AppleRenewal r = new AppleRenewal();
        r.originalTransactionId = p.getOriginalTransactionId();
        r.gracePeriodExpiresDateMs = p.getGracePeriodExpiresDate();
        r.appAccountToken = p.getAppAccountToken();
        return r;
    }

    public AppleTransaction verifyTransaction(String jws) throws AppleVerificationException {
        try {
            return toTransaction(production.verifyAndDecodeTransaction(jws));
        } catch (VerificationException e) {
            if (e.getStatus() != VerificationStatus.INVALID_ENVIRONMENT) {
                throw map(e);
            }
        } catch (RuntimeException e) {
            throw malformed(e);
        }
        return verifyTransaction(jws, AppleEnvironment.SANDBOX);
    }

    /** strict: env's verifier only, for data the API returned from that environment */
    public AppleTransaction verifyTransaction(String jws, AppleEnvironment env) throws AppleVerificationException {
        try {
            return toTransaction(forEnvironment(env).verifyAndDecodeTransaction(jws));
        } catch (VerificationException e) {
            throw map(e);
        } catch (RuntimeException e) {
            throw malformed(e);
        }
    }

    public AppleRenewal verifyRenewal(String jws, AppleEnvironment env) throws AppleVerificationException {
        try {
            return toRenewal(forEnvironment(env).verifyAndDecodeRenewalInfo(jws));
        } catch (VerificationException e) {
            throw map(e);
        } catch (RuntimeException e) {
            throw malformed(e);
        }
    }

    public AppleNotification verifyNotification(String signedPayload) throws AppleVerificationException {
        ResponseBodyV2DecodedPayload p;
        AppleEnvironment env = AppleEnvironment.PRODUCTION;
        try {
            try {
                p = production.verifyAndDecodeNotification(signedPayload);
            } catch (VerificationException e) {
                // the sandbox verifier still checks the signature chain, the bundle id and the environment
                if (e.getStatus() != VerificationStatus.INVALID_ENVIRONMENT
                        && e.getStatus() != VerificationStatus.INVALID_APP_IDENTIFIER) {
                    throw map(e);
                }
                p = sandbox.verifyAndDecodeNotification(signedPayload);
                env = AppleEnvironment.SANDBOX;
            }
        } catch (VerificationException e) {
            throw map(e);
        } catch (RuntimeException e) {
            throw malformed(e);
        }
        AppleNotification n = new AppleNotification();
        n.notificationUuid = p.getNotificationUUID();
        n.type = p.getNotificationType() != null ? p.getNotificationType().name() : p.getRawNotificationType();
        n.subtype = p.getSubtype() != null ? p.getSubtype().name() : p.getRawSubtype();
        n.environment = env;
        Data data = p.getData();
        if (data != null) {
            if (data.getSignedTransactionInfo() != null) {
                n.transaction = verifyTransaction(data.getSignedTransactionInfo(), env);
            }
            if (data.getSignedRenewalInfo() != null) {
                n.renewal = verifyRenewal(data.getSignedRenewalInfo(), env);
            }
        }
        return n;
    }
}
