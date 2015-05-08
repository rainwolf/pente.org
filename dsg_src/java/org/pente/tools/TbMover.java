package org.pente.tools;

import org.apache.log4j.BasicConfigurator;
import org.pente.database.DBHandler;
import org.pente.database.MySQLDBHandler;
import org.pente.game.GameVenueStorer;
import org.pente.game.MySQLGameVenueStorer;
import org.pente.gameServer.core.*;
import org.pente.gameServer.event.DSGPreferenceEvent;
import java.sql.*;

public class TbMover {


	public static void main(String[] args) throws Throwable {

		BasicConfigurator.configure();

        DBHandler dbHandler = new MySQLDBHandler(
            args[0], args[1], args[2], args[3]);

		Connection con = dbHandler.getConnection();
        
        PreparedStatement stmt = con.prepareStatement("insert into tb_move values (?, ?, ?)");
        
        stmt.setLong(1, Long.parseLong(args[0]));
        stmt.setLong(2, 0);
        stmt.setLong(3, 360);
        stmt.execute();
        
        int z=1;
        for (int i=0;i<19;i++) {
            for (int j=0;j<18;j++) {
                stmt.setInt(2, z);
                stmt.setInt(3, i*19+j);
                stmt.execute();
                z++;
            }
        }
        for (int i=0;i<19;i++) {
            stmt.setInt(2, z);
            stmt.setInt(3, i*19+18);
            stmt.execute();
            z++;
        }
        
        stmt.close();
        
        dbHandler.destroy();
	}

}
