package org.pente.subscriptions.play;

import java.util.function.Consumer;

import jakarta.servlet.ServletContext;

import org.apache.log4j.Category;
import org.pente.gameServer.server.Resources;
import org.pente.turnBased.CacheTBStorer;

/** Builds the PlayEntitlementService at startup (DSGContextListener) from web.xml context-params. */
public final class PlayBillingSetup {

    private static final Category log4j = Category.getInstance(PlayBillingSetup.class.getName());

    private PlayBillingSetup() {
    }

    /**
     * Wires Play into the context: builds the service, starts its pollers, then sets the attribute the
     * servlets read. Run last in DSGContextListener. Any Throwable (a missing androidpublisher jar is a
     * NoClassDefFoundError, which create's catch does not see) is logged and leaves the attribute unset,
     * so the rest of the site still starts; both servlets treat a null service as retry later.
     *
     * @return the service, or null when wiring failed
     */
    public static PlayEntitlementService install(ServletContext ctx, Resources resources, AccountIds accountIds,
                                                 Consumer<PlayEntitlementService> startPollers) {
        try {
            PlayEntitlementService service = create(ctx, resources, accountIds);
            startPollers.accept(service);
            ctx.setAttribute(PlayEntitlementService.class.getName(), service);
            log4j.info("PlayBillingSetup: created PlayEntitlementService");
            return service;
        } catch (Throwable t) {
            log4j.error("PlayBillingSetup: Google Play Billing not wired, Play purchases are refused until restart", t);
            return null;
        }
    }

    /**
     * @param accountIds from AccountIds.fromEnvironment(), which already failed startup on a blank secret.
     *                   A missing service-account key does not stop startup: Play calls then fail as
     *                   transient errors and are retried once the key is installed.
     */
    public static PlayEntitlementService create(ServletContext ctx, Resources resources, AccountIds accountIds) {
        String packageName = ctx.getInitParameter("googlePlayPackageName");
        String keyFile = ctx.getInitParameter("googlePlayServiceAccountKey");
        PlayApi api;
        try {
            api = GooglePlayApi.fromKeyFile(keyFile, packageName);
            log4j.info("PlayBillingSetup: Google Play API ready for " + packageName);
        } catch (Exception e) {
            log4j.error("PlayBillingSetup: Google Play API unavailable (key " + keyFile + "), purchases are retried later", e);
            api = new UnconfiguredPlayApi(String.valueOf(e));
        }
        MySQLPlayStore store = new MySQLPlayStore(resources.getDbHandler());
        DsgPlayGateway gateway = new DsgPlayGateway(resources.getDsgPlayerStorer(),
                (CacheTBStorer) resources.getTbGameStorer(), resources.getTourneyStorer(),
                resources.getDsgMessageStorer(), resources.getNotificationServer());
        return new PlayEntitlementService(api, store, store, gateway, accountIds, System::currentTimeMillis);
    }
}
