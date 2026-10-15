package org.pente.subscriptions.apple.test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import jakarta.servlet.ServletContext;

import junit.framework.TestCase;

import org.pente.subscriptions.apple.AppStoreVerifier;
import org.pente.subscriptions.apple.AppleAccountTokens;
import org.pente.subscriptions.apple.AppleBilling;
import org.pente.subscriptions.apple.AppleBillingSetup;
import org.pente.subscriptions.apple.UnconfiguredAppleApi;

/**
 * Production wiring that needs no network or DB (spec 2026-10-15 §4.1): a missing key leaves the API unconfigured
 * without failing startup, and any Throwable while wiring leaves the App Store unwired without escaping into
 * DSGContextListener.
 *
 * Run: ant test-apple-billing
 */
public class AppleWiringTest extends TestCase {

    public AppleWiringTest(String name) {
        super(name);
    }

    private static ServletContext context(final Map<String, String> params, final List<String> setAttributes) {
        return (ServletContext) Proxy.newProxyInstance(ServletContext.class.getClassLoader(),
                new Class<?>[] {ServletContext.class}, new InvocationHandler() {
                    public Object invoke(Object proxy, Method method, Object[] args) {
                        if ("getInitParameter".equals(method.getName())) {
                            if (params == null) {
                                throw new NoClassDefFoundError("com/apple/itunes/storekit/verification/SignedDataVerifier");
                            }
                            return params.get((String) args[0]);
                        }
                        if ("setAttribute".equals(method.getName())) {
                            setAttributes.add((String) args[0]);
                        }
                        return null;
                    }
                });
    }

    public void testMissingKeyLeavesTheApiUnconfigured() {
        Map<String, String> params = new HashMap<String, String>();
        params.put("appleBundleId", "be.submanifold.penteLive");
        params.put("appleAppAppleId", "595426592");
        params.put("appleIapKeyId", "");
        params.put("appleIapIssuerId", "");
        params.put("appleIapKeyFile", "/nonexistent/apple-iap-key.p8");
        ServletContext ctx = context(params, new ArrayList<String>());
        AppStoreVerifier verifier = AppStoreVerifier.create("be.submanifold.penteLive", Long.valueOf(595426592L), true);
        assertTrue(AppleBillingSetup.api(ctx, verifier) instanceof UnconfiguredAppleApi);
    }

    public void testLinkageErrorWhileWiringLeavesAppleUnwiredAndDoesNotEscape() {
        List<String> setAttributes = new ArrayList<String>();
        final List<AppleBilling> started = new ArrayList<AppleBilling>();
        Consumer<AppleBilling> startPollers = new Consumer<AppleBilling>() {
            public void accept(AppleBilling b) {
                started.add(b);
            }
        };

        AppleBilling billing = AppleBillingSetup.install(context(null, setAttributes), null,
                AppleAccountTokens.fromSecret("s"), startPollers);

        assertNull(billing);
        assertTrue(setAttributes.isEmpty());
        assertTrue(started.isEmpty());
    }
}
