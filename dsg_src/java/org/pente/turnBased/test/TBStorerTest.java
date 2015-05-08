package org.pente.turnBased.test;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.net.URLEncoder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.StringTokenizer;

import org.pente.database.*;
import org.pente.game.*;
import org.pente.gameServer.client.MilliSecondGameTimer;
import org.pente.gameServer.core.*;
import org.pente.turnBased.*;
import org.pente.message.*;

import org.apache.log4j.*;

public class TBStorerTest {

	private static final DateFormat DATE_FORMAT = new SimpleDateFormat(
		"MM/dd/yyyy HH:mm");

    private static Category log4j = Category.getInstance(
			TBStorerTest.class.getName());

	public static void main(String[] args) throws Throwable {

//		int today=3;
//		int wk1=0;
//		int diff=(wk1-today);
//		if (diff < 0) diff=7+diff;
//		System.out.println("diff="+diff);
//		String str=",this is a test,";
//		StringTokenizer st = new StringTokenizer(str, ",");
//		while (st.hasMoreTokens()) {
//			System.out.println(st.nextToken());
//		}
		
		BasicConfigurator.configure();

        DBHandler dbHandler = new MySQLDBHandler(
            args[0], args[1], args[2], args[3]);

		GameVenueStorer gameVenueStorer = new MySQLGameVenueStorer(dbHandler);
		DSGPlayerStorer dsgPlayerStorer = new MySQLDSGPlayerStorer(dbHandler,
			gameVenueStorer);
		GameStorer gameStorer = new MySQLPenteGameStorer(dbHandler, gameVenueStorer);
		DSGMessageStorer dsgMessageStorer = new MySQLDSGMessageStorer(dbHandler);
		TBGameStorer storer = new CacheTBStorer(new MySQLTBGameStorer(dbHandler),
			dsgPlayerStorer, gameStorer, dsgMessageStorer);

		
		// test code
		
		
//		
//		TBGame g = new TBGame();
//		g.setPlayer1Pid(22000000000002L);
//		g.setInviterPid(22000000000002L);
//		g.setEventId(12);
//		g.setGame(51);
//		g.setDaysPerMove(7);
//		g.setRated(false);
//		g.addMessage("Test", 0, new Date());
//		
//		storer.createGame(g);
//		System.out.println("stored, gid = " + g.getGid());
//		
//		List<TBGame> l = storer.loadGames(22000000000144L);
//		for (TBGame t : l) {
//			System.out.println("loaded game " + t.getGid());
//		}
		
		TBGame g = storer.loadGame(50000000000080L);
		System.out.println(DATE_FORMAT.format(g.getTimeoutDate()));
		
		System.out.println("time left=" + Utilities.getTimeLeft(
			g.getTimeoutDate().getTime()));
		//storer.storeNewMove(5, 10, 260, null);
		
//		ByteArrayOutputStream bout = new ByteArrayOutputStream();
//		ObjectOutputStream out = new ObjectOutputStream(bout);
//		out.writeObject(g);
//		out.flush();
//		out.close();
//		String tbGameStr = new String(bout.toByteArray());
//		tbGameStr = URLEncoder.encode(tbGameStr);
//		System.out.println(tbGameStr);
		
		storer.destroy();
	}

}
