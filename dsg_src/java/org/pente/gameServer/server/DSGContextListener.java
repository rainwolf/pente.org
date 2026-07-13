/**
 * DSGContextListener.java
 * Copyright (C) 2001 Dweebo's Stone Games (http://www.pente.org/)
 * <p>
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, you can find it online at
 * http://www.gnu.org/copyleft/gpl.txt
 */

package org.pente.gameServer.server;

import com.google.auth.oauth2.GoogleCredentials;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.websocket.server.ServerContainer;
import jakarta.websocket.server.ServerEndpointConfig;
import org.apache.log4j.Category;
import org.pente.database.DBHandler;
import org.pente.database.MySQLDBHandler;
import org.pente.game.*;
import org.pente.gameDatabase.GameStats;
import org.pente.gameDatabase.GameStorerSearcher;
import org.pente.gameDatabase.MySQLGameStorerSearcher;
import org.pente.gameDatabase.SimpleMySQLGameStats;
import org.pente.gameServer.client.web.LeaderBoard;
import org.pente.gameServer.client.web.SiteStatsData;
import org.pente.gameServer.core.*;
import org.pente.gameServer.event.WebSocketEndpoint;
import org.pente.gameServer.tourney.CacheTourneyStorer;
import org.pente.gameServer.tourney.MySQLTourneyStorer;
import org.pente.gameServer.tourney.Tourney;
import org.pente.gameServer.tourney.TourneyStorer;
import org.pente.kingOfTheHill.CacheKOTHStorer;
import org.pente.kingOfTheHill.MySQLKOTHStorer;
import org.pente.message.CacheMessageStorer;
import org.pente.message.DSGMessageStorer;
import org.pente.message.MySQLDSGMessageStorer;
import org.pente.notifications.CacheNotificationServer;
import org.pente.notifications.MySQLNotificationServer;
import org.pente.notifications.NotificationServer;
import org.pente.turnBased.CacheTBStorer;
import org.pente.turnBased.MySQLTBGameStorer;

import java.io.File;
import java.io.FileInputStream;
import java.sql.Driver;
import java.sql.DriverManager;
import java.util.*;


public class DSGContextListener implements ServletContextListener {

    private static final Category log4j =
            Category.getInstance(DSGContextListener.class.getName());

    private GameStats gameStats;

    private List<Timer> timers = null;

