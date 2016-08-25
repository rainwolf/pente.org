/** MySQLGameStorerSearcher.java
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

package org.pente.gameServer.core;

import java.io.*;
import java.sql.*;
import java.util.*;

import org.pente.database.*;
import org.pente.game.*;

import org.apache.log4j.*;

public class FastMySQLDSGGameLookup {

    private static final Category log4j = Category.getInstance(
        FastMySQLDSGGameLookup.class.getName());

    private DBHandler           dbHandler;
    private GameVenueStorer     gameVenueStorer;

    public FastMySQLDSGGameLookup(DBHandler dbHandler, GameVenueStorer gameVenueStorer) {

        this.dbHandler = dbHandler;
        this.gameVenueStorer = gameVenueStorer;
    }

    
    public int count(long pid, int game) throws Throwable {
        Connection con = null;
        PreparedStatement stmt = null;
        ResultSet results = null;

        int count = 0;
        try {
            con = dbHandler.getConnection();

            int actualGame = GridStateFactory.isTurnbasedGame(game) ?
                GridStateFactory.getNormalGameFromTurnbased(game) : game;
            GameEventData e = gameVenueStorer.getGameEventData(actualGame, 
                "Turn-based Game", "Pente.org");
            int tbEventId = (e == null) ? 0 : e.getEventID();
            boolean tb = GridStateFactory.isTurnbasedGame(game);
            String qryString = 
                "select count(*) " +
                "from pente_game " +
                "where game = ? " +
                "and player1_pid = ? " +
                "and gid " + (tb?">=":"<") + " 50000000000000 " +
                "union all " +
                "select count(*) " +
                "from pente_game " +
                "where game = ? " +
                "and player2_pid = ? " +
                "and gid " + (tb?">=":"<") + " 50000000000000 ";
            log4j.debug(qryString);

            stmt = con.prepareStatement(qryString);
            stmt.setInt(1, actualGame);
            stmt.setLong(2, pid);
//            stmt.setInt(3, tbEventId);
            stmt.setInt(3, actualGame);
            stmt.setLong(4, pid);
//            stmt.setInt(6, tbEventId);
            
            results = stmt.executeQuery();
            while (results.next()) {
                count += results.getInt(1);
            }

        } finally {
            dbHandler.freeConnection(con);
        }
        
        return count;
    }

    public int totalGames(long pid, long myPid, int game) throws Throwable {
        Connection con = null;
        PreparedStatement stmt = null;
        ResultSet results = null;

        int count = 0;
        try {
            con = dbHandler.getConnection();

            int actualGame = GridStateFactory.isTurnbasedGame(game) ?
                GridStateFactory.getNormalGameFromTurnbased(game) : game;
            GameEventData e = gameVenueStorer.getGameEventData(actualGame, 
                "Turn-based Game", "Pente.org");
            int tbEventId = (e == null) ? 0 : e.getEventID();
            boolean tb = GridStateFactory.isTurnbasedGame(game);
            String qryString = 
                "select count(*) " +
                "from pente_game " +
                "where game = ? " +
                "and player1_pid = ? " +
                "and player2_pid = ? " +
                "and gid " + (tb?">=":"<") + " 50000000000000 " +
                "union all " +
                "select count(*) " +
                "from pente_game " +
                "where game = ? " +
                "and player2_pid = ? " +
                "and player1_pid = ? " +
                "and gid " + (tb?">=":"<") + " 50000000000000 ";
            log4j.debug(qryString);

            stmt = con.prepareStatement(qryString);
            stmt.setInt(1, actualGame);
            stmt.setLong(2, pid);
            stmt.setLong(3, myPid);
//            stmt.setInt(4, tbEventId);
            stmt.setInt(4, actualGame);
            stmt.setLong(5, pid);
            stmt.setLong(6, myPid);
//            stmt.setInt(8, tbEventId);
            
            results = stmt.executeQuery();
            while (results.next()) {
                count += results.getInt(1);
            }

        } finally {
            dbHandler.freeConnection(con);
        }
        
        return count;
    }

    public int totalWins(long pid, long myPid, int game) throws Throwable {
        Connection con = null;
        PreparedStatement stmt = null;
        ResultSet results = null;

        int count = 0;
        try {
            con = dbHandler.getConnection();

            int actualGame = GridStateFactory.isTurnbasedGame(game) ?
                GridStateFactory.getNormalGameFromTurnbased(game) : game;
            GameEventData e = gameVenueStorer.getGameEventData(actualGame, 
                "Turn-based Game", "Pente.org");
            int tbEventId = (e == null) ? 0 : e.getEventID();
            boolean tb = GridStateFactory.isTurnbasedGame(game);
            String qryString = 
                "select count(*) " +
                "from pente_game " +
                "where game = ? " +
                "and player1_pid = ? " +
                "and player2_pid = ? " +
                "and winner = 2 " +
                "and gid " + (tb?">=":"<") + " 50000000000000 " +
                "union all " +
                "select count(*) " +
                "from pente_game " +
                "where game = ? " +
                "and player2_pid = ? " +
                "and player1_pid = ? " +
                "and winner = 1 " +
                "and gid " + (tb?">=":"<") + " 50000000000000 ";
            log4j.debug(qryString);

            stmt = con.prepareStatement(qryString);
            stmt.setInt(1, actualGame);
            stmt.setLong(2, pid);
            stmt.setLong(3, myPid);
//            stmt.setInt(4, tbEventId);
            stmt.setInt(4, actualGame);
            stmt.setLong(5, pid);
            stmt.setLong(6, myPid);
//            stmt.setInt(8, tbEventId);
            
            results = stmt.executeQuery();
            while (results.next()) {
                count += results.getInt(1);
            }

        } finally {
            dbHandler.freeConnection(con);
        }
        
        return count;
    }

    public List<GameData> search(String lookupName, long lookupPid, int lookupColor,
        long requestorPid, int game, int start, int len)
        throws Throwable {

        long startTime = System.currentTimeMillis();
        
        List<GameData> games = new ArrayList<GameData>();
        Connection con = null;
        PreparedStatement stmt = null;
        ResultSet results = null;

        try {
            con = dbHandler.getConnection();

            int actualGame = GridStateFactory.isTurnbasedGame(game) ?
                GridStateFactory.getNormalGameFromTurnbased(game) : game;
            GameEventData e = gameVenueStorer.getGameEventData(actualGame, 
                "Turn-based Game", "Pente.org");
            int tbEventId = (e == null) ? 0 : e.getEventID();
            boolean tb = GridStateFactory.isTurnbasedGame(game);
            
            String qryString = 
                "select g.gid, g.event_id, g.round, g.section, g.play_date, " +
                "g.rated, g.winner, 1, p.name, p.pid, g.player1_rating, " +
                "g.player2_rating, g.timer, g.initial_time, g.incremental_time, dp.name_color " +
                "from pente_game g, player p, dsg_player dp " +
                "where g.game = ? " +
                "and g.player1_pid = ? " +
                "and g.player2_pid = p.pid " +
                "and g.player2_pid = dp.pid " +
                "and gid " + (tb?">=":"<") + " 50000000000000 " +
                "and (g.private = 'N' or " +
                "     g.player1_pid = ? or g.player2_pid = ?) " +
                "union " +
                "select g.gid, g.event_id, g.round, g.section, g.play_date, " +
                "g.rated, g.winner, 2, p.name, p.pid, g.player1_rating, " +
                "g.player2_rating, g.timer, g.initial_time, g.incremental_time, dp.name_color " +
                "from pente_game g, player p, dsg_player dp " +
                "where g.game = ? " +
                "and g.player2_pid = ? " +
                "and g.player1_pid = p.pid " +
                "and g.player1_pid = dp.pid " +
                "and gid " + (tb?">=":"<") + " 50000000000000 " +
                "and (g.private = 'N' or " +
                "     g.player1_pid = ? or g.player2_pid = ?) " +
                "order by play_date desc " +
                "limit " + start + ", " + len;
            log4j.debug(qryString);

            stmt = con.prepareStatement(qryString);
            stmt.setInt(1, actualGame);
            stmt.setLong(2, lookupPid);
//            stmt.setInt(3, tbEventId);
            stmt.setLong(3, requestorPid);
            stmt.setLong(4, requestorPid);
            stmt.setInt(5, actualGame);
            stmt.setLong(6, lookupPid);
//            stmt.setInt(8, tbEventId);
            stmt.setLong(7, requestorPid);
            stmt.setLong(8, requestorPid);
            

            //log4j.debug("start getting game data");
            results = stmt.executeQuery();
            while (results.next()) {
                GameData d = new DefaultGameData();
                d.setGameID(results.getLong(1));
                //log4j.debug("start loading game " + d.getGameID());
                int eventId = results.getInt(2);
                GameEventData eventData = gameVenueStorer.getGameEventData(
                    actualGame, eventId, "Pente.org");
                d.setEvent(eventData.getName());
                
                d.setRound(results.getString(3));
                d.setSection(results.getString(4));
                d.setDate(new java.util.Date(results.getTimestamp(5).getTime()));
                d.setRated(results.getString(6).equals("Y"));
                d.setWinner(results.getInt(7));
                
                int seat = results.getInt(8);
                PlayerData p1 = new DefaultPlayerData();
                PlayerData p2 = new DefaultPlayerData();
                if (seat == 1) {
                    p1.setUserIDName(lookupName);
                    p1.setUserID(lookupPid);
                    p1.setNameColor(lookupColor);
                    p2.setUserIDName(results.getString(9));
                    p2.setUserID(results.getLong(10));
                    p2.setNameColor(results.getInt(16));
                }
                else {
                    p1.setUserIDName(results.getString(9));
                    p1.setUserID(results.getLong(10));
                    p1.setNameColor(results.getInt(16));
                    p2.setUserIDName(lookupName);
                    p2.setUserID(lookupPid);
                    p2.setNameColor(lookupColor);
                }
                p1.setRating(results.getInt(11));
                p2.setRating(results.getInt(12));
                d.setPlayer1Data(p1);
                d.setPlayer2Data(p2);
                d.setTimed(!results.getString(13).equals("N"));
                d.setInitialTime(results.getInt(14));
                d.setIncrementalTime(results.getInt(15));

                games.add(d);
                //log4j.debug("done");
            }

        } finally {
            dbHandler.freeConnection(con);
        }

        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;
        log4j.debug("search time: " + totalTime);
        
        return games;
    }
}