package org.pente.subscriptions.play;

import java.util.Collection;
import java.util.Map;

/**
 * RTDN endpoint authentication (spec 2026-10-11 §4.6): a Pub/Sub push "Authorization: Bearer" OIDC
 * token whose signature verifies, whose audience equals googlePlayRtdnAudience, and whose email claim
 * equals googlePlayRtdnServiceAccount with email_verified set.
 */
public final class RtdnAuth {

    private RtdnAuth() {
    }

    public static boolean authorized(String authorizationHeader, JwtSignatureVerifier verifier,
                                     String audience, String serviceAccount) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return false;
        }
        if (verifier == null || isBlank(audience) || isBlank(serviceAccount)) {
            return false;
        }
        String jwt = authorizationHeader.substring("Bearer ".length()).trim();
        if (jwt.isEmpty()) {
            return false;
        }
        Map<String, Object> claims;
        try {
            claims = verifier.verify(jwt);
        } catch (Exception e) {
            return false;
        }
        return claimsAccepted(claims, audience, serviceAccount);
    }

    public static boolean claimsAccepted(Map<String, Object> claims, String audience, String serviceAccount) {
        if (claims == null || isBlank(audience) || isBlank(serviceAccount)) {
            return false;
        }
        Object aud = claims.get("aud");
        boolean audienceOk = audience.equals(aud)
                || (aud instanceof Collection && ((Collection<?>) aud).contains(audience));
        Object verified = claims.get("email_verified");
        boolean emailVerified = Boolean.TRUE.equals(verified) || "true".equals(verified);
        return audienceOk && serviceAccount.equals(claims.get("email")) && emailVerified;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