    public void contextInitialized(ServletContextEvent servletContextEvent) {
        try {
            timers = new ArrayList<>();

            ServletContext ctx = servletContextEvent.getServletContext();
            ContextHolder.servletContext = ctx;
            Resources resources = new Resources();

            RedisConnectionManager redisConnectionManager = RedisConnectionManager.initialize();
            resources.setRedisConnectionManager(redisConnectionManager);

            String appletVersion = ctx.getInitParameter("appletVersion");
            resources.setAppletVersion(appletVersion);
            ctx.setAttribute("appletVersion", appletVersion);

            // get property file location and initialize database handler
            DBHandler dbHandler = new MySQLDBHandler(true, "dsg");
            resources.setDbHandler(dbHandler);
            ctx.setAttribute(DBHandler.class.getName(), dbHandler);
            log4j.info("contextInitialized(), created DBHandler[dsg]");

            DBHandler dbHandlerRo = new MySQLDBHandler(true, "dsg_ro");
            resources.setDbHandlerRo(dbHandlerRo);
            log4j.info("contextInitialized(), created DBHandler[dsg_ro]");

            String penteLiveGCMkey = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
            // Get OAuth 2.0 token
            GoogleCredentials googleCredentials = GoogleCredentials.fromStream(new FileInputStream(penteLiveGCMkey))
                    .createScoped(Collections.singletonList("https://www.googleapis.com/auth/cloud-platform"));

            String penteLiveAPNSkey = ctx.getInitParameter("penteLiveAPNSkey");
            String penteLiveAPNSpwd = ctx.getInitParameter("penteLiveAPNSpassword");
            boolean productionFlag = ctx.getInitParameter("penteLiveAPNSproductionFlag").equals("true");
            NotificationServer notificationServer = new CacheNotificationServer(new MySQLNotificationServer(dbHandler), penteLiveAPNSkey, googleCredentials, penteLiveAPNSpwd, productionFlag);
            resources.setNotificationServer(notificationServer);

            GameVenueStorer gameVenueStorer = new MySQLGameVenueStorer(dbHandler);
            // ensure every game defined in GridStateFactory has its game_event
            // rows (live/speed/turn-based/koth) so results can resolve their
            // event_id without hand-written add_dummy_*.sql scripts.  site 2 is
            // the live Pente.org site.  idempotent and cheap; safe every boot.
            // Register game_event rows + server offerings as a best-effort step:
            // a transient DB error (or a missing site row on a half-migrated DB)
            // must not abort the whole context init the way an uncaught throw
            // here would.  Mirrors the venue-storer constructor, which already
            // swallows its own DB errors, so boot degrades rather than fails.
            try {
                gameVenueStorer.registerAllGames(2);

                // keep each server's offered games in sync with GridStateFactory
                // so adding a game only means adding its ids to the arrays below.
                // idempotent; runs before setupLiveGameServers() reads offerings.
                // the Arena server (in-memory, id 0) copies its games from server
                // 1 in setupArenaServers(), so it inherits the live+speed set.
                int[] liveGames = GridStateFactory.LIVE_GAMES;
                int[] goGames = {
                        GridStateFactory.GO, GridStateFactory.SPEED_GO,
                        GridStateFactory.GO9, GridStateFactory.SPEED_GO9,
                        GridStateFactory.GO13, GridStateFactory.SPEED_GO13};
                // servers 1 (Main Room) and 37 (Beginners): all live + speed games
                MySQLServerStorer.addServerGames(dbHandler, 1, 2, liveGames, MySQLGameVenueStorer.LIVE_EVENT);
                MySQLServerStorer.addServerGames(dbHandler, 37, 2, liveGames, MySQLGameVenueStorer.LIVE_EVENT);
                // server 46 (Go): the 3 Go variants and their speed variants
                MySQLServerStorer.addServerGames(dbHandler, 46, 2, goGames, MySQLGameVenueStorer.LIVE_EVENT);
                // server 45 (King of the Hill): koth of all normal + speed variants
                MySQLServerStorer.addServerGames(dbHandler, 45, 2, liveGames, MySQLGameVenueStorer.KOTH_EVENT);
            } catch (Throwable t) {
                log4j.error("Problem registering games / server offerings at boot", t);
            }

            resources.setGameVenueStorer(gameVenueStorer);
            ctx.setAttribute(GameVenueStorer.class.getName(), gameVenueStorer);
            log4j.info("contextInitialized(), created GameVenueStorer");

            CacheDSGPlayerStorer dsgPlayerStorer = new CacheDSGPlayerStorer(new MySQLDSGPlayerStorer(dbHandler, gameVenueStorer), ctx, dbHandler);
            dsgPlayerStorer.setNotificationServer(notificationServer);
            resources.setDsgPlayerStorer(dsgPlayerStorer);
            ctx.setAttribute(DSGPlayerStorer.class.getName(), dsgPlayerStorer);
            log4j.info("contextInitialized(), created DSGPlayerStorer");

            System.setProperty("mail.smtp.host", ctx.getInitParameter("mail.smtp.host"));
            System.setProperty("mail.smtp.user", ctx.getInitParameter("mail.smtp.user"));
            System.setProperty("mail.smtp.password", ctx.getInitParameter("mail.smtp.password"));
            System.setProperty("mail.imap.host", ctx.getInitParameter("mail.imap.host"));
            System.setProperty("mail.imap.user", ctx.getInitParameter("mail.imap.user"));
            System.setProperty("mail.imap.password", ctx.getInitParameter("mail.imap.password"));
            Boolean emailEnabled = Boolean.valueOf(ctx.getInitParameter("emailEnabled"));

            ctx.setAttribute("emailEnabled", emailEnabled);
            resources.setEmailEnabled(emailEnabled.booleanValue());
            log4j.info("contextInitialized(), emailEnabled=" + emailEnabled);


            // setup storers
            GameStorer gameStorer = new MySQLPenteGameStorer(dbHandler,
                    gameVenueStorer);
            PlayerStorer playerStorer = (PlayerStorer) gameStorer;
            GameStorer gameStorerRo = new MySQLPenteGameStorer(dbHandlerRo,
                    gameVenueStorer);
            GameStorerSearcher gameStorerSearcher = new MySQLGameStorerSearcher(
                    dbHandlerRo, gameStorerRo, gameVenueStorer);


            resources.setGameStorer(gameStorer);
            resources.setGameStorerRo(gameStorerRo);
            ctx.setAttribute(GameStorer.class.getName(), gameStorer);
            log4j.info("contextInitialized(), created GameStorer");

            resources.setPlayerStorer(playerStorer);
            ctx.setAttribute(PlayerStorer.class.getName(), playerStorer);
            log4j.info("contextInitialized(), created PlayerStorer");

            resources.setGameStorerSearcher(gameStorerSearcher);
            ctx.setAttribute(GameStorerSearcher.class.getName(), gameStorerSearcher);
            log4j.info("contextInitialized(), created GameStorerSearcher");

            MySQLDSGReturnEmailStorer returnEmailStorer = new MySQLDSGReturnEmailStorer(dbHandler);
            resources.setReturnEmailStorer(returnEmailStorer);
            ctx.setAttribute(MySQLDSGReturnEmailStorer.class.getName(), returnEmailStorer);
            log4j.info("contextInitialized(), created MySQLDSGReturnEmailStorer");

            ServerStatsHandler serverStatsHandler = new ServerStatsHandler();
            resources.setServerStatsHandler(serverStatsHandler);
            ctx.setAttribute(ServerStatsHandler.class.getName(),
                    serverStatsHandler);
            gameStats = new SimpleMySQLGameStats(dbHandlerRo, 60 * 60 * 1000);
            // TODO add game stats to resources
            ctx.setAttribute(GameStats.class.getName(), gameStats);

            SiteStatsData siteStatsData = new SiteStatsData(
                    serverStatsHandler, gameStats);
            resources.setSiteStatsData(siteStatsData);
            ctx.setAttribute(SiteStatsData.class.getName(), siteStatsData);
            log4j.info("contextInitialized(), created SiteStatsData");

            String cipherKey = ctx.getInitParameter("cipherKeyFile");
            File cipherKeyFile = new File(cipherKey);
            if (!cipherKeyFile.exists() || !cipherKeyFile.isFile() ||
                    !cipherKeyFile.canRead()) {
                log4j.info("Cipher file invalid!");
            }
            PasswordHelper passwordHelper = new PasswordHelper(cipherKeyFile);
            resources.setPasswordHelper(passwordHelper);
            ctx.setAttribute(PasswordHelper.class.getName(), passwordHelper);
            log4j.info("contextInitialized(), created PasswordHelper");

            ActivityLogger activityLogger = new ActivityLogger(resources);
            resources.setActivityLogger(activityLogger);
            ctx.setAttribute(ActivityLogger.class.getName(), activityLogger);
            log4j.info("contextInitialized(), created ActivityLogger");

            String localGameDir = ctx.getInitParameter("localGameDir");
            String localPlayerDir = ctx.getInitParameter("localPlayerDir");
            // file game storer shared by all servers
            GameStorer fileGameStorer = null;
            try {
                fileGameStorer = new SimpleFileGameStorer(
                        new PGNGameFormat(),
                        new File(localGameDir),
                        new File(localPlayerDir));
            } catch (Throwable t) {
                log4j.error("Problem creating file game storer", t);
                return;
            }
            resources.setFileGameStorer(fileGameStorer);
            log4j.info("contextInitialized(), created FileGameStorer");

            // setup this for XMLAIConfigurator
            String aiConfigFile = ctx.getInitParameter("aiConfigFile");
            if (aiConfigFile != null) {
                ctx.setAttribute("aiConfigFile", aiConfigFile);
                log4j.info("contextInitialized(), aiConfigFile=" + aiConfigFile);
            }
            resources.setAiConfigFile(aiConfigFile);


            DSGMessageStorer dsgMessageStorer = new CacheMessageStorer(
                    new MySQLDSGMessageStorer(dbHandler),
                    emailEnabled,
                    ctx.getInitParameter("mail.smtp.host"),
                    Integer.parseInt(ctx.getInitParameter("mail.smtp.port")),
                    ctx.getInitParameter("mail.smtp.user"),
                    ctx.getInitParameter("mail.smtp.password"),
                    dsgPlayerStorer);
            resources.setDsgMessageStorer(dsgMessageStorer);
            log4j.info("contextInitialized(), created DSGMessageStorer");


            CacheKOTHStorer kothStorer = new CacheKOTHStorer(
                    new MySQLKOTHStorer(dbHandler), dsgPlayerStorer);
            log4j.info("contextInitialized(), created CacheKOTHStorer");

            CacheTBStorer tbGameStorer = new CacheTBStorer(
                    new MySQLTBGameStorer(dbHandler), dsgPlayerStorer, gameStorer,
                    dsgMessageStorer, kothStorer);
            tbGameStorer.setNotificationServer(notificationServer);
            resources.setTbGameStorer(tbGameStorer);
            log4j.info("contextInitialized(), created TBGameStorer");

            kothStorer.setTbStorer(tbGameStorer);
            resources.setKOTHStorer(kothStorer);

            CacheTourneyStorer tourneyStorer = new CacheTourneyStorer(
                    new MySQLTourneyStorer(dbHandler, gameVenueStorer));
            resources.setTourneyStorer(tourneyStorer);
            tourneyStorer.addTourneyListener(tbGameStorer);
            tourneyStorer.setTBStorer(tbGameStorer);
            tourneyStorer.setDsgPlayerStorer(dsgPlayerStorer);
            tourneyStorer.setNotificationServer(notificationServer);
            tourneyStorer.setKothStorer(kothStorer);
            tbGameStorer.setTourneyStorer(tourneyStorer);
            log4j.info("contextInitialized(), created TourneyStorer");

            FastMySQLDSGGameLookup lookup = new FastMySQLDSGGameLookup(
                    dbHandlerRo, gameVenueStorer);
            resources.setDsgGameLookup(lookup);

            DSGFollowerStorer followerStorer = new CacheDSGFollowerStorer(new MySQLDSGFollowerStorer(dbHandler), notificationServer, dsgPlayerStorer);
            resources.setFollowerStorer(followerStorer);

            ctx.setAttribute(Resources.class.getName(), resources);

            LeaderBoard lb = new LeaderBoard(dbHandler, dsgPlayerStorer);
            ctx.setAttribute("leaderboard", lb);

            setupLiveGameServers(resources, ctx, tourneyStorer);

            setupArenaServers(resources, ctx);

            tourneyStorer.setupTBTournaments();

        } catch (Throwable t) {
            log4j.error("Problem in contextInitialized()", t);
        }
    }

