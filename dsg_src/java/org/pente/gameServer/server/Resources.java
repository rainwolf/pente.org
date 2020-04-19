package org.pente.gameServer.server;


import java.util.*;
import org.pente.database.DBHandler;
import org.pente.game.*;
import org.pente.gameDatabase.GameStorerSearcher;
import org.pente.gameServer.client.web.SiteStatsData;
import org.pente.gameServer.core.*;
import org.pente.gameServer.event.WebSocketEndpoint;
import org.pente.gameServer.tourney.*;
import org.pente.notifications.NotificationServer;
import org.pente.turnBased.*;
import org.pente.message.*;

import org.pente.kingOfTheHill.*;

import javax.websocket.server.ServerContainer;
import javax.websocket.server.ServerEndpointConfig;

/** Holder of server side resources that are reused
 *  Created to avoid repeating a bunch of code that gets/sets things
 *  in the servlet context area (one Resources object will instead)
 */
public class Resources {

    private String appletVersion;
    private DBHandler dbHandler;
    private DBHandler dbHandlerRo;
    private GameVenueStorer gameVenueStorer;
    private CacheDSGPlayerStorer dsgPlayerStorer;
    private boolean emailEnabled;
    private GameStorer gameStorer;
    private GameStorer gameStorerRo;
    private GameStorer fileGameStorer;
    private PlayerStorer playerStorer;
    private GameStorerSearcher gameStorerSearcher;
    private boolean cacheResponses;
    private MySQLDSGReturnEmailStorer returnEmailStorer;
    private ServerStatsHandler serverStatsHandler;
    private SiteStatsData siteStatsData;
    private PasswordHelper passwordHelper;
    private ActivityLogger activityLogger;
    private String aiConfigFile;
    private TourneyStorer tourneyStorer;
    private TBGameStorer tbGameStorer;
    private DSGMessageStorer dsgMessageStorer;
    private FastMySQLDSGGameLookup dsgGameLookup;
    private CacheKOTHStorer kothStorer;
    private DSGFollowerStorer followerStorer;
    private NotificationServer notificationServer;

    private List<ServerData> serverData = new ArrayList<>();
    private List<Server> servers = new ArrayList<>();

