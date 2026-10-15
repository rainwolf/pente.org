package org.pente.subscriptions.apple;

import java.nio.file.Paths;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

import jakarta.servlet.ServletContext;

import org.apache.log4j.Category;
import org.pente.gameServer.server.Resources;
import org.pente.subscriptions.play.DsgPlayGateway;
import org.pente.subscriptions.play.MySQLPlayStore;
import org.pente.turnBased.CacheTBStorer;

/** Builds the App Store pieces at startup (DSGContextListener) from web.xml context-params (spec 2026-10-15 §4.1). */
public final class AppleBillingSetup {

    private static final Category log4j = Category.getInstance(AppleBillingSetup.class.getName());

    private AppleBillingSetup() {
    }

    /**
     * Wires the App Store into the context: builds AppleBilling, starts its pollers, then sets the attribute the
     * servlets read. Run last in DSGContextListener. Any Throwable (a missing jar is a NoClassDefFoundError) is
     * logged and leaves the attribute unset, so the rest of the site still starts; the purchase servlet then
     * answers "retry later" and the notification servlet 500.
     *
     * @return the wired billing, or null when wiring failed
     */
    public static AppleBilling install(ServletContext ctx, Resources resources, AppleAccountTokens tokens,
                                       Consumer<AppleBilling> startPollers) {
        try {
            AppleBilling billing = create(ctx, resources, tokens);
            startPollers.accept(billing);
            ctx.setAttribute(AppleBilling.class.getName(), billing);
            log4j.info("AppleBillingSetup: App Store billing wired");
            return billing;
        } catch (Throwable t) {
            log4j.error("AppleBillingSetup: App Store billing not wired, iOS purchases are retried until restart", t);
            return null;
        }
    }

    private static String param(ServletContext ctx, String name) {
        String value = ctx.getInitParameter(name);
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    /** the real API, or UnconfiguredAppleApi when the key file or its ids are missing (startup still succeeds) */
    public static AppleApi api(ServletContext ctx, AppStoreVerifier verifier) {
        String keyFile = param(ctx, "appleIapKeyFile");
        try {
            AppleApi api = AppStoreApi.fromKeyFile(keyFile, param(ctx, "appleIapKeyId"), param(ctx, "appleIapIssuerId"),
                    param(ctx, "appleBundleId"), verifier);
            log4j.info("AppleBillingSetup: App Store Server API ready");
            return api;
        } catch (Exception e) {
            log4j.error("AppleBillingSetup: App Store Server API unavailable (key " + keyFile
                    + "), purchases answer retry later and notifications 500 until it is installed: " + e);
            return new UnconfiguredAppleApi(String.valueOf(e));
        }
    }

    public static AppleBilling create(ServletContext ctx, Resources resources, AppleAccountTokens tokens) {
        String appAppleId = param(ctx, "appleAppAppleId");
        AppStoreVerifier verifier = AppStoreVerifier.create(param(ctx, "appleBundleId"),
                appAppleId == null ? null : Long.valueOf(appAppleId), true);
        AppleApi api = api(ctx, verifier);
        LongSupplier clock = System::currentTimeMillis;
        String sandboxFile = param(ctx, "appleSandboxAccountsFile");
        AppleSandboxAccounts accounts = new AppleSandboxAccounts(sandboxFile == null ? null : Paths.get(sandboxFile), clock);
        AppleSandboxGate gate = new DsgSandboxGate(accounts, resources.getDsgPlayerStorer());
        MySQLPlayStore subscribers = new MySQLPlayStore(resources.getDbHandler());
        MySQLAppleStore store = new MySQLAppleStore(resources.getDbHandler(), clock);
        DsgPlayGateway gateway = new DsgPlayGateway(resources.getDsgPlayerStorer(),
                (CacheTBStorer) resources.getTbGameStorer(), resources.getTourneyStorer(),
                resources.getDsgMessageStorer(), resources.getNotificationServer(), "App Store");
        AppleEntitlementService service = new AppleEntitlementService(api, verifier, store, subscribers, gateway, tokens,
                gate, clock);
        AppleNotificationHandler handler = new AppleNotificationHandler(service, verifier, store, clock);
        return new AppleBilling(service, handler, new AppleHistoryReplay(api, verifier, store, handler, clock));
    }
}
