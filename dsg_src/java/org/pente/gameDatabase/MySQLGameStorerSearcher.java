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

package org.pente.gameDatabase;

import java.sql.*;
import java.util.*;

import org.pente.database.*;
import org.pente.game.*;

import org.apache.log4j.*;

public class MySQLGameStorerSearcher implements GameStorerSearcher {

	private static final Category log4j = Category.getInstance(
		MySQLGameStorerSearcher.class.getName());

    private DBHandler           dbHandler;
    private GameStorer          gameStorer;
    private GameVenueStorer     gameVenueStorer;
    
    private String game_table = "pente_game";
    private String move_table = "pente_move";
    
    public MySQLGameStorerSearcher(DBHandler dbHandler, GameStorer gameStorer, GameVenueStorer gameVenueStorer) throws Exception {

        this.dbHandler = dbHandler;
        this.gameStorer = gameStorer;
        this.gameVenueStorer = gameVenueStorer;
    }


    public void search(GameStorerSearchRequestData requestData, GameStorerSearchResponseData responseData) throws Exception {
long startTime = System.currentTimeMillis();
        Connection con = null;

        responseData.setGameStorerSearchRequestData(requestData);

        // setup filter sql lines
        StringBuffer filterOptionsFrom = new StringBuffer();
        StringBuffer filterOptionsWhere = new StringBuffer();
        Vector filterOptionsParams = new Vector();
        StringBuffer filterOptionsFrom2 = new StringBuffer();
        StringBuffer filterOptionsWhere2 = new StringBuffer();
        Vector filterOptionsParams2 = new Vector();
        
        GameStorerSearchRequestFilterData filterData = requestData.getGameStorerSearchRequestFilterData();
        
		boolean includeGameTable = initFilterOptions(
			filterData, 1, filterOptionsFrom, filterOptionsWhere, filterOptionsParams);

       boolean union = false;
        if ((filterData.getPlayer1Name() != null && 
        	 filterData.getPlayer1Name().trim().length() > 0 &&
        	 filterData.getPlayer1Seat() == filterData.SEAT_ALL) ||
        	(filterData.getPlayer2Name() != null &&
             filterData.getPlayer2Name().trim().length() > 0 &&
        	 filterData.getPlayer2Seat() == filterData.SEAT_ALL)) {
        	union = true;
        	includeGameTable = true;
        	initFilterOptions(
                filterData, 2, filterOptionsFrom2, filterOptionsWhere2, filterOptionsParams2);
        }
		
        try {
            con = dbHandler.getConnection();

            int totalGameCount = 0;
            if (requestData.getGameStorerSearchRequestFilterData().doGetNextMoves()) {
	            totalGameCount = getSearchResults(requestData, 
					responseData, filterOptionsFrom, includeGameTable, 
					filterOptionsWhere, filterOptionsParams, 
					filterOptionsFrom2, filterOptionsWhere2, filterOptionsParams2, union, con);
            }
            filterOptionsWhere.append("and g.private = 'N' ");
            getMatchingGames(requestData, responseData, totalGameCount,
				filterOptionsFrom, includeGameTable, filterOptionsWhere, 
				filterOptionsParams, filterOptionsFrom2, filterOptionsWhere2, 
				filterOptionsParams2, union, con);

        } finally {
            dbHandler.freeConnection(con);
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
			 Vector filterOptionsParams) throws Exception {

		boolean includeGameTable = false;

		GameSiteData siteData = null;
        if (filterData.getSite() != null && filterData.getSite().trim().length() > 0) {

            siteData = gameVenueStorer.getGameSiteData(
                filterData.getGame(), filterData.getSite());
            if (siteData != null) {
				addGameTable(filterOptionsFrom, filterOptionsWhere);
				includeGameTable = true;
                filterOptionsWhere.append("and g.site_id = ? ");
                filterOptionsParams.addElement(new Integer(siteData.getSiteID()));
            }
        }
        if (filterData.getEvent() != null && filterData.getEvent().trim().length() > 0 &&
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
        if (filterData.getRound() != null && filterData.getRound().trim().length() > 0 &&
            !filterData.getRound().equals(GameRoundData.ALL_ROUNDS) && !filterData.getRound().equals("-")) {

			if (!includeGameTable) {
				addGameTable(filterOptionsFrom, filterOptionsWhere);
				includeGameTable = true;
			}
            filterOptionsWhere.append("and g.round = ? ");
            filterOptionsParams.addElement(filterData.getRound());
        }
        if (filterData.getSection() != null && filterData.getSection().trim().length() > 0 &&
            !filterData.getSection().equals(GameSectionData.ALL_SECTIONS) && !filterData.getSection().equals("-")) {

			if (!includeGameTable) {
				addGameTable(filterOptionsFrom, filterOptionsWhere);
				includeGameTable = true;
			}
            filterOptionsWhere.append("and g.section = ? ");
            filterOptionsParams.addElement(filterData.getSection());
        }
        
        boolean createOrPlayers = filterData.isP1OrP2() &&
                (filterData.getPlayer1Name() != null && filterData.getPlayer1Name().trim().length() > 0) &&
                (filterData.getPlayer2Name() != null && filterData.getPlayer2Name().trim().length() > 0);

        if (filterData.getPlayer1Name() != null && filterData.getPlayer1Name().trim().length() > 0) {
            filterOptionsFrom.append(", player p1 ");

            String pStr = filterData.getPlayer1Name().replace(" ", "");
            
			if (!includeGameTable) {
				addGameTable(filterOptionsFrom, filterOptionsWhere);
				includeGameTable = true;
			}
			if (filterData.getPlayer1Seat() == GameStorerSearchRequestFilterData.SEAT_ALL) {
				filterOptionsWhere.append("and g.player" + unionIndex + "_pid = p1.pid ");
			}
			else {
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
                for(String s: pStrArray) {
                    if (fst) {
                        fst = false;
                    } else {
                        filterOptionsWhere.append("or  ");
                    }
                    String sLower = s.toLowerCase();
                    if (sLower.contains("*")) {
                        filterOptionsWhere.append("p1.name_lower like ?  ");
                        filterOptionsParams.addElement(sLower.replace("*", "%"));
                    } else {
                        filterOptionsWhere.append("p1.name_lower = ?  ");
                        filterOptionsParams.addElement(sLower);
                    }
                }
                filterOptionsWhere.append(") ");
            }
        }
        if (filterData.getPlayer2Name() != null && filterData.getPlayer2Name().trim().length() > 0) {
            filterOptionsFrom.append(", player p2 ");

            String pStr = filterData.getPlayer2Name().replace(" ", "");
            String[] pStrArray = pStr.split(",");

			if (!includeGameTable) {
				addGameTable(filterOptionsFrom, filterOptionsWhere);
				includeGameTable = true;
			}
			if (filterData.getPlayer1Seat() == GameStorerSearchRequestFilterData.SEAT_ALL) {
				filterOptionsWhere.append("and g.player" + (3 - unionIndex) + "_pid = p2.pid ");
			}
			else {
				filterOptionsWhere.append("and g.player" + filterData.getPlayer2Seat() + "_pid = p2.pid ");
			}
			
			if (siteData != null) {
				filterOptionsWhere.append("and p2.site_id = " + siteData.getSiteID() + " ");
			}
			
            //filterOptionsWhere.append("and p2.name_lower = '" + 
           	//	filterData.getPlayer2Name().toLowerCase() + "' ");

            if (!createOrPlayers) {
                filterOptionsWhere.append("and (");
                boolean fst = true;
                for(String s: pStrArray) {
                    if (fst) {
                        fst = false;
                    } else {
                        filterOptionsWhere.append("or  ");
                    }
                    String sLower = s.toLowerCase();
                    if (sLower.contains("*")) {
                        filterOptionsWhere.append("p2.name_lower like ?  ");
                        filterOptionsParams.addElement(sLower.replace("*", "%"));
                    } else {
                        filterOptionsWhere.append("p2.name_lower = ?  ");
                        filterOptionsParams.addElement(sLower);
                    }
                }
                filterOptionsWhere.append(") ");
            }
        }
        if (createOrPlayers) {
            String p1Str = filterData.getPlayer1Name().replace(" ", "");
            String[] p1StrArray = p1Str.split(",");
            filterOptionsWhere.append("and (");
            boolean fst = true;
            for(String s: p1StrArray) {
                if (fst) {
                    fst = false;
                } else {
                    filterOptionsWhere.append("or  ");
                }
                String sLower = s.toLowerCase();
                if (sLower.contains("*")) {
                    filterOptionsWhere.append("p1.name_lower like ?  ");
                    filterOptionsParams.addElement(sLower.replace("*", "%"));
                } else {
                    filterOptionsWhere.append("p1.name_lower = ?  ");
                    filterOptionsParams.addElement(sLower);
                }
            }
            String p2Str = filterData.getPlayer2Name().replace(" ", "");
            String[] p2StrArray = p2Str.split(",");
            for(String s: p2StrArray) {
                filterOptionsWhere.append("or  ");
                String sLower = s.toLowerCase();
                if (sLower.contains("*")) {
                    filterOptionsWhere.append("p2.name_lower like ?  ");
                    filterOptionsParams.addElement(sLower.replace("*", "%"));
                } else {
                    filterOptionsWhere.append("p2.name_lower = ?  ");
                    filterOptionsParams.addElement(sLower);
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
		return includeGameTable;
    }

    protected void setFilterOptionsParams(PreparedStatement stmt, Vector filterOptionsParams, int startParam) throws SQLException {

        for (int i = 0; i < filterOptionsParams.size(); i++) {
            Object param = filterOptionsParams.elementAt(i);

            if (param instanceof Integer) {
                Integer p = (Integer) param;
                stmt.setInt(startParam + i, p.intValue());
            }
            else if (param instanceof Timestamp) {
                Timestamp t = (Timestamp) param;
                stmt.setTimestamp(startParam + i, t);
            }
            else {
                stmt.setString(startParam + i, param.toString());
            }
        }
    }

    protected int getSearchResults(GameStorerSearchRequestData requestData,
                                    GameStorerSearchResponseData responseData,
                                    StringBuffer filterOptionsFrom,
                                    boolean includeGameTable,
                                    StringBuffer filterOptionsWhere,
                                    Vector filterOptionsParams,
                                    StringBuffer filterOptionsFrom2,
                                    StringBuffer filterOptionsWhere2,
                                    Vector filterOptionsParams2,
                                    boolean union,
                                    Connection con) throws Exception {

        PreparedStatement stmt = null;
        ResultSet result = null;
        Hashtable moveResponses = new Hashtable();
		int totalGameCount = 0;
        int game = requestData.getGameStorerSearchRequestFilterData().getGame();
        GridState state = GridStateFactory.createGridState(
                requestData.getGameStorerSearchRequestFilterData().getGame(),
                requestData);
        responseData.setRotation(state.getRotation());
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
        	if (!union) {
				qryString = 
					"select m.next_move, m.rotation, m.winner, count(*) " +
					"from " + move_table + " m " + filterOptionsFrom.toString() +
					"where m.hash_key = " + hash + " " +
					"and m.move_num = " + (requestData.getNumMoves() - 1) + " " +
					"and m.game = " + game + " " +
					(includeGameTable ? "and g.game = " + game + " " : "") + //thinking is that the game table might be primary table if filtering by certain things
					filterOptionsWhere.toString() +
					"group by m.next_move, m.rotation, m.winner";
        	}
        	else {
				qryString = 
					"select nextm, rot, win, count(*) " +
					"from ( " +
					"select * from (select m.next_move as nextm, m.rotation as rot, m.winner as win " +
					"from " + move_table + " m " + filterOptionsFrom.toString() +
					"where m.hash_key = " + hash + " " +
					"and m.move_num = " + (requestData.getNumMoves() - 1) + " " +
					"and m.game = " + game + " " +
					"and g.game = " + game + " " +
					filterOptionsWhere.toString() + ") as d1 " +
					"union all " +
					"select * from (select m.next_move as nextm, m.rotation as rot, m.winner as win " +
					"from " + move_table + " m " + filterOptionsFrom2.toString() +
					"where m.hash_key = " + hash + " " +
					"and m.move_num = " + (requestData.getNumMoves() - 1) + " " +
					"and m.game = " + game + " " +
					"and g.game = " + game + " " +
					filterOptionsWhere2.toString() + ") as d2 " +
					") as main group by nextm, rot, win";
			}
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
                    GameStorerSearchResponseMoveData moveData = (GameStorerSearchResponseMoveData) moveResponses.get(new Integer(move));
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
                        moveResponses.put(new Integer(move), moveData);
                    }
                }
            }

            Enumeration e = moveResponses.elements();
            while (e.hasMoreElements()) {
                GameStorerSearchResponseMoveData moveData = (GameStorerSearchResponseMoveData) e.nextElement();
                responseData.addSearchResponseMoveData(moveData);
            }

        } finally {
            if (result != null) {
                result.close();
            }
            if (stmt != null) {
                stmt.close();
            }
        }
		return totalGameCount;
    }

    
    protected void getMatchingGames(GameStorerSearchRequestData requestData,
                                    GameStorerSearchResponseData responseData,
                                    int totalGameCount,
                                    StringBuffer filterOptionsFrom,
                                    boolean includeGameTable,
                                    StringBuffer filterOptionsWhere,
                                    Vector filterOptionsParams,                                    StringBuffer filterOptionsFrom2,
                                    StringBuffer filterOptionsWhere2,
                                    Vector filterOptionsParams2,
                                    boolean union,
                                    Connection con) throws Exception {

        PreparedStatement stmt = null;
        ResultSet results = null;
        Vector gids = new Vector();

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
			
			String qryString = null;

        	if (!union) {
        		qryString =
					"select m.gid " +
					"from " + move_table + " m " + filterOptionsFrom.toString() +
					"where m.hash_key = " + hash + " " +
					"and m.move_num = " + (requestData.getNumMoves() - 1) + " " +
					"and m.game = " + gm + " " +
					(includeGameTable ? "and g.game = " + gm + " " : "") +
					filterOptionsWhere.toString() +
					"order by m.play_date desc " +
					"limit " + limitStart + ", " + limitLen;
        	}
        	else {
        		qryString =
					"select gid from ( " +
					"select m.gid as gid, m.play_date as play_d " +
					"from " + move_table + " m " + filterOptionsFrom.toString() +
					"where m.hash_key = " + hash + " " +
					"and m.move_num = " + (requestData.getNumMoves() - 1) + " " +
					"and m.game = " + gm + " " +
					"and g.game = " + gm + " " +
					filterOptionsWhere.toString() +
					"union all " +
					"select m.gid, m.play_date as play_d " +
					"from " + move_table + " m " + filterOptionsFrom2.toString() +
					"where m.hash_key = " + hash + " " +
					"and m.move_num = " + (requestData.getNumMoves() - 1) + " " +
					"and m.game = " + gm + " " +
					"and g.game = " + gm + " " +
					filterOptionsWhere2.toString() +
					") as main order by play_d desc " +
					"limit " + limitStart + ", " + limitLen;
        	}

            log4j.debug("queryString (getMatchingGames) : " + qryString);

            stmt = con.prepareStatement(qryString);


			
log4j.debug("hash="+hash);
log4j.debug("move_num="+ (requestData.getNumMoves() - 1));
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
        		gids.addElement(new Long(results.getLong(1)));
        		log4j.debug(results.getLong(1));
            }

            if (results != null) {
                results.close();
            }
            if (stmt != null) {
                stmt.close();
            }
			

			requestData.getGameStorerSearchRequestFilterData().setTotalGameNum(
				totalGameCount);
log4j.debug("total matched games = " + requestData.getGameStorerSearchRequestFilterData().getTotalGameNum());

            // load matched games into response object
            for (i = 0; i < gids.size(); i++) {
                long gid = ((Long) gids.elementAt(i)).longValue();
log4j.debug("load game " + gid);
                GameData gameData = gameStorer.loadGame(gid, null);
                responseData.addGame(gameData);
            }

        } finally {

            if (results != null) {
                results.close();
            }
            if (stmt != null) {
                stmt.close();
            }
        }
    }
}