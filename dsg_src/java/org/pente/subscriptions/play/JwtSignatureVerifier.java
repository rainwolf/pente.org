package org.pente.subscriptions.play;

import java.util.Map;

/**
 * Signature and expiry check of the Pub/Sub push OIDC token. GoogleJwtVerifier is the real
 * implementation (google-auth TokenVerifier); tests use a fake so the claim rules are testable.
 */
public interface JwtSignatureVerifier {

    /** @return the token's payload claims; throws when the signature or expiry is invalid */
    Map<String, Object> verify(String jwt) throws Exception;
}
