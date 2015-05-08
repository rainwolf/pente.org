package org.pente.gameServer.core.test;

import org.pente.database.*;
import org.pente.game.*;
import org.pente.gameServer.core.*;

import org.apache.log4j.*;
import java.sql.*;

public class MySQLDSGPlayerStorerTest {

	public static void main(String[] args) throws Throwable {

		BasicConfigurator.configure();
		
		DBHandler dbHandler = new MySQLDBHandler(
			args[0], args[1], args[2], args[3]);
		GameVenueStorer gvs = new MySQLGameVenueStorer(dbHandler);
		DSGPlayerStorer dps = new MySQLDSGPlayerStorer(
			dbHandler, gvs);
		
		DSGPlayerData d = dps.loadPlayer(22000000000144L);
		//System.out.println(d.hasAvatar());
		
		Connection con = dbHandler.getConnection();
		PreparedStatement stmt = con.prepareStatement("select * from test " +
			"where pid = ?");
		stmt.setLong(1, 22000000000002L);
		ResultSet result = stmt.executeQuery();
		if (result.next()) {
			System.out.println("id = " + result.getLong(2));
			System.out.println("name = " + result.getString(1));
		}
			
	}

}