    private void setupArenaServers(Resources resources, ServletContext ctx) {
        ServerData toCopy = resources.getServerData(1);
        ServerData serverData = new ServerData();
        serverData.setName("Arena Server");
        serverData.setPort(15999);
        serverData.setServerId(0);
        serverData.setTournament(false);
        serverData.setPrivateServer(false);
        serverData.setArena(true);
        for (GameEventData g : toCopy.getGameEvents()) {
            serverData.addGameEvent(g);
        }

        try {
            ArenaServer server = new ArenaServer(resources, serverData);
            addServer(server, ctx, resources);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private void addServer(Server server, ServletContext ctx, Resources resources) {
        ServerData serverData = server.getServerData();
        resources.addServer(server);
        log4j.info("Server " + serverData + " started.");
        ServerEndpointConfig.Configurator configurator = new WebSocketConfigurator(server);
        ServerEndpointConfig sec = ServerEndpointConfig.Builder.
                create(WebSocketEndpoint.class, "/websocketServer/" + serverData.getPort()).
                configurator(configurator).build();
        ServerContainer serverContainer = (ServerContainer) ctx.getAttribute("jakarta.websocket.server.ServerContainer");
        try {
            serverContainer.addEndpoint(sec);
        } catch (Throwable t) {
            t.printStackTrace();
        }

    }

    private void setupLiveGameServers(Resources resources, ServletContext ctx, TourneyStorer tourneyStorer) {
        // start game servers
        try {
            List<ServerData> serverData = MySQLServerStorer.getActiveServers(
                    resources.getDbHandler(), resources.getGameVenueStorer());

            for (ServerData data : serverData) {
                Server server;
                if (data.isTournament()) {
                    continue;
                }
                server = new Server(resources, data);
                addServer(server, ctx, resources);
            }

            List<Tourney> tournaments = new ArrayList<>();
            tournaments.addAll(tourneyStorer.getCurrentTournies());
            tournaments.addAll(tourneyStorer.getUpcomingTournies());
            Date oneHourAgo = new Date();
            oneHourAgo.setTime(oneHourAgo.getTime() - 3600L * 1000);
            for (Tourney t : tournaments) {
                Tourney tourney = tourneyStorer.getTourneyDetails(t.getEventID());
                if (tourney.isSpeed()) {
                    log4j.info("tournament " + tourney.getName());
                    if (tourney.getStartDate().before(oneHourAgo)) {
                        resources.startNewServer(tourney.getEventID());
                        log4j.info("Server " + tourney.getName() + " started.");
                    } else {
                        Date startDate = new Date(tourney.getStartDate().getTime() - 3600L * 1000);
                        Timer timer = new Timer(tourney.getName() + " start timer");
                        int index = timers.size();
                        timer.schedule(new TimerTask() {
                            @Override
                            public void run() {
                                resources.startNewServer(tourney.getEventID());
                                log4j.info("Server " + tourney.getName() + " started.");
                                Timer timer = timers.get(index);
                                timer.cancel();
                                timer.purge();
                                timers.set(index, null);
                            }
                        }, startDate);
                        timers.add(timer);
                    }
                    if (tourney.getNumRounds() == 0) {
                        ((CacheTourneyStorer) tourneyStorer).startTournamentOrSetupTimer(tourney);
                    }
                }
            }

            log4j.info("Servers ready.");

        } catch (Throwable t) {
            log4j.error("Problem creating servers.", t);
        }
    }

    public void contextDestroyed(ServletContextEvent servletContextEvent) {
        if (gameStats != null) {
            gameStats.destroy();
        }

        // stop servers
        ServletContext ctx = servletContextEvent.getServletContext();
        Resources resources = (Resources)
                ctx.getAttribute(Resources.class.getName());

        resources.getTbGameStorer().destroy();
        ((CacheTourneyStorer) resources.getTourneyStorer()).destroy();
        resources.getDsgPlayerStorer().destroy();
        resources.getKOTHStorer().destroy();
        ((CacheNotificationServer) resources.getNotificationServer()).destroy();
        ((CacheMessageStorer) resources.getDsgMessageStorer()).destroy();
        RedisConnectionManager.getInstance().destroy();

        for (Timer timer : timers) {
            if (timer != null) {
                timer.cancel();
                timer.purge();
            }
        }
        timers.clear();

        for (Object o : resources.getServers()) {
            Server s = (Server) o;
            log4j.info("Destroying server " + s.getServerData() + ".");
            s.destroy();
        }

        // --- JDBC / MariaDB Connector/J cleanup ---
        // Runs LAST, after every storer/server/redis/timer has been destroyed,
        // so no in-flight queries remain. Removes the classloader leak logged
        // on hot reload: the org.mariadb.jdbc.Driver registration. Bundled
        // driver is mariadb-java-client-3.5.9 (org.mariadb.jdbc namespace).
        // MariaDB Connector/J spawns no AbandonedConnectionCleanupThread and no
        // per-statement "Statement Cancellation Timer" threads, so there is
        // nothing thread-wise to shut down here (unlike legacy MySQL C/J 5.1.x).

        // Deregister only the JDBC drivers loaded by THIS webapp classloader,
        // so a driver class held by Tomcat's classloader is left untouched.
        try {
            ClassLoader webappCl = this.getClass().getClassLoader();
            for (Enumeration<Driver> drivers = DriverManager.getDrivers(); drivers.hasMoreElements(); ) {
                Driver d = drivers.nextElement();
                if (d.getClass().getClassLoader() == webappCl) {
                    try {
                        DriverManager.deregisterDriver(d);
                        log4j.info("contextDestroyed(), deregistered JDBC driver " + d.getClass().getName());
                    } catch (Throwable t) {
                        log4j.error("Error deregistering JDBC driver " + d.getClass().getName() + ".", t);
                    }
                }
            }
        } catch (Throwable t) {
            log4j.error("Error enumerating JDBC drivers for deregistration.", t);
        }

        // Known, out-of-scope third-party leaks that still warn on hot reload:
        //  - Jive Forums task engine ("Task Engine Worker *", "Cache Timer")
        //  - Netty InternalThreadLocalMap (pulled in transitively via
        //    google-auth / grpc; logged as checkThreadLocalMapForLeaks)
        // These are owned by third-party libraries we do not modify and are
        // expected/harmless on a webapp reload.
    }
}