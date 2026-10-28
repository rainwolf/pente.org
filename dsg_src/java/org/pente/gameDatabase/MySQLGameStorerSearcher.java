/**
 * MySQLGameStorerSearcher.java
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

package org.pente.gameDatabase;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.pente.database.*;
import org.pente.game.*;

import org.apache.log4j.*;

public class MySQLGameStorerSearcher implements GameStorerSearcher {

    private static final Category log4j = Category.getInstance(
            MySQLGameStorerSearcher.class.getName());

    /** What the next-moves (stats) query produced; applied to the response by search(). */
    private static final class StatsResult {
        volatile boolean rotationSet;
        volatile int rotation;
        volatile int total;
        volatile List<GameStorerSearchResponseMoveData> moves = new ArrayList<>();
    }

    private DBHandler dbHandler;
    private GameStorer gameStorer;
    private GameVenueStorer gameVenueStorer;

    private String game_table = "pente_game";
    private String move_table = "pente_move";

    public MySQLGameStorerSearcher(DBHandler dbHandler, GameStorer gameStorer, GameVenueStorer gameVenueStorer) throws Exception {

        this.dbHandler = dbHandler;
        this.gameStorer = gameStorer;
        this.gameVenueStorer = gameVenueStorer;
    }


    public void search(GameStorerSearchRequestData requestData, GameStorerSearchResponseData responseData) throws Exception {
        long startTime = System.currentTimeMillis();

        responseData.setGameStorerSearchRequestData(requestData);

        // setup filter sql lines
        StringBuffer filterOptionsFrom = new StringBuffer();
        StringBuffer filterOptionsWhere = new StringBuffer();
        Vector<Object> filterOptionsParams = new Vector<>();
        StringBuffer filterOptionsFrom2 = new StringBuffer();
        StringBuffer filterOptionsWhere2 = new StringBuffer();
        Vector<Object> filterOptionsParams2 = new Vector<>();

        GameStorerSearchRequestFilterData filterData = requestData.getGameStorerSearchRequestFilterData();

        boolean includeGameTable = initFilterOptions(
                filterData, 1, filterOptionsFrom, filterOptionsWhere, filterOptionsParams);

        boolean union = false;
        if ((filterData.getPlayer1Name() != null &&
                !filterData.getPlayer1Name().trim().isEmpty() &&
                filterData.getPlayer1Seat() == GameStorerSearchRequestFilterData.SEAT_ALL) ||
                (filterData.getPlayer2Name() != null &&
                        !filterData.getPlayer2Name().trim().isEmpty() &&
                        filterData.getPlayer2Seat() == GameStorerSearchRequestFilterData.SEAT_ALL)) {
            union = true;
            includeGameTable = true;
            initFilterOptions(
                    filterData, 2, filterOptionsFrom2, filterOptionsWhere2, filterOptionsParams2);
        }

        try {

            int totalGameCount = 0;

            StringBuffer filterOptionsWhereTmp = new StringBuffer();
            filterOptionsWhereTmp.append(filterOptionsWhere);
            filterOptionsWhereTmp.append("and g.private = 'N' ");
            // getMatchingGames appends the game table to the FROM it is given when the
            // filters did not already include it; it must not leak into the stats query
            // that runs concurrently, so the matching task gets its own copy.
            StringBuffer filterOptionsFromMatching = new StringBuffer(filterOptionsFrom);

            boolean finalIncludeGameTable = includeGameTable;
            boolean finalUnion = union;

            // The two queries are independent and each runs on its own pooled
            // connection (borrowed inside the task) so they overlap on the network:
            // submit both before joining either.
            StatsResult stats = new StatsResult();
            CompletableFuture<Integer> statsFuture = null;
            if (filterData.doGetNextMoves()) {
                statsFuture = RoReads.async(() -> {
                    try {
                        return RoReads.withConnection(dbHandler, c ->
                                getSearchResults(requestData, stats, filterOptionsFrom,
                                        finalIncludeGameTable, filterOptionsWhere, filterOptionsParams,
                                        filterOptionsFrom2, filterOptionsWhere2, filterOptionsParams2,
                                        finalUnion, c));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    return 0;
                });
            }
            CompletableFuture<List<GameData>> gamesFuture = RoReads.async(() -> {
                try {
                    return RoReads.withConnection(dbHandler, c ->
                            getMatchingGames(requestData, filterOptionsFromMatching,
                                    finalIncludeGameTable, filterOptionsWhereTmp,
                                    filterOptionsParams, filterOptionsFrom2, filterOptionsWhere2,
                                    filterOptionsParams2, finalUnion, c));
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return new ArrayList<GameData>();
            });

            // results are applied here, on the calling thread, in the former order
            if (statsFuture != null) {
                totalGameCount = RoReads.join(statsFuture);
                if (stats.rotationSet) {
                    responseData.setRotation(stats.rotation);
                }
                for (GameStorerSearchResponseMoveData moveData : stats.moves) {
                    responseData.addSearchResponseMoveData(moveData);
                }
            }
            for (GameData game : RoReads.join(gamesFuture)) {
                responseData.addGame(game);
            }

            requestData.getGameStorerSearchRequestFilterData().setTotalGameNum(
                    totalGameCount);
            log4j.debug("total matched games = " + requestData.getGameStorerSearchRequestFilterData().getTotalGameNum());

        } finally {
            // nothing to release: each task borrows and frees its own connection
        }
        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;
        log4j.debug("search time: " + totalTime);
    }

    private void addGameTable(StringBuffer filterOptionsFrom,
                              StringBuffer filterOptionsWhere) {

        filterOptionsFrom.append(", " + game_table + " g ");
        filterOptionsWhere.append("and m.gid = g.gid ");
    }

    protected boolean initFilterOptions(GameStorerSearchRequestFilterData filterData,
                                        int unionIndex,
                                        StringBuffer filterOptionsFrom,
                                        StringBuffer filterOptionsWhere,
                                        Vector<Object> filterOptionsParams) throws Exception {

        boolean includeGameTable = false;

        GameSiteData siteData = null;
        if (filterData.getSite() != null && !filterData.getSite().trim().isEmpty()) {

            siteData = gameVenueStorer.getGameSiteData(
                    filterData.getGame(), filterData.getSite());
            if (siteData != null) {
                addGameTable(filterOptionsFrom, filterOptionsWhere);
                includeGameTable = true;
                filterOptionsWhere.append("and g.site_id = ? ");
                filterOptionsParams.addElement(Integer.valueOf(siteData.getSiteID()));
            }
        }
        if (filterData.getEvent() != null && !filterData.getEvent().trim().isEmpty() &&
                !filterData.getEvent().equals(GameEventData.ALL_EVENTS) && !filterData.getEvent().equals("-")) {

            if (!includeGameTable) {
                addGameTable(filterOptionsFrom, filterOptionsWhere);
                includeGameTable = true;
            }

            filterOptionsWhere.append(" and g.event_id = ? ");
            GameEventData e = gameVenueStorer.getGameEventData(filterData.getGame(),
                    filterData.getEvent(), filterData.getSite());
            filterOptionsParams.addElement(e.getEventID());
        }
        if (filterData.getRound() != null && !filterData.getRound().trim().isEmpty() &&
                !filterData.getRound().equals(GameRoundData.ALL_ROUNDS) && !filterData.getRound().equals("-")) {

            if (!includeGameTable) {
                addGameTable(filterOptionsFrom, filterOptionsWhere);
                includeGameTable = true;
            }
            filterOptionsWhere.append("and g.round = ? ");
            filterOptionsParams.addElement(filterData.getRound());
        }
        if (filterData.getSection() != null && !filterData.getSection().trim().isEmpty() &&
                !filterData.getSection().equals(GameSectionData.ALL_SECTIONS) && !filterData.getSection().equals("-")) {

            if (!includeGameTable) {
                addGameTable(filterOptionsFrom, filterOptionsWhere);
                includeGameTable = true;
            }
            filterOptionsWhere.append("and g.section = ? ");
            filterOptionsParams.addElement(filterData.getSection());
        }

        boolean createOrPlayers = filterData.isP1OrP2() &&
                (filterData.getPlayer1Name() != null && !filterData.getPlayer1Name().trim().isEmpty()) &&
                (filterData.getPlayer2Name() != null && !filterData.getPlayer2Name().trim().isEmpty());

        if (filterData.getPlayer1Name() != null && !filterData.getPlayer1Name().trim().isEmpty()) {
            filterOptionsFrom.append(", player p1 ");

            String pStr = filterData.getPlayer1Name().toLowerCase();

            if (!includeGameTable) {
                addGameTable(filterOptionsFrom, filterOptionsWhere);
                includeGameTable = true;
            }
            if (filterData.getPlayer1Seat() == GameStorerSearchRequestFilterData.SEAT_ALL) {
                filterOptionsWhere.append("and g.player" + unionIndex + "_pid = p1.pid ");
            } else {
                filterOptionsWhere.append("and g.player" + filterData.getPlayer1Seat() + "_pid = p1.pid ");
            }

            if (siteData != null) {
                filterOptionsWhere.append("and p1.site_id = " + siteData.getSiteID() + " ");
            }

            //filterOptionsWhere.append("and p1.name_lower = '" +
            //	filterData.getPlayer1Name().toLowerCase() + "' ");

            if (!createOrPlayers) {
                String[] pStrArray = pStr.split(",");
                filterOptionsWhere.append("and (");
                boolean fst = true;
                for (String s : pStrArray) {
                    if (fst) {
                        fst = false;
                    } else {
                        filterOptionsWhere.append("or  ");
                    }
                    String strimmed = s.trim();
                    if (strimmed.contains("*")) {
                        filterOptionsWhere.append("p1.name_lower like ?  ");
                        filterOptionsParams.addElement(strimmed.replace("*", "%"));
                    } else {
                        filterOptionsWhere.append("p1.name_lower = ?  ");
                        filterOptionsParams.addElement(strimmed);
                    }
                }
                filterOptionsWhere.append(") ");
            }
        }
        if (filterData.getPlayer2Name() != null && !filterData.getPlayer2Name().trim().isEmpty()) {
            filterOptionsFrom.append(", player p2 ");

            String pStr = filterData.getPlayer2Name().toLowerCase();

            if (!includeGameTable) {
                addGameTable(filterOptionsFrom, filterOptionsWhere);
                includeGameTable = true;
            }
            if (filterData.getPlayer1Seat() == GameStorerSearchRequestFilterData.SEAT_ALL) {
                filterOptionsWhere.append("and g.player" + (3 - unionIndex) + "_pid = p2.pid ");
            } else {
                filterOptionsWhere.append("and g.player" + filterData.getPlayer2Seat() + "_pid = p2.pid ");
            }

            if (siteData != null) {
                filterOptionsWhere.append("and p2.site_id = " + siteData.getSiteID() + " ");
            }

            //filterOptionsWhere.append("and p2.name_lower = '" + 
            //	filterData.getPlayer2Name().toLowerCase() + "' ");

            if (!createOrPlayers) {
                String[] pStrArray = pStr.split(",");
                filterOptionsWhere.append("and (");
                boolean fst = true;
                for (String s : pStrArray) {
                    if (fst) {
                        fst = false;
                    } else {
                        filterOptionsWhere.append("or  ");
                    }
                    String strimmed = s.trim();
                    if (strimmed.contains("*")) {
                        filterOptionsWhere.append("p2.name_lower like ?  ");
                        filterOptionsParams.addElement(strimmed.replace("*", "%"));
                    } else {
                        filterOptionsWhere.append("p2.name_lower = ?  ");
                        filterOptionsParams.addElement(strimmed);
                    }
                }
                filterOptionsWhere.append(") ");
            }
        }
        if (createOrPlayers) {
            String p1Str = filterData.getPlayer1Name().toLowerCase();
            String[] p1StrArray = p1Str.split(",");
            filterOptionsWhere.append("and (");
            boolean fst = true;
            for (String s : p1StrArray) {
                if (fst) {
                    fst = false;
                } else {
                    filterOptionsWhere.append("or  ");
                }
                String sTrimmed = s.trim();
                if (sTrimmed.contains("*")) {
                    filterOptionsWhere.append("p1.name_lower like ?  ");
                    filterOptionsParams.addElement(sTrimmed.replace("*", "%"));
                } else {
                    filterOptionsWhere.append("p1.name_lower = ?  ");
                    filterOptionsParams.addElement(sTrimmed);
                }
            }
            String p2Str = filterData.getPlayer2Name().toLowerCase();
            String[] p2StrArray = p2Str.split(",");
            for (String s : p2StrArray) {
                filterOptionsWhere.append("or  ");
                String sTrimmed = s.trim();
                if (sTrimmed.contains("*")) {
                    filterOptionsWhere.append("p2.name_lower like ?  ");
                    filterOptionsParams.addElement(sTrimmed.replace("*", "%"));
                } else {
                    filterOptionsWhere.append("p2.name_lower = ?  ");
                    filterOptionsParams.addElement(sTrimmed);
                }
            }
            filterOptionsWhere.append(") ");
        }

        if (filterData.getAfterDate() != null) {
            if (!includeGameTable) {
                addGameTable(filterOptionsFrom, filterOptionsWhere);
                includeGameTable = true;
            }
            filterOptionsWhere.append("and g.play_date > ? ");
            filterOptionsParams.addElement(new Timestamp(filterData.getAfterDate().getTime()));
        }
        if (filterData.getBeforeDate() != null) {
            if (!includeGameTable) {
                addGameTable(filterOptionsFrom, filterOptionsWhere);
                includeGameTable = true;
            }
            filterOptionsWhere.append("and g.play_date < ? ");
            filterOptionsParams.addElement(new Timestamp(filterData.getBeforeDate().getTime()));
        }

        if (filterData.getWinner() != GameData.UNKNOWN) {
            filterOptionsWhere.append("and m.winner = ? ");
            filterOptionsParams.addElement(filterData.getWinner());
        }

        if (filterData.getRatingP1Above() > 0) {
            if (!includeGameTable) {
                addGameTable(filterOptionsFrom, filterOptionsWhere);
                includeGameTable = true;
            }
            filterOptionsWhere.append("and g.player1_rating > ? ");
            filterOptionsParams.addElement(filterData.getRatingP1Above());
        }
        if (filterData.getRatingP2Above() > 0) {
            if (!includeGameTable) {
                addGameTable(filterOptionsFrom, filterOptionsWhere);
                includeGameTable = true;
            }
            filterOptionsWhere.append("and g.player2_rating > ? ");
            filterOptionsParams.addElement(filterData.getRatingP2Above());
        }

        if (filterData.isExcludeTimeOuts()) {
            if (!includeGameTable) {
                addGameTable(filterOptionsFrom, filterOptionsWhere);
                includeGameTable = true;
            }
            filterOptionsWhere.append("and (g.status is NULL or g.status != \'" + GameData.STATUS_TIMEOUT + "\') ");
        }

        if (filterData.isOnlyLive()) {
            if (!includeGameTable) {
                addGameTable(filterOptionsFrom, filterOptionsWhere);
                includeGameTable = true;
            }
            filterOptionsWhere.append("and (g.site_id = 2 and g.gid < 50000000000000) ");
        }

        if (filterData.isOnlyTurnBased()) {
            if (!includeGameTable) {
                addGameTable(filterOptionsFrom, filterOptionsWhere);
                includeGameTable = true;
            }
            filterOptionsWhere.append("and ((g.site_id = 2 and g.gid >= 50000000000000) or g.site_id != 2) ");
        }

        return includeGameTable;
    }

    protected void setFilterOptionsParams(PreparedStatement stmt, Vector<Object> filterOptionsParams, int startParam) throws SQLException {

        for (int i = 0; i < filterOptionsParams.size(); i++) {
            Object param = filterOptionsParams.elementAt(i);

            if (param instanceof Integer) {
                Integer p = (Integer) param;
                stmt.setInt(startParam + i, p.intValue());
            } else if (param instanceof Timestamp) {
                Timestamp t = (Timestamp) param;
                stmt.setTimestamp(startParam + i, t);
            } else {
                stmt.setString(startParam + i, param.toString());
            }
        }
    }

    protected int getSearchResults(GameStorerSearchRequestData requestData,
                                   StatsResult out,
                                   StringBuffer filterOptionsFrom,
                                   boolean includeGameTable,
                                   StringBuffer filterOptionsWhere,
                                   Vector<Object> filterOptionsParams,
                                   StringBuffer filterOptionsFrom2,
                                   StringBuffer filterOptionsWhere2,
                                   Vector<Object> filterOptionsParams2,
                                   boolean union,
                                   Connection con) throws Exception {

        PreparedStatement stmt = null;
        ResultSet result = null;
        Hashtable<Integer, GameStorerSearchResponseMoveData> moveResponses = new Hashtable<>();
        int totalGameCount = 0;
        int game = requestData.getGameStorerSearchRequestFilterData().getGame();
        GridState state = GridStateFactory.createGridState(
                requestData.getGameStorerSearchRequestFilterData().getGame(),
                requestData);
        out.rotation = state.getRotation();
        out.rotationSet = true;
        long hash = state.getHash();
        int currentPlayer = requestData.getNumMoves() % 2 + 1;

        try {

//			String qryString = 
//				"select m.next_move, m.rotation, m.winner, count(*) " +
//				"from " + move_table + " m " + filterOptionsFrom.toString() +
//				"where m.hash_key = ? " +
//				"and m.move_num = ? " +
//				"and m.game = ? " +
//				(includeGameTable ? "and g.game = m.game " : "") +
//				filterOptionsWhere.toString() +
//				"group by m.next_move, m.rotation, m.winner";
            String qryString = null;
            qryString = statsSql(move_table, hash, requestData.getNumMoves() - 1, game, includeGameTable,
                    filterOptionsFrom.toString(), filterOptionsWhere.toString(),
                    filterOptionsFrom2.toString(), filterOptionsWhere2.toString(), union);
            log4j.debug("queryString (getSearchResults) :" + qryString);

            stmt = con.prepareStatement(qryString);


            //stmt.setInt(2, requestData.getNumMoves() - 1);
            //stmt.setInt(3, requestData.getGameStorerSearchRequestFilterData().getGame());

            //int currentPlayer = requestData.getNumMoves() % 2 + 1;

            //GridState state = GridStateFactory.createGridState(
            //    requestData.getGameStorerSearchRequestFilterData().getGame(),
            //    requestData);
            //long hash = state.getHash();

            System.out.println("hash_key = " + hash);
            System.out.println("rotation = " + state.getRotation());
            System.out.println("move_num = " + (requestData.getNumMoves() - 1));

            //stmt.setLong(1, hash);
            int i = 1;
            setFilterOptionsParams(stmt, filterOptionsParams, i);
            if (union) {
                i += filterOptionsParams.size();

                setFilterOptionsParams(stmt, filterOptionsParams2, i);
            }

            log4j.debug("queryString (getSearchResults) : " + stmt.toString());

            result = stmt.executeQuery();

//System.out.println("results");
            while (result.next()) {

                int move = result.getInt(1);
                int rotation = result.getInt(2);
                int winner = result.getInt(3);
                int count = result.getInt(4);
                totalGameCount += count;

                if (move == 361) continue;
                //indicates game ended at this point
                //but still include in total game count

//System.out.println("move before rot, " + move);
                if (requestData.getNumMoves() == 0) {
                    move = state.rotateFirstMove(move, rotation);
                } else {
                    move = state.rotateMoveToLocalRotation(move, rotation);
                }

//System.out.println("move after rot, " + move);
//System.out.println("rotation, " + rotation);

                if (winner != GameData.UNKNOWN) {

                    boolean alreadyStored = true;
                    GameStorerSearchResponseMoveData moveData = moveResponses.get(Integer.valueOf(move));
                    if (moveData == null) {
                        alreadyStored = false;
                        moveData = new SimpleGameStorerSearchResponseMoveData();
                    }

                    moveData.setMove(move);
                    moveData.setGames(moveData.getGames() + count);
                    if (currentPlayer == winner) {
                        moveData.setWins(moveData.getWins() + count);
                    }

                    if (!alreadyStored) {
                        moveResponses.put(Integer.valueOf(move), moveData);
                    }
                }
            }

            for (GameStorerSearchResponseMoveData moveData : moveResponses.values()) {
                out.moves.add(moveData);
            }

        } finally {
            if (result != null) {
                result.close();
            }
            if (stmt != null) {
                stmt.close();
            }
        }
        out.total = totalGameCount;
        return totalGameCount;
    }


    protected List<GameData> getMatchingGames(GameStorerSearchRequestData requestData,
//                                    int totalGameCount,
                                   StringBuffer filterOptionsFrom,
                                   boolean includeGameTable,
                                   StringBuffer filterOptionsWhere,
                                   Vector<Object> filterOptionsParams, StringBuffer filterOptionsFrom2,
                                   StringBuffer filterOptionsWhere2,
                                   Vector<Object> filterOptionsParams2,
                                   boolean union,
                                   Connection con) throws Exception {

        PreparedStatement stmt = null;
        ResultSet results = null;
        List<Long> gids = new ArrayList<>();
        List<GameData> games = new ArrayList<>();

        try {

            // setup the limit to which games get selected
            int startGameNum = requestData.getGameStorerSearchRequestFilterData().getStartGameNum();
            int endGameNum = requestData.getGameStorerSearchRequestFilterData().getEndGameNum();

            String limitStart = Integer.toString(startGameNum);
            String limitLen = Integer.toString(endGameNum - startGameNum);

            GridState state = GridStateFactory.createGridState(
                    requestData.getGameStorerSearchRequestFilterData().getGame(),
                    requestData);
            long hash = state.getHash();
            int gm = requestData.getGameStorerSearchRequestFilterData().getGame();

            if (!includeGameTable) {
                addGameTable(filterOptionsFrom, filterOptionsWhere);
            }
//			String qryString = 
//				"select m.gid " +
//				"from " + move_table + " m " + filterOptionsFrom.toString() +
//				"where m.game = ? " +
//				"and m.hash_key = ? " +
//				"and m.move_num = ? " +
//				"and g.game = m.game " +
//				filterOptionsWhere.toString() +
//				"order by m.play_date desc ";


            //qryString += "limit " + limitStart + ", " + limitLen;

            String qryString = matchingGamesSql(move_table, hash, requestData.getNumMoves() - 1, gm, includeGameTable,
                    filterOptionsFrom.toString(), filterOptionsWhere.toString(),
                    filterOptionsFrom2.toString(), filterOptionsWhere2.toString(),
                    union, limitStart, limitLen);

            log4j.debug("queryString (getMatchingGames) : " + qryString);

            stmt = con.prepareStatement(qryString);


            log4j.debug("hash=" + hash);
            log4j.debug("move_num=" + (requestData.getNumMoves() - 1));
            int i = 1;
            //stmt.setInt(1, requestData.getGameStorerSearchRequestFilterData().getGame());
            //stmt.setLong(2, hash);
            //stmt.setInt(3, requestData.getNumMoves() - 1);
            setFilterOptionsParams(stmt, filterOptionsParams, i);
            if (union) {
                i += filterOptionsParams.size();
                setFilterOptionsParams(stmt, filterOptionsParams2, i++);
            }

            log4j.debug("queryString (getMatchingGames) : " + stmt.toString());

            results = stmt.executeQuery();

            int cnt = 0;
            while (results.next()) {
                gids.add(Long.valueOf(results.getLong(1)));
                log4j.debug(results.getLong(1));
            }

            if (results != null) {
                results.close();
            }
            if (stmt != null) {
                stmt.close();
            }


//			requestData.getGameStorerSearchRequestFilterData().setTotalGameNum(
//				totalGameCount);
//log4j.debug("total matched games = " + requestData.getGameStorerSearchRequestFilterData().getTotalGameNum());

//            // load matched games into response object
//            for (i = 0; i < gids.size(); i++) {
//                long gid = ((Long) gids.elementAt(i)).longValue();
//log4j.debug("load game " + gid);
////                GameData gameData = gameStorer.loadGame(gid, null);
//                GameData gameData = ((MySQLPenteGameStorer) gameStorer).loadGame(con, gid, null);
//                responseData.addGame(gameData);
//            }

            // load matched games into response object
            if (!gids.isEmpty()) {
                games = ((MySQLPenteGameStorer) gameStorer).loadGames(con, gids);
            }

        } finally {

            if (results != null) {
                results.close();
            }
            if (stmt != null) {
                stmt.close();
            }
        }
        return games;
    }

    public static String statsSql(String moveTable, long hash, int moveNum, int game, boolean includeGameTable,
                           String from1, String where1, String from2, String where2,
                           boolean union) {
        if (!union) {
            return "select m.next_move, m.rotation, m.winner, count(*) " +
                    "from " + moveTable + " m " + from1 +
                    "where m.hash_key = " + hash + " " +
                    "and m.move_num = " + moveNum + " " +
                    "and m.game = " + game + " " +
                    "and m.private = 'N' " +
                    (includeGameTable ? "and g.game = " + game + " " : "") + //thinking is that the game table might be primary table if filtering by certain things
                    where1 +
                    "group by m.next_move, m.rotation, m.winner";
        }
        return "select nextm, rot, win, count(*) " +
                "from ( " +
                "select * from (select m.next_move as nextm, m.rotation as rot, m.winner as win " +
                "from " + moveTable + " m " + from1 +
                "where m.hash_key = " + hash + " " +
                "and m.move_num = " + moveNum + " " +
                "and m.game = " + game + " " +
                "and m.private = 'N' " +
                "and g.game = " + game + " " +
                where1 + ") as d1 " +
                "union all " +
                "select * from (select m.next_move as nextm, m.rotation as rot, m.winner as win " +
                "from " + moveTable + " m " + from2 +
                "where m.hash_key = " + hash + " " +
                "and m.move_num = " + moveNum + " " +
                "and m.game = " + game + " " +
                "and m.private = 'N' " +
                "and g.game = " + game + " " +
                where2 + ") as d2 " +
                ") as main group by nextm, rot, win";
    }

    public static String matchingGamesSql(String moveTable, long hash, int moveNum, int gm, boolean includeGameTable,
                                   String from1, String where1, String from2, String where2,
                                   boolean union, String limitStart, String limitLen) {
        if (!union) {
            return "select m.gid " +
                    "from " + moveTable + " m " + from1 +
                    "where m.hash_key = " + hash + " " +
                    "and m.move_num = " + moveNum + " " +
                    "and m.game = " + gm + " " +
                    "and m.private = 'N' " +
                    (includeGameTable ? "and g.game = " + gm + " " : "") +
                    where1 +
                    "order by m.play_date desc " +
                    "limit " + limitStart + ", " + limitLen;
        }
        return "select gid from ( " +
                "select m.gid as gid, m.play_date as play_d " +
                "from " + moveTable + " m " + from1 +
                "where m.hash_key = " + hash + " " +
                "and m.move_num = " + moveNum + " " +
                "and m.game = " + gm + " " +
                "and m.private = 'N' " +
                "and g.game = " + gm + " " +
                where1 +
                "union all " +
                "select m.gid, m.play_date as play_d " +
                "from " + moveTable + " m " + from2 +
                "where m.hash_key = " + hash + " " +
                "and m.move_num = " + moveNum + " " +
                "and m.game = " + gm + " " +
                "and m.private = 'N' " +
                "and g.game = " + gm + " " +
                where2 +
                ") as main order by play_d desc " +
                "limit " + limitStart + ", " + limitLen;
    }
}
