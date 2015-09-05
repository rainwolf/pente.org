package org.pente.gameServer.client.web;

import java.util.*;
import java.sql.*;

import org.pente.database.DBHandler;
import org.pente.game.*;
import org.pente.gameServer.core.*;

public class LeaderBoard {

	private long updateInterval = 5 * 60 * 1000; //5min
	private Map<Integer, List<Long>> leaders = new HashMap<Integer, List<Long>>();
	private Map<Integer, Long> nextUpdateTimes = new HashMap<Integer, Long>();
	
	private DBHandler dbHandler;
	private DSGPlayerStorer dsgPlayerStorer;
	
	public LeaderBoard(DBHandler dbHandler, DSGPlayerStorer dsgPlayerStorer) {
		this.dbHandler = dbHandler;
		this.dsgPlayerStorer = dsgPlayerStorer;
		
		for (Game g : GridStateFactory.getDisplayGames()) {
			leaders.put(g.getId(), new ArrayList<Long>(10));
			nextUpdateTimes.put(g.getId(), 0L);
		}
	}
	
	public List<DSGPlayerData> getLeaders(int game) throws DSGPlayerStoreException {
		updateLeaders(game);
		List<Long> ll = leaders.get(game);
		List<DSGPlayerData> ld = new ArrayList<DSGPlayerData>(10);
		for (Long l : ll) {
			ld.add(dsgPlayerStorer.loadPlayer(l));
		}
		return ld;
	}
	
	private void updateLeaders(int game) {
		List<Long> l = leaders.get(game);
		if (l == null || System.currentTimeMillis() > nextUpdateTimes.get(game)) {
			synchronized (this) {
				
				Connection con = null;
				PreparedStatement stmt = null;
				ResultSet result = null;
				
				
				
				try {
					con = dbHandler.getConnection();
					stmt = con.prepareStatement(
						"select p.pid " +
						"from dsg_player p, dsg_player_game g " +
						"where p.pid = g.pid " +
						"and p.status = '" + DSGPlayerData.ACTIVE + "' " +
						"and g.game = ? " +
						"and p.player_type = '" + DSGPlayerData.HUMAN + "' " +
						"and g.computer = 'N' " +
						"and (g.wins + g.losses + g.draws >= 50) " +
						// "and (g.wins + g.losses + g.draws >= 100) " +
						"and last_game_date > date_add(sysdate(), interval -1 month) " +
						// "and last_game_date > date_add(sysdate(), interval -1 " +
						// (GridStateFactory.isTurnbasedGame(game) ? "week) " : "month) ") +
						"order by g.rating desc " +
						"limit 0, 10");

					stmt.setInt(1, game);
					result = stmt.executeQuery();

					l.clear();
					
					while (result.next()) {
						l.add(result.getLong(1));
					}
					
					nextUpdateTimes.put(game, System.currentTimeMillis() + updateInterval);
					
				} catch (Exception e) {
					e.printStackTrace();
				} finally {
					if (result != null) {
						try { result.close(); } catch (SQLException s) {}
					}
					if (stmt != null) {
						try { stmt.close(); } catch (SQLException s) {}
					}
					if (con != null) {
						try { dbHandler.freeConnection(con); } catch (SQLException s) {}
					}
				}
			}
		}
	}
}
