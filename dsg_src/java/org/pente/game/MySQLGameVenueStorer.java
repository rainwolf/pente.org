/** MySQLGameVenueStorer.java
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

package org.pente.game;

import java.util.*;
import java.sql.*;

import org.apache.log4j.*;

import org.pente.database.*;

public class MySQLGameVenueStorer implements GameVenueStorer {

    private static Category log4j = Category.getInstance(
            MySQLGameVenueStorer.class.getName());
    
    public static final String GAME_SITE_TABLE = "game_site";
    public static final String GAME_EVENT_TABLE = "game_event";

    private DBHandler   dbHandler;
    private Vector      tree;

    /** The names of all tables, used to lock them at one time */
    protected static final Vector<String> ALL_TABLES = new Vector<String>();

    static {
        ALL_TABLES.addElement(MySQLPenteGameStorer.GAME_TABLE);
        ALL_TABLES.addElement(MySQLGameVenueStorer.GAME_SITE_TABLE);
        ALL_TABLES.addElement(MySQLGameVenueStorer.GAME_EVENT_TABLE);
    }

    public static void main(String args[]) throws Throwable {
        BasicConfigurator.configure();
        
        long startTime = System.currentTimeMillis();

        DBHandler dbHandler = new MySQLDBHandler(
            args[0], args[1], args[2], args[3]);
        MySQLGameVenueStorer storer = new MySQLGameVenueStorer(dbHandler);

        System.out.println("time = " + (System.currentTimeMillis() - startTime));
        
        GameSiteData s = storer.getGameSiteData(1, 1);
        GameEventData e = storer.getGameEventData(1, 1, s.getName());
        org.pente.gameDatabase.GameVenueJSFormat jsFormat =
            new org.pente.gameDatabase.GameVenueJSFormat();
        jsFormat.format(storer.getGameTree());
        //System.out.println(jsFormat.format(storer.getSiteTree()).toString());
    }

    public MySQLGameVenueStorer(DBHandler dbHandler) {

        this.dbHandler = dbHandler;

        tree = new Vector();

        // update the tree initially so calls to getSiteData, getEventData
        // dont' fail.  don't need site tree until getSiteTree() is called.
        try {
            updateGameTree(dbHandler);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /** Calling this can take awhile since the tree is completely regenerated */
    public Vector getGameTree() {

        try {
            updateGameTree(dbHandler);
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return tree;
    }

    class Data {
        int game;
        int siteId;
        GameSiteData siteData;
        int eventId;
        GameEventData eventData;
        String eventStr;
        String round;
        String section;
    }
        
    private void updateGameTree(DBHandler dbHandler) throws Exception {

        log4j.info("MySQLGameVenueStorer.updateGameTree() started");
        Connection con = null;
        PreparedStatement stmt = null;
        ResultSet result = null;

        Vector<GameTreeData> newTree = new Vector<GameTreeData>();

        try {
            con = dbHandler.getConnection();

            GameTreeData    lastGame = null;
            GameSiteData    lastSite = null;
            GameEventData   lastEvent = null;
            GameRoundData   lastRound = null;
            
            // get all combinations of games+sites+events+rounds+sections
            // uses index on pente_game, pretty quick
            stmt = con.prepareStatement(
                "select distinct game, site_id, event_id, round, section " +
                "from pente_game " +
                "order by game, site_id, event_id, round, section");
            result = stmt.executeQuery();
            List<Data> data = new ArrayList<Data>(3000);
            while (result.next()) {
                Data d = new Data();
                d.game = result.getInt(1);
                d.siteId = result.getInt(2);
                d.eventId = result.getInt(3);
                d.round = result.getString(4);
                d.section = result.getString(5);
                data.add(d);
            }
            result.close();
            stmt.close();
            
            // get all unique site data
            stmt = con.prepareStatement(
                "select sid, name, short_name, URL from game_site");
            List<GameSiteData> siteData = new ArrayList<GameSiteData>(5);
            result = stmt.executeQuery();
            while (result.next()) {
                GameSiteData s = new SimpleGameSiteData();
                s.setSiteID(result.getInt(1));
                s.setName(result.getString(2));
                s.setShortSite(result.getString(3));
                s.setURL(result.getString(4));
                siteData.add(s);
            }
            result.close();
            stmt.close();
            
            stmt = con.prepareStatement("select eid, name, game from game_event " +
                "order by eid");
            
            
            long start = System.currentTimeMillis();
            
            // get all events in order of eid
            Map<Integer, GameEventData> eventData = new HashMap<Integer, GameEventData>(400);
            result = stmt.executeQuery();
            while (result.next()) {
                GameEventData e = new SimpleGameEventData();
                e.setEventID(result.getInt(1));
                e.setName(result.getString(2));
                e.setGame(result.getInt(3));
                eventData.put(e.getEventID(), e);
            }
            
            
            GameEventData ced = null;
            int ceid = -1;
            
            // associate sitedata, eventdata with data
            outer2: for (Iterator<Data> it = data.iterator(); it.hasNext();) {
                long startTime = System.currentTimeMillis();
                Data d = it.next();
                // associate sitedata
                outer : for (GameSiteData s : siteData) {
                    if (d.siteId == s.getSiteID()) {
                        d.siteData = s;
                        break outer;
                    }
                }
                //site not found
                if (d.siteData == null) {
                    it.remove();
                    continue outer2;
                }
                
                if (ceid == d.eventId) {
                    d.eventData = ced;
                }
                else {
                    ceid = d.eventId;
                    ced = eventData.get(ceid);
                    d.eventData = ced;
                    if (ced == null) {
                        it.remove();
                        ceid = -1;
                        continue outer2;
                    }
                }
                
                
//              if (ceid == d.eventId) {
//                  d.eventData = ced;
//              }
//              else {
//                  ceid = d.eventId;
//                  // get event name from db, associate
//                  stmt.setInt(1, d.eventId);
//                  result = stmt.executeQuery();
//                  if (result.next()) {
//                      d.eventData = new SimpleGameEventData();
//                      d.eventData.setEventID(d.eventId);
//                      d.eventData.setName(result.getString(1));
//                      d.eventData.setGame(d.game);
//                      ced = d.eventData;
//                  }
//                  else {
//                      it.remove();
//                      ceid = -1;
//                  }
//                  result.close();
//              }
            }
            log4j.debug("get eid time=" + (System.currentTimeMillis() - start));
            
            start = System.currentTimeMillis();
            // sort by game, site name, event name, round, section
            Collections.sort(data, new Comparator<Data>() {
                public int compare(Data d1, Data d2) {
                    if (d1.game != d2.game) {
                        return d1.game - d2.game;
                    }
                    else {
                        if (d1.siteData != d2.siteData) {
                            return d1.siteData.getName().compareTo(
                                d2.siteData.getName());
                        }
                        else {
                            if (d1.eventData != d2.eventData) {
                                return d1.eventData.getName().compareTo(
                                    d2.eventData.getName());
                            }
                            else {
                                if (d1.round == null) {
                                    if (d2.round == null) return 1;
                                    else return 0;
                                } else if (d2.round == null) {
                                    return 1;
                                }
                                else if (!d1.round.equals(d2.round)) {
                                    return d1.round.compareTo(d2.round);
                                }
                                else {
                                    if (d1.section == null) {
                                        if (d2.section == null) return 1;
                                        else return 0;
                                    }
                                    else {
                                        return d1.section.compareTo(d2.section);
                                    }
                                }
                            }
                        }
                    }
                }
            });

            log4j.debug("sort time=" + (System.currentTimeMillis() - start));
            
            
/* old way          
            stmt = con.prepareStatement(
                "select distinct game_event.game, game_site.sid, " +
                "game_site.name, game_site.short_name, game_site.URL, " +
                "game_event.eid, game_event.name, pente_game.round, " +
                "pente_game.section " +
                "from game_site, game_event, pente_game " +
                "where game_site.sid = pente_game.site_id " +
                "and game_site.sid = game_event.site_id " +
                "and game_event.eid = pente_game.event_id " +
                "and game_event.game = pente_game.game " +
                "order by game_event.game, game_site.name, game_event.name, " +
                "pente_game.round, pente_game.section");


            result = stmt.executeQuery();

            while (result.next()) {
*/
            for (Data d : data) {

                int game = d.game;
                if (lastGame == null || game != lastGame.getID()) {
                    GameTreeData newGame = new SimpleGameTreeData();
                    newGame.setID(game);
                    newGame.setName(GridStateFactory.getGameName(game));

                    newTree.addElement(newGame);
                    lastGame = newGame;
                    lastSite = null;
                    lastEvent = null;
                    lastRound = null;
                }
                
                int sid = d.siteId;
                if (lastSite == null || sid != lastSite.getSiteID()) {

                    GameSiteData sd = (GameSiteData) d.siteData.clone();
                    lastGame.addGameSiteData(sd);
                    lastSite = sd;
                    lastEvent = null;
                    lastRound = null;
                }

                int eid = d.eventId;
                if (lastEvent == null || eid != lastEvent.getEventID()) {

                    lastSite.addGameEventData(d.eventData);
                    lastEvent = d.eventData;
                    lastRound = null;
                }

                String round = d.round;
                if (round != null && (lastRound == null || !round.equals(lastRound.getName()))) {

                    GameRoundData newRound = new SimpleGameRoundData(round);

                    lastEvent.addGameRoundData(newRound);
                    lastRound = newRound;
                }

                String section = d.section;
                if (section != null) {
                    lastRound.addGameSectionData(new SimpleGameSectionData(section));
                }
                
                log4j.debug("Loaded game=" + game + " site=" + sid + " event=" +
                    eid + " round=" + round + " section=" + section);
            }

        } finally {

            if (result != null) {
                result.close();
            }
            if (stmt != null) {
                stmt.close();
            }
            if (con != null) {
                dbHandler.freeConnection(con);
            }
        }

        synchronized (tree) {
            tree = newTree;
        }
        
        log4j.info("MySQLGameVenueStorer.updateGameTree() done");
    }

    public int getSiteID(String name) {
        
        synchronized (tree) {

            for (int i = 0; i < tree.size(); i++) {
                GameTreeData gameTreeData = (GameTreeData) tree.get(i);
                for (Iterator sites = gameTreeData.getGameSiteData().iterator(); sites.hasNext();) {
                    GameSiteData gameSiteData = (GameSiteData) sites.next();
                    if (gameSiteData.getName().equals(name)) {
                        return gameSiteData.getSiteID();
                    }
                }
            }
        }

        return -1;
    }

    public GameSiteData getGameSiteData(int game, int sid) {

        synchronized (tree) {

            GameTreeData gameTreeData;
            if (game > 50) {
                gameTreeData = (GameTreeData) tree.get(game- 1 - 50);
            } else {
                gameTreeData = (GameTreeData) tree.get(game- 1);
            }
            if (gameTreeData == null) {
                return null;
            }
            for (Iterator sites = gameTreeData.getGameSiteData().iterator(); sites.hasNext();) {
                GameSiteData gameSiteData = (GameSiteData) sites.next();
                if (gameSiteData.getSiteID() == sid) {
                    return gameSiteData;
                }
            }
        }

        return null;
    }

    public GameSiteData getGameSiteData(int game, String name) {

        synchronized (tree) {
            GameTreeData gameTreeData;
            if (game > 50) {
                gameTreeData = (GameTreeData) tree.get(game- 1 - 50);
            } else {
                gameTreeData = (GameTreeData) tree.get(game- 1);
            }
            if (gameTreeData == null) {
                return null;
            }
            for (Iterator sites = gameTreeData.getGameSiteData().iterator(); sites.hasNext();) {
                GameSiteData gameSiteData = (GameSiteData) sites.next();
                if (gameSiteData.getName().equals(name)) {
                    return gameSiteData;
                }
            }
        }

        return null;
    }

    public void addGameSiteData(int game, GameSiteData gameSiteData)
        throws Exception {

        Connection con = null;
        PreparedStatement stmt = null;
        ResultSet result = null;

        try {

            // insert site info into db
            stmt = con.prepareStatement("insert into " + GAME_SITE_TABLE + " " +
                                        "(name, short_name, URL) " +
                                        "values(?, ?, ?)");

            stmt.setString(1, gameSiteData.getName());
            stmt.setString(2, gameSiteData.getShortSite());
            stmt.setString(3, gameSiteData.getURL());
            stmt.executeUpdate();

            // get site id for new site
            if (stmt != null) {
                stmt.close();
            }
            stmt = con.prepareStatement("select sid " +
                                        "from " + GAME_SITE_TABLE + " " +
                                        "where name = ?");
            stmt.setString(1, gameSiteData.getName());
            result = stmt.executeQuery();
            if (result.next()) {
                gameSiteData.setSiteID(result.getInt(1));
            }

            // add site to data tree
            synchronized (tree) {
                GameTreeData gameTreeData = (GameTreeData) tree.get(game);
                if (gameTreeData != null) {
                    gameTreeData.addGameSiteData(gameSiteData);
                }
            }

        } finally {
            if (result != null) { try { result.close(); } catch(SQLException ex) {} }
            if (stmt != null) { try { stmt.close(); } catch(SQLException ex) {} }
            if (con != null) { try { dbHandler.freeConnection(con); } catch (Exception ex) {} }
        }
    }

    public GameEventData getGameEventData(int game, int eid, String site) {

        GameSiteData gameSiteData = getGameSiteData(game, site);
        if (gameSiteData == null) {
            return null;
        }

        List<GameEventData> events = gameSiteData.getGameEventData();
        synchronized (events) {
            for (int j = 0; j < events.size(); j++) {
                GameEventData gameEventData = (GameEventData) events.get(j);
                if (gameEventData.getEventID() == eid) {
                    return gameEventData;
                }
            }
        }

        return null;
    }

    public GameEventData getGameEventData(int game, String eventName, String site) {

        GameSiteData gameSiteData = getGameSiteData(game, site);
        if (gameSiteData == null) {
            return null;
        }

        List<GameEventData> events = gameSiteData.getGameEventData();
        synchronized (events) {
            for (int j = 0; j < events.size(); j++) {
                GameEventData gameEventData = (GameEventData) events.get(j);
                if (gameEventData.getName().equals(eventName)) {
                    return gameEventData;
                }
            }
        }

        return null;
    }

    public void addGameEventData(int game, 
        GameEventData gameEventData, String site) throws Exception {

        Connection con = null;
        PreparedStatement stmt = null;
        ResultSet result = null;

        try {
            // assume the site data exists
            GameSiteData siteData = getGameSiteData(game, site);

            // insert event info into db
            con = dbHandler.getConnection();
            stmt = con.prepareStatement("insert into " + GAME_EVENT_TABLE + " " +
                                        "(name, site_id, game) " +
                                        "values(?, ?, ?)");

            stmt.setString(1, gameEventData.getName());
            stmt.setInt(2, siteData.getSiteID());
            stmt.setInt(3, game);
            stmt.executeUpdate();

            // get event id for new event
            if (stmt != null) {
                stmt.close();
            }
            stmt = con.prepareStatement("select eid " +
                                        "from " + GAME_EVENT_TABLE + " " +
                                        "where name = ? " +
                                        "and site_id = ? " +
                                        "and game = ?");
            stmt.setString(1, gameEventData.getName());
            stmt.setInt(2, siteData.getSiteID());
            stmt.setInt(3, game);
            result = stmt.executeQuery();
            if (result.next()) {
                gameEventData.setEventID(result.getInt(1));
            }

            // add event to data tree
            if (siteData != null) {
                siteData.addGameEventData(gameEventData);
            }

        } finally {
            if (result != null) { try { result.close(); } catch(SQLException ex) {} }
            if (stmt != null) { try { stmt.close(); } catch(SQLException ex) {} }
            if (con != null) { try { dbHandler.freeConnection(con); } catch (Exception ex) {} }
        }
    }
}