/** DSGContextListener.java
 *  Copyright (C) 2001 Dweebo's Stone Games (http://www.pente.org/)
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, you can find it online at
 *  http://www.gnu.org/copyleft/gpl.txt
 */

package org.pente.gameServer.server;

import java.io.*;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.servlet.*;
import javax.websocket.server.ServerContainer;
import javax.websocket.server.ServerEndpointConfig;

import org.apache.log4j.*;

import org.pente.database.*;
import org.pente.game.*;
import org.pente.gameDatabase.*;
import org.pente.gameServer.core.*;
import org.pente.gameServer.client.web.*;
import org.pente.gameServer.event.WebSocketEndpoint;
import org.pente.gameServer.tourney.*;
import org.pente.notifications.CacheNotificationServer;
import org.pente.notifications.MySQLNotificationServer;
import org.pente.notifications.NotificationServer;
import org.pente.notifications.NotificationServerException;
import org.pente.turnBased.*;
import org.pente.message.*;

import org.pente.kingOfTheHill.*;


public class DSGContextListener implements ServletContextListener {
    
    private static final Category log4j = 
        Category.getInstance(DSGContextListener.class.getName());

    private DBHandler dbHandler;
    private GameVenueStorer gameVenueStorer;
    private CacheDSGPlayerStorer dsgPlayerStorer;
    private GameStats gameStats;
    
    public void contextInitialized(ServletContextEvent servletContextEvent) {
        try {

            ServletContext ctx = servletContextEvent.getServletContext();
            Resources resources = new Resources();
            
            String appletVersion = ctx.getInitParameter("appletVersion");
            resources.setAppletVersion(appletVersion);
            ctx.setAttribute("appletVersion", appletVersion);

            // get property file location and initialize database handler
            dbHandler = new MySQLDBHandler(true, "dsg");
            resources.setDbHandler(dbHandler);
            ctx.setAttribute(DBHandler.class.getName(), dbHandler);
            log4j.info("contextInitialized(), created DBHandler[dsg]");
            
            DBHandler dbHandlerRo = new MySQLDBHandler(true, "dsg_ro");
            resources.setDbHandlerRo(dbHandlerRo);
            log4j.info("contextInitialized(), created DBHandler[dsg_ro]");

            String penteLiveGCMkey = ctx.getInitParameter("penteLiveGCMkey");
            String penteLiveAPNSkey = ctx.getInitParameter("penteLiveAPNSkey");
            String penteLiveAPNSpwd = ctx.getInitParameter("penteLiveAPNSpassword");
            boolean productionFlag = ctx.getInitParameter("penteLiveAPNSproductionFlag").equals("true");
            NotificationServer notificationServer = new CacheNotificationServer(new MySQLNotificationServer(dbHandler), penteLiveAPNSkey, penteLiveGCMkey, penteLiveAPNSpwd, productionFlag);
            resources.setNotificationServer(notificationServer);

            gameVenueStorer = new MySQLGameVenueStorer(dbHandler);
            resources.setGameVenueStorer(gameVenueStorer);
            ctx.setAttribute(GameVenueStorer.class.getName(), gameVenueStorer);
            log4j.info("contextInitialized(), created GameVenueStorer");

            dsgPlayerStorer = new CacheDSGPlayerStorer(new MySQLDSGPlayerStorer(dbHandler, gameVenueStorer), ctx, dbHandler);
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
            Boolean emailEnabled = new Boolean(ctx.getInitParameter("emailEnabled"));

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
                emailEnabled.booleanValue(),
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
            
            TourneyStorer tourneyStorer = new CacheTourneyStorer(
                new MySQLTourneyStorer(dbHandler, gameVenueStorer));
            resources.setTourneyStorer(tourneyStorer);
            tourneyStorer.addTourneyListener(tbGameStorer);
            ((CacheTourneyStorer) tourneyStorer).setTBStorer(tbGameStorer);
            ((CacheTourneyStorer) tourneyStorer).setDsgPlayerStorer(dsgPlayerStorer);
            ((CacheTourneyStorer) tourneyStorer).setNotificationServer(notificationServer);
            ((CacheTourneyStorer) tourneyStorer).setKothStorer(kothStorer);
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
            
            // start game servers
            try {
                List serverData = MySQLServerStorer.getActiveServers(
                    resources.getDbHandler(), resources.getGameVenueStorer());

                ServerContainer serverContainer = (ServerContainer) ctx.getAttribute("javax.websocket.server.ServerContainer");

                for (Iterator it = serverData.iterator(); it.hasNext();) {
                    ServerData data = (ServerData) it.next();
                    Server server;
                    if (data.isTournament()) {
                        server = new TournamentServer(resources, data);
                    } else {
                        server = new Server(resources, data);
                    }
                    resources.addServer(server);
                    log4j.info("Server " + data + " started.");
                    ServerEndpointConfig.Configurator configurator = new WebSocketConfigurator(server);
                    ServerEndpointConfig sec = ServerEndpointConfig.Builder.
                            create(WebSocketEndpoint.class, "/websocketServer/"+data.getPort()).
                            configurator(configurator).build();
                    serverContainer.addEndpoint(sec);
                }
                
                log4j.info("Servers ready.");
                
            } catch (Throwable t) {
                log4j.error("Problem creating servers.", t);
            }
            
        } catch (Throwable t) {
            log4j.error("Problem in contextInitialized()", t);
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
        
        for (Iterator it = resources.getServers().iterator(); it.hasNext();) {
            Server s = (Server) it.next();
            log4j.info("Destroying server " + s.getServerData() + ".");
            s.destroy(); 
        }
    }
}