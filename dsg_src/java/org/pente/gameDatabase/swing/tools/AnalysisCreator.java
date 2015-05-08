package org.pente.gameDatabase.swing.tools;

import java.io.*;
import java.sql.*;
import java.util.Date;
import java.util.*;

import org.pente.database.*;
import org.pente.game.*;
import org.pente.gameDatabase.GameStorerSearchRequestData;
import org.pente.gameDatabase.GameStorerSearchRequestFilterData;
import org.pente.gameDatabase.GameStorerSearchResponseData;
import org.pente.gameDatabase.HttpGameStorerSearcher;
import org.pente.gameDatabase.SimpleGameStorerSearchRequestData;
import org.pente.gameDatabase.SimpleGameStorerSearchRequestFilterData;
import org.pente.gameDatabase.SimpleGameStorerSearchResponseData;
import org.pente.gameDatabase.swing.*;
import org.pente.gameDatabase.swing.importer.SGFGameFormat;


public class AnalysisCreator {

	/**
	 * @param args
	 */
	public static void main(String[] args) throws Throwable {
		
		String name="dweebo";
		String password="wacky7mole";
		String host = "pente.org";
		
		/*
		HttpGameStorerSearcher searcher = null;
		
		StringBuffer encryptedPasswordBuf = new StringBuffer();
		StringBuffer sessionIdBuf = new StringBuffer();
		
		PlunkHttpLoader httpLoader = new PlunkHttpLoader(
			host, 80, name, password);
		
		
		int loginStatus = 500;
		try {
			loginStatus = httpLoader.remoteLogin(encryptedPasswordBuf, sessionIdBuf);
		} catch (Exception e) {
			System.err.println("Error logging in to pente.org");
			e.printStackTrace();
		}

		if (loginStatus == 404) {
			System.err.println("Invalid Login: name or password incorrect");
		}
		else if (loginStatus == 200) {
			System.out.println("logged in " + sessionIdBuf.toString() + ","+encryptedPasswordBuf.toString());
			searcher = new HttpGameStorerSearcher(
				host, 80, new PGNGameFormat(),
				"/gameServer/controller", name, encryptedPasswordBuf.toString());
			searcher.setCookie(sessionIdBuf.toString());
		}
		
		final GameStorerSearchRequestData request = 
			new SimpleGameStorerSearchRequestData();
		request.addMove(180);
		
		GameStorerSearchRequestFilterData filterData = new SimpleGameStorerSearchRequestFilterData();
		filterData.setStartGameNum(0);
		filterData.setEndGameNum(2000);
		filterData.setGame(GridStateFactory.PENTE);
		filterData.setPlayer1Name("zoeyk");
		request.setGameStorerSearchRequestFilterData(filterData);
		

		final GameStorerSearchResponseData response =
			new SimpleGameStorerSearchResponseData();

		System.out.println("start search");
		searcher.search(request, response);
		
		System.out.println("search done, found " + response.getGames().size() + " games");
		
		createAnalysisFile(new File("/analysis.sgf"), response.getGames());
		*/
		

		Vector games = new Vector();
		
		DBHandler dbHandler = new MySQLDBHandler("dsg_rw", "F1jtdpgb", "dsg", "localhost:3307");
		
		Connection con = null;
		PreparedStatement stmt = null;
		ResultSet result = null;
		PreparedStatement stmt2 = null;
		ResultSet result2 = null;
		try {
			
			con = dbHandler.getConnection();
			System.out.println("got connection");
			stmt = con.prepareStatement(
				"select g.gid, g.winner " +
				"from pente_game g, dsg_player_game dpg1, dsg_player_game dpg2 " +
				"where g.game = 1 " +
				"and g.player1_rating > 1800 " +
				"and g.player2_rating > 1800 " +
				"and dpg1.game = 1 " +
				"and dpg1.rating > 1800 " +
				"and dpg1.computer = 'N' " +
				"and (dpg1.wins + dpg1.losses) > 100 " +
				"and dpg1.pid = g.player1_pid " +
				"and dpg2.game = 1 " +
				"and dpg2.rating > 1800 " +
				"and dpg2.computer = 'N' " +
				"and (dpg2.wins + dpg2.losses) > 100 " +
				"and dpg2.pid = g.player2_pid " +
				"union " +
				"select g.gid, g.winner from pente_game g " +
				"where g.site_id in (3,4) " +
				"and ((g.player1_rating > 2000 and g.player2_rating > 2000) or g.player1_rating = 0)");
			
			stmt2 = con.prepareStatement(
				"select next_move " +
				"from pente_move " +
				"where gid = ? " +
				"and next_move != 361 " +
				"order by move_num");

			result = stmt.executeQuery();
			while (result.next()) {
				long gid = result.getLong(1);
				System.out.println(games.size() + " " + gid);
				GameData gd = new DefaultGameData();
				gd.setGame("Pente");
				stmt2.setLong(1, gid);
				result2 = stmt2.executeQuery();
				gd.addMove(180);
				gd.setWinner(result.getInt(2));
				while (result2.next()) {
					gd.addMove(result2.getInt(1));
				}
				result2.close();
				games.add(gd);
			}
			
		} finally {
			if (result != null) {
				result.close();
			}
			if (stmt != null) {
				stmt.close();
			}
			if (result2 != null) {
				result2.close();
			}
			if (stmt2 != null) {
				stmt2.close();
			}
			if (con != null) {
				dbHandler.freeConnection(con);
			}
			dbHandler.destroy();
		}
		createAnalysisFile(new File("/analysis.sgf"), games);
		
	}
	
