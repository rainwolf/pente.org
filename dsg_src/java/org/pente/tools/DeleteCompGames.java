package org.pente.tools;

import java.sql.*;
import java.util.*;

import org.apache.log4j.*;
import org.pente.database.*;

public class DeleteCompGames {


    public static void main(String[] args) throws Throwable {
        BasicConfigurator.configure();

        DBHandler dbHandler = null;
        Connection con = null;
        PreparedStatement stmt = null;
        PreparedStatement stmt2 = null;
        ResultSet result = null;
        List<Long> gids = new ArrayList<Long>(90000);
        long startTime = System.currentTimeMillis();
        try {
            dbHandler = new MySQLDBHandler(
                    args[0], args[1], args[2], args[3]);

            con = dbHandler.getConnection();


            stmt = con.prepareStatement("select gid from pente_game " +
                    "where player1_type='1' or player2_type='1'");
            result = stmt.executeQuery();
            while (result.next()) {
                gids.add(result.getLong(1));
            }
            System.out.println("got gids, count=" + gids.size());
            stmt.close();
            result.close();

            stmt = con.prepareStatement("delete " +
                    "from pente_move where gid=?");
            stmt2 = con.prepareStatement("delete " +
                    "from pente_game where gid=?");
            int i = 0;
            int rowsDeleted = 0;
            while (i < gids.size()) {
                for (int j = 0; j < 100 && i < gids.size(); j++, i++) {
                    stmt.setLong(1, gids.get(i));
                    stmt2.setLong(1, gids.get(i));
                    stmt.addBatch();
                    stmt2.addBatch();
                }
                int r[] = stmt.executeBatch();
                stmt2.executeBatch();
                for (int k = 0; k < r.length; k++) rowsDeleted += r[k];
                System.out.println("Deleted up to " + i + ", " + rowsDeleted + " rows deleted");
            }

        } finally {
            if (result != null) {
                result.close();
            }
            if (stmt != null) {
                stmt.close();
            }
            if (dbHandler != null && con != null) {
                dbHandler.freeConnection(con);
                dbHandler.destroy();
            }
        }
        System.out.println("total time = " + (System.currentTimeMillis() - startTime));
    }

}
