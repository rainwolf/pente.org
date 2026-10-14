package org.pente.subscriptions.play.test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import jakarta.servlet.ServletContext;

import junit.framework.TestCase;

import org.pente.subscriptions.play.AccountIds;
import org.pente.subscriptions.play.GoogleJwtVerifier;
import org.pente.subscriptions.play.PlayApiException;
import org.pente.subscriptions.play.PlayBillingSetup;
import org.pente.subscriptions.play.PlayEntitlementService;
import org.pente.subscriptions.play.RtdnAuth;
import org.pente.subscriptions.play.UnconfiguredPlayApi;

/**
 * Production wiring that needs no network: the real JWT verifier rejects a non-JWT before any key
 * fetch, a missing service-account key turns every Play call into a retryable failure, and any
 * Throwable while wiring Play (a missing androidpublisher jar) leaves Play unwired without escaping
 * into DSGContextListener.
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

    public void testLinkageErrorWhileWiringLeavesPlayUnwiredAndDoesNotEscape() {
        final List<String> setAttributes = new ArrayList<String>();
        ServletContext ctx = (ServletContext) Proxy.newProxyInstance(ServletContext.class.getClassLoader(),
                new Class<?>[] {ServletContext.class}, new InvocationHandler() {
                    public Object invoke(Object proxy, Method method, Object[] args) {
                        if ("getInitParameter".equals(method.getName())) {
                            throw new NoClassDefFoundError("com/google/api/services/androidpublisher/AndroidPublisher$Builder");
                        }
                        if ("setAttribute".equals(method.getName())) {
                            setAttributes.add((String) args[0]);
                        }
                        return null;
                    }
                });
        final List<PlayEntitlementService> started = new ArrayList<PlayEntitlementService>();
        Consumer<PlayEntitlementService> startPollers = new Consumer<PlayEntitlementService>() {
            public void accept(PlayEntitlementService s) {
                started.add(s);
            }
        };

        PlayEntitlementService service = PlayBillingSetup.install(ctx, null, AccountIds.fromSecret("s"), startPollers);

        assertNull(service);
        assertTrue(setAttributes.isEmpty());
        assertTrue(started.isEmpty());
    }
}