	public static void createAnalysisFile(File file, Vector games) throws IOException {
		PlunkTree pt = new PlunkTree();
		pt.setCreated(new Date());
		pt.setCreator("dweebo");
		pt.setLastModified(new Date());
		pt.setName("test");
		pt.setVersion("1.0");
		pt.setCanEditProps(true);
		
		PlunkNode root = null;

		Map<Long, PlunkNode> nodes = new HashMap<Long, PlunkNode>();

		for (int k = 0; k < games.size(); k++) {
			GameData g = (GameData) games.elementAt(k);
			GridState gs = null;
			try {
				gs = GridStateFactory.createGridState(
					GridStateFactory.getGameId(g.getGame()), g);
			} catch (IllegalArgumentException i) {
				System.out.println("bad game " + g.getGameID());
				continue;
			}

			if (root == null) {
				root = new PlunkNode();
				root.setDepth(0);
				root.setHash(gs.getHash(0));
				root.setMove(gs.getMove(0));
				root.setRotation(gs.getRotation(0));
				root.setComments(g.getWinner()==1?"1/0":"0/1");
				root.setName("0");
				nodes.put(gs.getHash(0), root);
			}
			
			for (int i = 0; i < gs.getNumMoves(); i++) {

				PlunkNode exist = nodes.get(gs.getHash(i));
				if (exist != null) {
					int ct = Integer.parseInt(exist.getName());
					ct++;
					exist.setName(ct+"");
					
					int index = exist.getComments().indexOf("/");
					int win = Integer.parseInt(exist.getComments().substring(0, index));
					int loss = Integer.parseInt(exist.getComments().substring(index+1));
					if (g.getWinner() == i%2+1) win++;
					else loss++;
					exist.setComments(win+"/"+loss);
					
					continue;
				}
				PlunkNode p = nodes.get(gs.getHash(i - 1));
				if (p == null) {
					System.out.println("parent null");
				}
				PlunkNode e = new PlunkNode();
				e.setParent(p);
				
				GridState gs2 = GridStateFactory.createGridState(
					GridStateFactory.getGameId(g.getGame()));
				PlunkNode path[] = new PlunkNode[p.getDepth() + 1];
				PlunkNode t = p;
				while (t != null) {
					path[t.getDepth()] = t;
					t = t.getParent();
				}
				for (int m = 0; m < path.length; m++) {
					gs2.addMove(path[m].getMove());
				}
				
				int rot = gs.getMove(i);
    			int possMoves[] = gs2.getAllPossibleRotations(gs.getMove(i), 
    				gs.getRotation(i - 1));
    			boolean foundSame = false;
    			if (i > 1) {
	    			for (int pm : possMoves) {
	    				if (pm == gs.getMove(i)) {
	    					foundSame = true;
	    					break;
	    				}
	    			}
    			}
    			if (!foundSame) {
    				rot = gs2.rotateMoveToLocalRotation(
    					gs.getMove(i), gs.getRotation(i - 1));
    			}

    			gs2.addMove(rot);
				e.setMove(rot);
				e.setHash(gs2.getHash());
				e.setDepth(i);
				e.setRotation(gs2.getRotation());
				e.setName("1");
				e.setComments(g.getWinner()==i%2+1?"1/0":"0/1");
				//if (rot != gs.getMove(i)) {
				//	System.out.println("rotated " + gs.getMove(i) + " to " + rot);
				//}

				nodes.put(gs2.getHash(), e);
			}
		}
		
		trim(root, 0);
		

		FileWriter out = new FileWriter(file);
		SGFGameFormat sgf = new SGFGameFormat();
		sgf.format(root, 1, pt, out);

		out.close();
		System.out.println("done making file");
	}
	
	private static int trim(PlunkNode n, int pc) {
		if (!n.hasChildren()) return 0;
		else {
			int max = n.getChildCount();
			for (PlunkNode c : n.getChildren()) {
				int r = trim(c, n.getChildCount());
				if (r > max) {
					max = r;
				}
			}
			if (max == 1 && pc == 1) {
				n.getChildren().clear();
			}
			return max;
		}
	}
}
