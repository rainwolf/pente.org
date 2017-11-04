package org.pente.turnBased;

import java.sql.*;
import java.util.*;
import java.util.Date;

import org.pente.database.*;

import org.apache.log4j.*;

public class MySQLTBGameStorer implements TBGameStorer {

	private Category log4j = Category.getInstance(
		MySQLTBGameStorer.class.getName());

	public static final int FLOATINGVACATIONDAYS = 10;

	private DBHandler dbHandler;
	
	public MySQLTBGameStorer(DBHandler dbHandler) {
		this.dbHandler = dbHandler;
	}

	public int getEventId(int game) throws TBStoreException {
		log4j.debug("MySQLTBGameStorer.getEventId(" + game + ")");
		
		Connection con = null;
        PreparedStatement stmt = null;
        ResultSet result = null;
		int eid = -1;
		
		try {
	        try {
				con = dbHandler.getConnection();
	
	            stmt = con.prepareStatement(
					"select eid " +
					"from game_event " +
					"where game = ? " +
					"and site_id = ? " +
					"and name = ?");
				
				stmt.setInt(1, game);
	            stmt.setInt(2, 2);
	            stmt.setString(3, "Turn-based Game");
				
				result = stmt.executeQuery();
	
	            if (result.next()) {
	                eid = result.getInt(1);
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
	    } catch (SQLException se) {
			throw new TBStoreException(se);
	    }
		
		return eid;
	}

	public void createSet(TBSet tbSet) throws TBStoreException {
		log4j.debug("MySQLTBGameStorer.createSet()");
		
		Connection con = null;
        PreparedStatement stmt = null;
        ResultSet result = null;

		try {
	        try {
				con = dbHandler.getConnection();

				String tbTable = " tb_set ";
				if (tbSet.getPlayer1Pid() == 23000000020606L || tbSet.getPlayer2Pid() == 23000000020606L) {
					tbTable = " tb_set_ai ";
				}

	            stmt = con.prepareStatement(
					"insert into" + tbTable +
	                "(gid1, gid2, p1_pid, p2_pid, state, creation_date, " +
	                "inviter_pid, cancel_pid, private, invitation_restriction) " +
					"values(?, ?, ?, ?, ?, ?, ?, 0, ?, ?)",
					Statement.RETURN_GENERATED_KEYS);

				long gid2 = tbSet.getGames()[1] != null ? 
					tbSet.getGames()[1].getGid() : 0;

                if (tbSet.getState() != TBSet.STATE_ACTIVE) {
                    tbSet.setState(TBSet.STATE_NOT_STARTED);
                }
                    
				stmt.setLong(1, tbSet.getGames()[0].getGid());
				stmt.setLong(2, gid2);
				stmt.setLong(3, tbSet.getPlayer1Pid());
				stmt.setLong(4, tbSet.getPlayer2Pid());
				stmt.setString(5, Character.toString(tbSet.getState()));
				stmt.setTimestamp(6, new Timestamp(tbSet.getCreationDate().getTime()));
				stmt.setLong(7, tbSet.getInviterPid());
				stmt.setString(8, tbSet.isPrivateGame() ? "Y" : "N");
				stmt.setString(9, "" + tbSet.getInvitationRestriction());
	            stmt.executeUpdate();
				result = stmt.getGeneratedKeys();
	
	            if (result.next()) {
	                long sid = result.getLong(1);
	                tbSet.setSetId(sid);
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
	    } catch (SQLException se) {
			throw new TBStoreException(se);
	    }
	}
	
	public void createGame(TBGame game) throws TBStoreException {

		log4j.debug("MySQLTBGameStorer.createGame()");
		Connection con = null;
        PreparedStatement stmt = null;
        ResultSet result = null;

		try {
	        try {
				con = dbHandler.getConnection();
	
				String tbTable = " tb_game ";
				if (game.getPlayer1Pid() == 23000000020606L || game.getPlayer2Pid() == 23000000020606L) {
					tbTable = " tb_game_ai ";
				}

	            stmt = con.prepareStatement(
					"insert into" + tbTable +
	                "(state, p1_pid, p2_pid, creation_date, game, " +
	                " event_id, round, section, days_per_move, rated, " +
	                " dpente_state, start_date, last_move_date, timeout_date) " +
					"values(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", 
					Statement.RETURN_GENERATED_KEYS);
				
                if (game.getState() != TBGame.STATE_ACTIVE) {
                    game.setState(TBGame.STATE_NOT_STARTED);
                }
                
				stmt.setString(1, Character.toString(game.getState()));
	            stmt.setLong(2, game.getPlayer1Pid());
	            stmt.setLong(3, game.getPlayer2Pid());
	            stmt.setTimestamp(4, new Timestamp(game.getCreationDate().getTime()));
				stmt.setInt(5, game.getGame());
				stmt.setInt(6, game.getEventId());
				stmt.setInt(7, game.getRound());
				stmt.setInt(8, game.getSection());
				stmt.setInt(9, game.getDaysPerMove());
				stmt.setString(10, game.isRated() ? "Y" : "N");
				stmt.setInt(11, game.getDPenteState());
				
				if (game.getState() == TBGame.STATE_ACTIVE) {
				    stmt.setTimestamp(12, new Timestamp(game.getStartDate().getTime()));
                    stmt.setTimestamp(13, new Timestamp(game.getLastMoveDate().getTime()));
				    stmt.setTimestamp(14, new Timestamp(game.getTimeoutDate().getTime()));
                }
                else {
                    stmt.setNull(12, Types.TIMESTAMP);
                    stmt.setNull(13, Types.TIMESTAMP);
                    stmt.setNull(14, Types.TIMESTAMP);
                }
                
	            stmt.executeUpdate();
				result = stmt.getGeneratedKeys();
	
	            if (result.next()) {
	                long gid = result.getLong(1);
					game.setGid(gid);
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
	    } catch (SQLException se) {
			throw new TBStoreException(se);
	    }
	}

	public TBSet loadSet(long setId) throws TBStoreException {
		
		log4j.debug("MySQLTBGameStorer.loadSet(" + setId + ")");
		Connection con = null;
        PreparedStatement stmt = null;
        ResultSet result = null;
		TBSet set = null;
		
		try {
	        try {
				con = dbHandler.getConnection();
	
	            stmt = con.prepareStatement(
					"select gid1, gid2, p1_pid, p2_pid, state, " +
					"creation_date, completion_date, inviter_pid, cancel_pid, " +
					"cancel_msg, private " +
					"from tb_set " +
					"where sid = ?"); 
				
				stmt.setLong(1, setId);
				
				result = stmt.executeQuery();
	
	            if (result.next()) {
	            	TBGame g1 = loadGame(result.getLong(1));
	            	TBGame g2 = loadGame(result.getLong(2));
					set = new TBSet(setId, g1, g2);
					set.setPlayer1Pid(result.getLong(3));
					set.setPlayer2Pid(result.getLong(4));
					set.setState(result.getString(5).charAt(0));
					set.setCreationDate(getDate(result, 6));
					set.setCompletionDate(getDate(result, 7));
					set.setInviterPid(result.getLong(8));
                    set.setCancelPid(result.getLong(9));
                    set.setCancelMsg(result.getString(10));
                    set.setPrivateGame(result.getString(11).equals("Y"));
	            } else {
		            stmt = con.prepareStatement(
						"select gid1, gid2, p1_pid, p2_pid, state, " +
						"creation_date, completion_date, inviter_pid, cancel_pid, " +
						"cancel_msg, private " +
						"from tb_set_ai " +
						"where sid = ?"); 
					
					stmt.setLong(1, setId);
					
					result = stmt.executeQuery();
		
		            if (result.next()) {
		            	TBGame g1 = loadGame(result.getLong(1));
		            	TBGame g2 = loadGame(result.getLong(2));
						set = new TBSet(setId, g1, g2);
						set.setPlayer1Pid(result.getLong(3));
						set.setPlayer2Pid(result.getLong(4));
						set.setState(result.getString(5).charAt(0));
						set.setCreationDate(getDate(result, 6));
						set.setCompletionDate(getDate(result, 7));
						set.setInviterPid(result.getLong(8));
	                    set.setCancelPid(result.getLong(9));
	                    set.setCancelMsg(result.getString(10));
	                    set.setPrivateGame(result.getString(11).equals("Y"));
		            }
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
	    } catch (SQLException se) {
			throw new TBStoreException(se);
	    }
		
		return set;
	}

	public TBSet loadSetByGid(long gid) throws TBStoreException {
		
		log4j.debug("MySQLTBGameStorer.loadSetByGid(" + gid + ")");
		Connection con = null;
        PreparedStatement stmt = null;
        ResultSet result = null;
		TBSet set = null;
		
		try {
	        try {
				con = dbHandler.getConnection();
	
	            stmt = con.prepareStatement(
					"select sid " +
					"from tb_set " +
					"where gid1 = ? or gid2 = ?"); 
				
				stmt.setLong(1, gid);
				stmt.setLong(2, gid);
				
				result = stmt.executeQuery();
	
	            if (result.next()) {
	            	long sid = result.getLong(1);
	            	set = loadSet(sid);
	            } else {
		            stmt = con.prepareStatement(
						"select sid " +
						"from tb_set_ai " +
						"where gid1 = ? or gid2 = ?"); 
					
					stmt.setLong(1, gid);
					stmt.setLong(2, gid);
					
					result = stmt.executeQuery();
		
		            if (result.next()) {
		            	long sid = result.getLong(1);
		            	set = loadSet(sid);
		            }
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
	    } catch (SQLException se) {
			throw new TBStoreException(se);
	    }
		
		return set;
	}
	
	public void undoLastMove(long gid) {

		log4j.debug("MySQLGameTbStorer.undoLastMove(" + gid + ")");

		Connection con = null;
		PreparedStatement stmt = null;

		try {
			con = dbHandler.getConnection();

			stmt = con.prepareStatement(
					"DELETE FROM tb_move WHERE gid = ? and move_num = (SELECT maxmove FROM ( SELECT MAX(move_num) AS maxmove FROM tb_move where gid = ?) AS tmp)");
			stmt.setLong(1, gid);
			stmt.setLong(2, gid);

			stmt.executeUpdate();

		} catch (SQLException se) {
//			throw new TBStoreException(se);
		} finally {
			if (stmt != null) {
				try { stmt.close(); } catch (SQLException se) {}
			}
			if (con != null) {
				try { dbHandler.freeConnection(con); } catch (SQLException se) {}
			}
		}
	}

	public void hideGame(long gid, byte hiddenBy) {
		log4j.debug("MySQLGameTbStorer.hideGame(" + gid + ", " + hiddenBy + ")");

		Connection con = null;
		PreparedStatement stmt = null;

		try {
			con = dbHandler.getConnection();

			stmt = con.prepareStatement(
					"update tb_game " +
							" set hiddenBy = ? " +
							" where gid = ?");
			stmt.setByte(1, hiddenBy);
			stmt.setLong(2, gid);
			stmt.executeUpdate();

		} catch (SQLException se) {
			se.printStackTrace();
		} finally {
			if (stmt != null) {
				try { stmt.close(); } catch (SQLException se) {}
			}
			if (con != null) {
				try { dbHandler.freeConnection(con); } catch (SQLException se) {}
			}
		}
	}

	private static final String TB_SET_COLUMNS = 
		"s.sid, s.p1_pid, s.p2_pid, s.state, s.creation_date, " +
		"s.completion_date, s.inviter_pid, s.cancel_pid, s.cancel_msg, " +
		"s.private, s.invitation_restriction, " +
		"g.gid, g.state, g.p1_pid, g.p2_pid, g.creation_date, " +
		"g.start_date, g.last_move_date, g.timeout_date, g.completion_date, " +
		"g.game, g.event_id, g.round, g.section, g.days_per_move, g.rated, " +
		"g.winner, g.dpente_state, g.dpente_swap, g.hiddenBy";

	private static final String TB_COLUMNS = 
		"gid, state, p1_pid, p2_pid, creation_date, " +
		"start_date, last_move_date, timeout_date, completion_date, " +
		"game, event_id, round, section, days_per_move, rated, " +
		"winner, dpente_state, dpente_swap, hiddenBy";

	public TBGame loadGame(long gid) throws TBStoreException {

		log4j.debug("MySQLTBGameStorer.loadGame(" + gid + ")");
		Connection con = null;
        PreparedStatement stmt = null;
        ResultSet result = null;
		TBGame game = null;
		
		try {
	        try {
				con = dbHandler.getConnection();
	
	            stmt = con.prepareStatement(
					"select " + TB_COLUMNS + " " +
					"from tb_game " +
					"where gid = ?"); 
				
				stmt.setLong(1, gid);
				
				result = stmt.executeQuery();
	
	            if (result.next()) {
					game = new TBGame();
					loadGame(con, result, game, 1);
	            } else {
		            stmt = con.prepareStatement(
						"select " + TB_COLUMNS + " " +
						"from tb_game_ai " +
						"where gid = ?"); 
					
					stmt.setLong(1, gid);
					
					result = stmt.executeQuery();
		
		            if (result.next()) {
						game = new TBGame();
						loadGame(con, result, game, 1);
		            }
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
	    } catch (SQLException se) {
			throw new TBStoreException(se);
	    }
		
		return game;
	}
	
	private void loadMoves(Connection con, TBGame game) throws SQLException {
		PreparedStatement stmt = null;
		ResultSet result = null;
		
		try {
			String tbTable = " tb_move ";
			if (game.getPlayer1Pid() == 23000000020606L || game.getPlayer2Pid() == 23000000020606L) {
				tbTable = " tb_move_ai ";
			}
			stmt = con.prepareStatement(
				"select move " +
				"from " + tbTable +
				"where gid = ? " +
				"order by move_num asc");
			stmt.setLong(1, game.getGid());
			result = stmt.executeQuery();
			List<Integer> moves = new ArrayList<Integer>();
			while (result.next()) {
				int move = result.getInt(1);
				if (move == -1) {
					game.setUndoRequested(true);
				} else {
					moves.add(move);
				}
			}
			game.setMoves(moves);
			
	    } finally {
	        if (result != null) {
	            result.close();
	        }
	        if (stmt != null) {
	            stmt.close();
	        }
	    }
	}
	
	private void loadMessages(Connection con, TBGame game) throws SQLException {
		PreparedStatement stmt = null;
		ResultSet result = null;

		try {
			stmt = con.prepareStatement(
				"select pid, message, move_num, seq_nbr, date " +
				"from tb_message " +
				"where gid = ? " +
				"order by move_num asc, seq_nbr asc");
			stmt.setLong(1, game.getGid());
			result = stmt.executeQuery();
			while (result.next()) {
				TBMessage m = new TBMessage();
				m.setPid(result.getLong(1));
				m.setMessage(result.getString(2));
				m.setMoveNum(result.getInt(3));
				m.setSeqNbr(result.getInt(4));
				m.setDate(new java.util.Date(result.getTimestamp(5).getTime()));
				game.addMessage(m);
			}
			
	    } finally {
	        if (result != null) {
	            result.close();
	        }
	        if (stmt != null) {
	            stmt.close();
	        }
	    }
	}
	
	public void storeNewMessage(long gid, TBMessage message)
		throws TBStoreException {

		log4j.debug("MySQLTBGameStorer.storeNewMessage(" + gid + ")");
		
		Connection con = null;
		PreparedStatement stmt = null;

		try {
			con = dbHandler.getConnection();
			stmt = con.prepareStatement(
				"insert into tb_message" +
				"(gid, pid, seq_nbr, message, move_num, date) " +
				"values(?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE message=VALUES(message)");
			stmt.setLong(1, gid);
			stmt.setLong(2, message.getPid());
			stmt.setInt(3, message.getSeqNbr());
			stmt.setString(4, message.getMessage());
			stmt.setInt(5, message.getMoveNum());
			stmt.setTimestamp(6, new Timestamp(message.getDate().getTime()));
			stmt.execute();
			
	    } catch (SQLException se) {
			throw new TBStoreException(se);
	    } finally {
			if (stmt != null) {
				try { stmt.close(); } catch (SQLException se) {}
			}
			if (con != null) {
				try { dbHandler.freeConnection(con); } catch (SQLException se) {}
			}
	    }
	}
	
	private void loadGame(Connection con, 
		ResultSet result, TBGame game, int r) throws SQLException {
		
		fillGame(result, game, r);
		loadMoves(con, game);
		loadMessages(con, game);
	}
	private void fillGame(ResultSet result, TBGame game, int r) throws SQLException {
		game.setGid(result.getLong(r++));
		game.setState(result.getString(r++).charAt(0));
		game.setPlayer1Pid(result.getLong(r++));
		game.setPlayer2Pid(result.getLong(r++));
		game.setCreationDate(getDate(result, r++));
		game.setStartDate(getDate(result, r++));
		game.setLastMoveDate(getDate(result, r++));
		game.setTimeoutDate(getDate(result, r++));
		game.setCompletionDate(getDate(result, r++));
		game.setGame(result.getInt(r++));
		game.setEventId(result.getInt(r++));
		game.setRound(result.getInt(r++));
		game.setSection(result.getInt(r++));
		game.setDaysPerMove(result.getInt(r++));
		game.setRated(result.getString(r++).equals("Y"));
		game.setWinner(result.getInt(r++));
		game.setDPenteState(result.getInt(r++));
		String swapped = result.getString(r++);
		game.setDPenteSwapped(swapped != null && swapped.equals("Y"));
		game.setHiddenBy(result.getByte(r++));
	}
	private java.util.Date getDate(ResultSet result, int column) 
		throws SQLException {
		java.util.Date d = null;
		Timestamp t = result.getTimestamp(column);
		if (t != null) {
			d = new java.util.Date(t.getTime());
		}
		return d;
	}

	public List<TBSet> loadGamesExpiringBefore(java.util.Date date) 
		throws TBStoreException {

		log4j.debug("MySQLTBGameStorer.loadGamesExpiringBefore(" + 
			date.getTime() + ")");

		Connection con = null;
        PreparedStatement stmt = null;
        ResultSet result = null;
		List<TBSet> sets = null;
		
		try {
			con = dbHandler.getConnection();

            stmt = con.prepareStatement(
				"(select gid " +
				"from tb_game " +
				"where timeout_date < ? and state = ? )" +
				" union " +
				"(select gid " +
				"from tb_game_ai " +
				"where timeout_date < ? and state = ? )");
			stmt.setTimestamp(1, new Timestamp(date.getTime()));
			stmt.setString(2, Character.toString(TBGame.STATE_ACTIVE));
			stmt.setTimestamp(3, new Timestamp(date.getTime()));
			stmt.setString(4, Character.toString(TBGame.STATE_ACTIVE));
			result = stmt.executeQuery();
			
			List<Long> gids = new ArrayList<Long>();
			while (result.next()) {
				gids.add(result.getLong(1));
			}
			sets = new ArrayList<TBSet>(gids.size());
			for (long l : gids) {
				TBSet s = loadSetByGid(l);
				if (s != null) {
					sets.add(s);
				}
			}
				
	    } catch (SQLException se) {
			throw new TBStoreException(se);
	    } finally {
            if (result != null) {
                try { result.close(); } catch (SQLException se) {}
            }
			if (stmt != null) {
				try { stmt.close(); } catch (SQLException se) {}
			}
			if (con != null) {
				try { dbHandler.freeConnection(con); } catch (SQLException se) {}
			}
	    }
		
		return sets;
	}
	public List<TBSet> loadWaitingSets() throws TBStoreException {

		log4j.debug("MySQLTBGameStorer.loadWaitingSets()");
		
		Connection con = null;
        PreparedStatement stmt = null;
		List<TBSet> sets = null;
		
		try {
			con = dbHandler.getConnection();

            stmt = con.prepareStatement(
				"select " + TB_SET_COLUMNS + " " +
				"from tb_set s, tb_game g " +
				"where s.state = '" + TBSet.STATE_NOT_STARTED + "' " +
				"and (s.p1_pid = 0 or s.p2_pid = 0) " +
				"and (s.gid1 = g.gid or s.gid2 = g.gid)");
            
            sets = loadSets(con, stmt);
				
	    } catch (SQLException se) {
			throw new TBStoreException(se);
	    } finally {
			if (stmt != null) {
				try { stmt.close(); } catch (SQLException se) {}
			}
			if (con != null) {
				try { dbHandler.freeConnection(con); } catch (SQLException se) {}
			}
	    }
		
		return sets;
	}

	public int getNumGamesMyTurn(long pid) throws TBStoreException {
		throw new UnsupportedOperationException("Not supported.");
	}

	public List<TBSet> loadSets(long pid) throws TBStoreException {

		log4j.debug("MySQLTBGameStorer.loadSets(" + pid + ")");
		Connection con = null;
        PreparedStatement stmt = null;
		List<TBSet> sets = null;
		
		try {
			con = dbHandler.getConnection();

            stmt = con.prepareStatement(
    			"(select " + TB_SET_COLUMNS + " " +
				"from tb_set s, tb_game g " +
				"where s.state != '" + TBSet.STATE_CANCEL + "' " +
				"and s.state != '" + TBSet.STATE_COMPLETED + "' " +
				"and s.state != '" + TBSet.STATE_TIMEOUT + "' " +
				"and (s.p1_pid = ? or s.p2_pid = ?) " +
				"and (s.gid1 = g.gid or s.gid2 = g.gid) )" +
				" UNION " + 
    			"(select " + TB_SET_COLUMNS + " " +
				"from tb_set_ai s, tb_game_ai g " +
				"where s.state != '" + TBSet.STATE_CANCEL + "' " +
				"and s.state != '" + TBSet.STATE_COMPLETED + "' " +
				"and s.state != '" + TBSet.STATE_TIMEOUT + "' " +
				"and (s.p1_pid = ? or s.p2_pid = ?) " +
				"and (s.gid1 = g.gid or s.gid2 = g.gid) )");

			stmt.setLong(1, pid);
			stmt.setLong(2, pid);
			stmt.setLong(3, pid);
			stmt.setLong(4, pid);

            sets = loadSets(con, stmt);
				
	    } catch (SQLException se) {
			throw new TBStoreException(se);
	    } finally {
			if (stmt != null) {
				try { stmt.close(); } catch (SQLException se) {}
			}
			if (con != null) {
				try { dbHandler.freeConnection(con); } catch (SQLException se) {}
			}
	    }
		
		return sets;
	}
	
//	private List<TBGame> loadGames(Connection con, PreparedStatement stmt)
//		throws SQLException {
//		List<TBGame> games = new ArrayList<TBGame>();
//		
//		ResultSet result = null;
//        try {
//			result = stmt.executeQuery();
//            while (result.next()) {
//				TBGame game = new TBGame();
//				loadGame(con, result, game, 1);
//				games.add(game);
//            }
//
//        } finally {
//            if (result != null) {
//                result.close();
//            }
//        }
//		return games;
//	}
	private List<TBSet> loadSets(Connection con, PreparedStatement stmt) 
		throws SQLException {
		
		List<TBSet> sets = new ArrayList<TBSet>();
		
		ResultSet results = null;
		TBSet currentSet = null;
		
        try {
			results = stmt.executeQuery();
		
	        while (results.next()) {
	        	long sid = results.getLong(1);
	        	if (currentSet == null || sid != currentSet.getSetId()) {
	        		currentSet = new TBSet(sid);
	        		currentSet.setPlayer1Pid(results.getLong(2));
	        		currentSet.setPlayer2Pid(results.getLong(3));
	        		currentSet.setState(results.getString(4).charAt(0));
	        		currentSet.setCreationDate(getDate(results, 5));
	        		currentSet.setCompletionDate(getDate(results, 6));
	        		currentSet.setInviterPid(results.getLong(7));
                    currentSet.setCancelPid(results.getLong(8));
                    currentSet.setCancelMsg(results.getString(9));
                    currentSet.setPrivateGame(results.getString(10).equals("Y"));
	        		currentSet.setInvitationRestriction(results.getString(11).charAt(0));
	            	sets.add(currentSet);
	        	}
	        	TBGame game = new TBGame();
				loadGame(con, results, game, 12);
	        	currentSet.addGame(game);	
	        }

        } finally {
            if (results != null) {
                results.close();
            }
        }

        return sets;
	}

	public void storeNewMove(long gid, int moveNum, int move)
		throws TBStoreException {
		
		log4j.debug("MySQLGameTbStorer.storeNewMove(" + gid + ", " + moveNum + ", " +
			move + ")");
		
		Connection con = null;
		PreparedStatement stmt = null;

		try {
			con = dbHandler.getConnection();

	        stmt = con.prepareStatement(
				"insert into tb_move " +
				"(gid, move_num, move) " +
				"values(?, ?, ?) ON DUPLICATE KEY UPDATE move=VALUES(move)");
			stmt.setLong(1, gid);
			stmt.setInt(2, moveNum);
			stmt.setInt(3, move);
	
			stmt.executeUpdate();
			
	    } catch (SQLException se) {
			throw new TBStoreException(se);
	    } finally {
			if (stmt != null) {
				try { stmt.close(); } catch (SQLException se) {}
			}
			if (con != null) {
				try { dbHandler.freeConnection(con); } catch (SQLException se) {}
			}
	    }
	}
	public void storeNewAIMove(long gid, int moveNum, int move)
		throws TBStoreException {
		
		log4j.debug("MySQLGameTbStorer.storeNewMove(" + gid + ", " + moveNum + ", " +
			move + ")");
		
		Connection con = null;
		PreparedStatement stmt = null;

		try {
			con = dbHandler.getConnection();

	        stmt = con.prepareStatement(
				"insert into tb_move_ai " +
				"(gid, move_num, move) " +
				"values(?, ?, ?)");
			stmt.setLong(1, gid);
			stmt.setInt(2, moveNum);
			stmt.setInt(3, move);
	
			stmt.executeUpdate();
			
	    } catch (SQLException se) {
			throw new TBStoreException(se);
	    } finally {
			if (stmt != null) {
				try { stmt.close(); } catch (SQLException se) {}
			}
			if (con != null) {
				try { dbHandler.freeConnection(con); } catch (SQLException se) {}
			}
	    }
	}
	public void updateGameAfterMove(TBGame game) throws TBStoreException {
		log4j.debug("MySQLGameTbStorer.updateGameAfterMove(" + game.getGid() + ")");

		Connection con = null;
		PreparedStatement stmt = null;

		try {
			con = dbHandler.getConnection();

			String tbTable = " tb_game ";
			if (game.getPlayer1Pid() == 23000000020606L || game.getPlayer2Pid() == 23000000020606L) {
				tbTable = " tb_game_ai ";
			}

			stmt = con.prepareStatement(
					"update" + tbTable +
							"set last_move_date = ?, " +
							"timeout_date = ? " +
							"where gid = ?");
			stmt.setTimestamp(1, new Timestamp(game.getLastMoveDate().getTime()));
			stmt.setTimestamp(2, new Timestamp(game.getTimeoutDate().getTime()));
			stmt.setLong(3, game.getGid());
			stmt.executeUpdate();

		} catch (SQLException se) {
			throw new TBStoreException(se);
		} finally {
			if (stmt != null) {
				try { stmt.close(); } catch (SQLException se) {}
			}
			if (con != null) {
				try { dbHandler.freeConnection(con); } catch (SQLException se) {}
			}
		}
	}

	public void setGameEventId(long gameId, long eventId) throws TBStoreException {
		log4j.debug("MySQLGameTbStorer.updateGameEventId(" + gameId + ", " + eventId + ")");

		Connection con = null;
		PreparedStatement stmt = null;

		try {
			con = dbHandler.getConnection();

			stmt = con.prepareStatement(
					"update tb_game " +
							" set event_id = ? " +
							" where gid = ?");
			stmt.setLong(1, eventId);
			stmt.setLong(2, gameId);
			stmt.executeUpdate();

		} catch (SQLException se) {
			throw new TBStoreException(se);
		} finally {
			if (stmt != null) {
				try { stmt.close(); } catch (SQLException se) {}
			}
			if (con != null) {
				try { dbHandler.freeConnection(con); } catch (SQLException se) {}
			}
		}
	}

	public void acceptInvite(TBSet set, long pid)
		throws TBStoreException {
		
		log4j.debug("MySQLTBGameStorer.acceptInvite(" + set.getSetId() + 
			", " + pid + ")");
		Connection con = null;
        PreparedStatement stmt = null;
		
		try {
			con = dbHandler.getConnection();

			stmt = con.prepareStatement(
				"update tb_set " +
				"set p1_pid = ?, p2_pid = ?, state = ? " +
				"where sid = ?");
			stmt.setLong(1, set.getPlayer1Pid());
			stmt.setLong(2, set.getPlayer2Pid());
			stmt.setString(3, Character.toString(set.getState()));
			stmt.setLong(4, set.getSetId());
			stmt.executeUpdate();
			
			for (int i = 0; i < 2; i++) {
				TBGame game = set.getGames()[i];
				if (game == null) break;
				
	            stmt = con.prepareStatement(
					"update tb_game " +
					"set p1_pid = ?, p2_pid = ?, state = ?, start_date = ?, " +
					"timeout_date = ?, last_move_date = ? " +
					"where tb_game.gid = ?");
				stmt.setLong(1, game.getPlayer1Pid());
				stmt.setLong(2, game.getPlayer2Pid());
				stmt.setString(3, Character.toString(game.getState()));
				stmt.setTimestamp(4, new Timestamp(game.getStartDate().getTime()));
				stmt.setTimestamp(5, new Timestamp(game.getTimeoutDate().getTime()));
				stmt.setTimestamp(6, new Timestamp(game.getLastMoveDate().getTime()));
				stmt.setLong(7, game.getGid());
	
				stmt.executeUpdate();
			}
			
	    } catch (SQLException se) {
			throw new TBStoreException(se);
	    } finally {
			if (stmt != null) {
				try { stmt.close(); } catch (SQLException se) {}
			}
			if (con != null) {
				try { dbHandler.freeConnection(con); } catch (SQLException se) {}
			}
	    }
	}

	public void cancelSet(TBSet set) throws TBStoreException {
		log4j.debug("MySQLTBGameStorer.cancelSet(" + set.getSetId() + ")");

		endSet(set);
        endGame(set.getGame1());
        if (set.getGame2() != null) {
            endGame(set.getGame2());
        }
	}
    public void declineCancel(TBSet set) throws TBStoreException {
        Connection con = null;
        PreparedStatement stmt = null;
        
        try {
            con = dbHandler.getConnection();

            stmt = con.prepareStatement(
                "update tb_set " +
                "set cancel_pid = 0, " +
                "cancel_msg = NULL " +
                "where tb_set.sid = ?");
            
            stmt.setLong(1, set.getSetId());

            stmt.executeUpdate();
                
        } catch (SQLException se) {
            throw new TBStoreException(se);
        } finally {
            if (stmt != null) {
                try { stmt.close(); } catch (SQLException se) {}
            }
            if (con != null) {
                try { dbHandler.freeConnection(con); } catch (SQLException se) {}
            }
        }
    }
    public void requestCancel(TBSet set, long requestorPid, String message) throws TBStoreException {
        Connection con = null;
        PreparedStatement stmt = null;
        
        try {
            con = dbHandler.getConnection();

            stmt = con.prepareStatement(
                "update tb_set " +
                "set cancel_pid = ?, " +
                "cancel_msg = ? " +
                "where tb_set.sid = ?");
            
            stmt.setLong(1, requestorPid);
            stmt.setString(2, message);
            stmt.setLong(3, set.getSetId());

            stmt.executeUpdate();
                
        } catch (SQLException se) {
            throw new TBStoreException(se);
        } finally {
            if (stmt != null) {
                try { stmt.close(); } catch (SQLException se) {}
            }
            if (con != null) {
                try { dbHandler.freeConnection(con); } catch (SQLException se) {}
            }
        }
    }
	public void endSet(TBSet set) throws TBStoreException {
		log4j.debug("MySQLTBGameStorer.endSet(" + set.getSetId() + ")");
		
		Connection con = null;
        PreparedStatement stmt = null;
		
		try {
			con = dbHandler.getConnection();

			String tbTable = " tb_set";
			if (set.getPlayer1Pid() == 23000000020606L || set.getPlayer2Pid() == 23000000020606L) {
				tbTable = " tb_set_ai";
			}
            stmt = con.prepareStatement(
				"update" + tbTable +
				" set state = ?, " +
				"completion_date = ? " +
				"where " + tbTable + ".sid = ?");
			
			stmt.setString(1, Character.toString(set.getState()));
			stmt.setTimestamp(2, new Timestamp(set.getCompletionDate().getTime()));
			stmt.setLong(3, set.getSetId());

			stmt.executeUpdate();
				
	    } catch (SQLException se) {
			throw new TBStoreException(se);
	    } finally {
			if (stmt != null) {
				try { stmt.close(); } catch (SQLException se) {}
			}
			if (con != null) {
				try { dbHandler.freeConnection(con); } catch (SQLException se) {}
			}
	    }
	}

	public void resignGame(TBGame game) throws TBStoreException {
		throw new UnsupportedOperationException("Not implemented, just " +
			"use endGame()");
	}

	public void endGame(TBGame game) throws TBStoreException {
		
		log4j.debug("MySQLTBGameStorer.endGame(" + game.getGid() + ")");
		
		Connection con = null;
        PreparedStatement stmt = null;
		
		try {
			con = dbHandler.getConnection();

			String tbTable = " tb_game";
			if (game.getPlayer1Pid() == 23000000020606L || game.getPlayer2Pid() == 23000000020606L) {
				tbTable = " tb_game_ai";
			}

            stmt = con.prepareStatement(
				"update" + tbTable +
				" set state = ?, " +
				"completion_date = ?, " +
				"winner = ? " +
				"where " + tbTable + ".gid = ?");
			
			stmt.setString(1, Character.toString(game.getState()));
			stmt.setTimestamp(2, new Timestamp(game.getCompletionDate().getTime()));
			stmt.setInt(3, game.getWinner());
			stmt.setLong(4, game.getGid());

			stmt.executeUpdate();
				
	    } catch (SQLException se) {
			throw new TBStoreException(se);
	    } finally {
			if (stmt != null) {
				try { stmt.close(); } catch (SQLException se) {}
			}
			if (con != null) {
				try { dbHandler.freeConnection(con); } catch (SQLException se) {}
			}
	    }
	}
	
	public void updateDPenteState(TBGame g, int state) throws TBStoreException {

		log4j.debug("MySQLTBGameStorer.updateDPenteState(" + g.getGid() + ", " +
			state + ")");
		
		Connection con = null;
		PreparedStatement stmt = null;

		try {
			con = dbHandler.getConnection();
			stmt = con.prepareStatement(
				"update tb_game " +
				"set dpente_state = ? " +
				"where gid = ?");
			stmt.setInt(1, state);
			stmt.setLong(2, g.getGid());
			stmt.executeUpdate();
			
	    } catch (SQLException se) {
			throw new TBStoreException(se);
	    } finally {
			if (stmt != null) {
				try { stmt.close(); } catch (SQLException se) {}
			}
			if (con != null) {
				try { dbHandler.freeConnection(con); } catch (SQLException se) {}
			}
	    }
	}
	
	public void dPenteSwap(TBGame g, boolean swap) throws TBStoreException {
		
		log4j.debug("MySQLGameTbStorer.dPenteSwap(" + g.getGid() + ", " + swap + ")");
		
		Connection con = null;
		PreparedStatement stmt = null;

		try {
			con = dbHandler.getConnection();
			stmt = con.prepareStatement(
				"update tb_game " +
				"set last_move_date = ?, " +
				"timeout_date = ?, " +
				"dpente_state = ?, " +
				"dpente_swap = ?, " +
				"p1_pid = ?, " +
				"p2_pid = ? " +
				"where gid = ?");
			stmt.setTimestamp(1, new Timestamp(g.getLastMoveDate().getTime()));
			stmt.setTimestamp(2, new Timestamp(g.getTimeoutDate().getTime()));
			stmt.setInt(3, g.getDPenteState());
			stmt.setString(4, g.didDPenteSwap() ? "Y" : "N");
			stmt.setLong(5, g.getPlayer1Pid());
			stmt.setLong(6, g.getPlayer2Pid());
			stmt.setLong(7, g.getGid());
			stmt.executeUpdate();
			
	    } catch (SQLException se) {
			throw new TBStoreException(se);
	    } finally {
			if (stmt != null) {
				try { stmt.close(); } catch (SQLException se) {}
			}
			if (con != null) {
				try { dbHandler.freeConnection(con); } catch (SQLException se) {}
			}
	    }
	}

	public void updateDaysOff(long pid, int weekend[]) throws TBStoreException{
		throw new UnsupportedOperationException("Not supported.");
	}
	public void destroy() {
		// nothing to destroy
	}

	public void restoreGame(long gid) throws TBStoreException {
		Connection con = null;
		PreparedStatement stmt = null;

		try {
			con = dbHandler.getConnection();

			stmt = con.prepareStatement(
					"delete from pente_game where gid = ?");
			stmt.setLong(1, gid);
			stmt.executeUpdate();

			stmt = con.prepareStatement(
					"delete from pente_move where gid = ?");
			stmt.setLong(1, gid);
			stmt.executeUpdate();

			stmt = con.prepareStatement(
					"update tb_game set state = 'A', timeout_date = ?, completion_date = NULL, winner = 0 where gid = ?");
			stmt.setTimestamp(1, new Timestamp((new Date()).getTime() + 1000L * 3600*24*5));
			stmt.setLong(2, gid);
			stmt.executeUpdate();

			stmt = con.prepareStatement(
					"update tb_set set state = 'A', completion_date = NULL where (gid1 = ?) or (gid2 = ?)");
			stmt.setLong(1, gid);
			stmt.setLong(2, gid);
			stmt.executeUpdate();

		} catch (SQLException se) {
			throw new TBStoreException(se);
		} finally {
			if (stmt != null) {
				try { stmt.close(); } catch (SQLException se) {}
			}
			if (con != null) {
				try { dbHandler.freeConnection(con); } catch (SQLException se) {}
			}
		}
	}

	public TBVacation getTBVacation(long pid) {

		Connection con = null;
		PreparedStatement stmt = null;
		ResultSet result = null;
		int hoursLeft = FLOATINGVACATIONDAYS*24;;
		Date lastPinchDate = null;
		TBVacation vacation = new TBVacation();

		try {
			try {

				con = dbHandler.getConnection();

				stmt = con.prepareStatement(
						"select hoursLeft, lastPinch " +
								"from tb_emergency_time " +
								"where pid = ?");
				stmt.setLong(1, pid);
				result = stmt.executeQuery();

				if (result.next()) {
					Calendar now = Calendar.getInstance();
					int currentYear = now.get(Calendar.YEAR);
					Calendar storedLastPinch = Calendar.getInstance();
					lastPinchDate = getDate(result, 2);
					if (lastPinchDate != null) {
						storedLastPinch.setTime(lastPinchDate);
						int lastPinchYear = storedLastPinch.get(Calendar.YEAR);

						if (lastPinchYear == currentYear) {
							hoursLeft = result.getInt(1);
							vacation.setLastPinched(lastPinchDate);
						}
					}
				}

				vacation.setHoursLeft(hoursLeft);
			}
			finally {
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

		} catch (SQLException sq) {
			log4j.debug("Problem getting emergency time for " + pid + "\n" + sq);
		}

		return vacation;
	}


	public void storeTBVacation(long pid, TBVacation vacation) {

		Connection con = null;
		PreparedStatement stmt = null;

		try {
			try {

				con = dbHandler.getConnection();

				stmt = con.prepareStatement(
						"insert into tb_emergency_time " +
								"(pid, hoursLeft, lastPinch) " +
								"values(?, ?, ?) ON DUPLICATE KEY UPDATE hoursLeft=VALUES(hoursLeft), lastPinch=VALUES(lastPinch)");
				stmt.setLong(1, pid);
				stmt.setInt(2, vacation.getHoursLeft());
				stmt.setTimestamp(3, new Timestamp(vacation.getLastPinched().getTime()));
				stmt.executeUpdate();

			}
			finally {
				if (stmt != null) {
					stmt.close();
				}
				if (con != null) {
					dbHandler.freeConnection(con);
				}
			}

		} catch (SQLException sq) {
			log4j.debug("Problem storeTBVacation emergency time for " + pid + "\n" + sq);
		}
	}


}
