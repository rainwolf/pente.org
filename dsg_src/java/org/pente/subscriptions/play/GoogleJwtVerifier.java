package org.pente.subscriptions.play;

import java.util.Map;

import com.google.api.client.json.webtoken.JsonWebSignature;
import com.google.auth.oauth2.TokenVerifier;

/**
 * Verifies a Pub/Sub push OIDC token with google-auth-library-oauth2-http's TokenVerifier
 * (Google's public keys, expiry and audience). RtdnAuth then checks the email claims.
 */
public class GoogleJwtVerifier implements JwtSignatureVerifier {

    private final TokenVerifier verifier;

    public GoogleJwtVerifier(String audience) {
        this.verifier = TokenVerifier.newBuilder().setAudience(audience).build();
    }

    public Map<String, Object> verify(String jwt) throws Exception {
        JsonWebSignature jws = verifier.verify(jwt);
        return jws.getPayload();
    }
}