    public void addServer(Server s) {
        servers.add(s);
        serverData.add(s.getServerData());
    }
    public void removeServer(long id) {
        Server server = null;
        for (Iterator it = servers.iterator(); it.hasNext();) {
            Server s = (Server) it.next();
            if (s.getServerData().getServerId() == id) {
                server = s;
                break;
            }
        }
        try {
            MySQLServerStorer.removeServer(getDbHandler(), server.getServerData().getServerId());
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
        servers.remove(server);
        serverData.remove(server.getServerData());
        server.destroy();
    }
    public void startNewServer(int tourneyID) {
        try {
            Tourney tournament = this.getTourneyStorer().getTourney(tourneyID);
            ServerContainer serverContainer = (ServerContainer) ContextHolder.servletContext.getAttribute("javax.websocket.server.ServerContainer");
            ServerData data = new ServerData();
            data.setTournament(true);
            data.setPort(getNewServerPort());
            data.setServerId(getNewServerID());
            data.setName(tournament.getName());
            GameEventData e = new SimpleGameEventData();
            e.setGame(tournament.getGame());
            e.setEventID(tournament.getEventID());
            e.setName(tournament.getName());
            data.addGameEvent(e);
            TournamentServer server = new TournamentServer(this, data);
            this.addServer(server);
            ServerEndpointConfig.Configurator configurator = new WebSocketConfigurator(server);
            ServerEndpointConfig sec = ServerEndpointConfig.Builder.
                    create(WebSocketEndpoint.class, "/websocketServer/"+data.getPort()).
                    configurator(configurator).build();
            serverContainer.addEndpoint(sec);
        } catch (Throwable t) {
            t.printStackTrace();
//            log4j.error("Problem in startNewServer()", t);
        }
    }

    public void startTournament(int tourneyID) {
        try {
            // create first round
            Tourney tournament = this.getTourneyStorer().getTourney(tourneyID);
            List players = this.getTourneyStorer().setInitialSeeds(tourneyID);
            TourneyRound newRound = tournament.createFirstRound(players);
            this.getTourneyStorer().insertRound(newRound);
        } catch (Throwable t) {
            t.printStackTrace();
//            log4j.error("Problem in startTournament()", t);
        }
    }
    private int getNewServerID() {
        int maxID = 0;
        for(Object o: this.getServers()) {
            Server s = (Server) o;
            maxID = Math.max(maxID, s.getServerData().getServerId());
        }
        return maxID + 1;
    }
    private int getNewServerPort() {
        int maxPort = 0;
        for(Object o: this.getServers()) {
            Server s = (Server) o;
            maxPort = Math.max(maxPort, s.getServerData().getPort());
        }
        return maxPort + 1;
    }

    public List getServers() {
        return servers;
    }
    public ServerData getServerData(int serverId) {
        for (Iterator it = serverData.iterator(); it.hasNext();) {
            ServerData d = (ServerData) it.next();
            if (d.getServerId() == serverId) {
                return d;
            }
        }
        return null;
    }
    public List getServerData() {
        return serverData;
    }

    public ActivityLogger getActivityLogger() {
        return activityLogger;
    }
    public void setActivityLogger(ActivityLogger activityLogger) {
        this.activityLogger = activityLogger;
    }
    public String getAppletVersion() {
        return appletVersion;
    }
    public void setAppletVersion(String appletVersion) {
        this.appletVersion = appletVersion;
    }
    public boolean isCacheResponses() {
        return cacheResponses;
    }
    public void setCacheResponses(boolean cacheResponses) {
        this.cacheResponses = cacheResponses;
    }
    public DBHandler getDbHandler() {
        return dbHandler;
    }
    public void setDbHandler(DBHandler dbHandler) {
        this.dbHandler = dbHandler;
    }
    public DBHandler getDbHandlerRo() {
        return dbHandlerRo;
    }
    public void setDbHandlerRo(DBHandler dbHandlerRo) {
        this.dbHandlerRo = dbHandlerRo;
    }
    public CacheDSGPlayerStorer getDsgPlayerStorer() {
        return dsgPlayerStorer;
    }
    public void setDsgPlayerStorer(CacheDSGPlayerStorer dsgPlayerStorer) {
        this.dsgPlayerStorer = dsgPlayerStorer;
    }
    public boolean isEmailEnabled() {
        return emailEnabled;
    }
    public void setEmailEnabled(boolean emailEnabled) {
        this.emailEnabled = emailEnabled;
    }
    public GameStorer getGameStorer() {
        return gameStorer;
    }
    public void setGameStorer(GameStorer gameStorer) {
        this.gameStorer = gameStorer;
    }
    public GameStorer getGameStorerRo() {
        return gameStorerRo;
    }
    public void setGameStorerRo(GameStorer gameStorerRo) {
        this.gameStorerRo = gameStorerRo;
    }
    public GameStorerSearcher getGameStorerSearcher() {
        return gameStorerSearcher;
    }
    public void setGameStorerSearcher(GameStorerSearcher gameStorerSearcher) {
        this.gameStorerSearcher = gameStorerSearcher;
    }
    public GameVenueStorer getGameVenueStorer() {
        return gameVenueStorer;
    }
    public void setGameVenueStorer(GameVenueStorer gameVenueStorer) {
        this.gameVenueStorer = gameVenueStorer;
    }
    public PasswordHelper getPasswordHelper() {
        return passwordHelper;
    }
    public void setPasswordHelper(PasswordHelper passwordHelper) {
        this.passwordHelper = passwordHelper;
    }
    public PlayerStorer getPlayerStorer() {
        return playerStorer;
    }
    public void setPlayerStorer(PlayerStorer playerStorer) {
        this.playerStorer = playerStorer;
    }
    public MySQLDSGReturnEmailStorer getReturnEmailStorer() {
        return returnEmailStorer;
    }
    public void setReturnEmailStorer(MySQLDSGReturnEmailStorer returnEmailStorer) {
        this.returnEmailStorer = returnEmailStorer;
    }
    public ServerStatsHandler getServerStatsHandler() {
        return serverStatsHandler;
    }
    public void setServerStatsHandler(ServerStatsHandler serverStatsHandler) {
        this.serverStatsHandler = serverStatsHandler;
    }
    public SiteStatsData getSiteStatsData() {
        return siteStatsData;
    }
    public void setSiteStatsData(SiteStatsData siteStatsData) {
        this.siteStatsData = siteStatsData;
    }
    public GameStorer getFileGameStorer() {
        return fileGameStorer;
    }
    public void setFileGameStorer(GameStorer fileGameStorer) {
        this.fileGameStorer = fileGameStorer;
    }
    public String getAiConfigFile() {
        return aiConfigFile;
    }
    public void setAiConfigFile(String aiConfigFile) {
        this.aiConfigFile = aiConfigFile;
    }
    public TourneyStorer getTourneyStorer() {
        return tourneyStorer;
    }
    public void setTourneyStorer(TourneyStorer tourneyStorer) {
        this.tourneyStorer = tourneyStorer;
    }
    public TBGameStorer getTbGameStorer() {
        return tbGameStorer;
    }
    public void setTbGameStorer(TBGameStorer tbGameStorer) {
        this.tbGameStorer = tbGameStorer;
    }
    public DSGMessageStorer getDsgMessageStorer() {
        return dsgMessageStorer;
    }
    public void setDsgMessageStorer(DSGMessageStorer dsgMessageStorer) {
        this.dsgMessageStorer = dsgMessageStorer;
    }
    public FastMySQLDSGGameLookup getDsgGameLookup() {
        return dsgGameLookup;
    }
    public void setDsgGameLookup(FastMySQLDSGGameLookup dsgGameLookup) {
        this.dsgGameLookup = dsgGameLookup;
    }

    public CacheKOTHStorer getKOTHStorer() {
        return this.kothStorer;
    }
    public void setKOTHStorer(CacheKOTHStorer kothStorer) {
        this.kothStorer = kothStorer;
    }

    public DSGFollowerStorer getFollowerStorer() { return followerStorer; }
    public void setFollowerStorer(DSGFollowerStorer followerStorer) { this.followerStorer = followerStorer; }

    public NotificationServer getNotificationServer() { return notificationServer; }
    public void setNotificationServer(NotificationServer notificationServer) { this.notificationServer = notificationServer; }

}
