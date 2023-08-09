package org.pente.tools;

import java.sql.*;
import java.util.*;

import org.apache.log4j.*;
import org.pente.database.*;

public class UpdatePenteMove {

    public static void main(String[] args) throws Throwable {
        BasicConfigurator.configure();

        DBHandler dbHandler = null;
        Connection con = null;
        PreparedStatement stmt = null;
        PreparedStatement stmt2 = null;
        ResultSet result = null;
        List<Long> gids = new ArrayList<Long>(170000);
        long startTime = System.currentTimeMillis();
        try {
            dbHandler = new MySQLDBHandler(
                    args[0], args[1], args[2], args[3]);

            con = dbHandler.getConnection();


            stmt = con.prepareStatement("select gid from pente_game");
            result = stmt.executeQuery();
            while (result.next()) {
                gids.add(result.getLong(1));
            }
            System.out.println("got gids, count=" + gids.size());
            stmt.close();
            result.close();

            MySQLDBHandler.lockTable("pente_move", con);

            stmt = con.prepareStatement("select next_move from pente_move " +
                    "where gid=? order by move_num");
            stmt2 = con.prepareStatement("update pente_move set next_move=? " +
                    "where gid=? and move_num=?");
            int numMoves = 0;
            int moves[] = new int[400];
            int batchSize = 0;
            int numBatches = 0;
            for (int i = 0; i < gids.size(); i++) {
                numMoves = 0;
                stmt.setLong(1, gids.get(i));
                result = stmt.executeQuery();
                while (result.next()) {
                    moves[numMoves++] = result.getInt(1);
                    if (numMoves == 400) break;
                }
                result.close();

                stmt2.setLong(2, gids.get(i));
                // know that first move is 180, so just store next move
                for (int j = 1; j < numMoves; j++) {
                    stmt2.setInt(1, moves[j]);
                    stmt2.setInt(3, j - 1);
                    stmt2.addBatch();
                    batchSize++;
                }
                // set last move in game's next move to 361-indicates null
                stmt2.setInt(1, 361);
                stmt2.setInt(3, numMoves - 1);
                stmt2.addBatch();
                batchSize++;

                if (batchSize > 1000) {
                    batchSize = 0;
                    stmt2.executeBatch();
                    System.out.println("Updated up to " + i + ", " +
                            1000 * ++numBatches + " rows updated");
                }
            }
            if (batchSize > 0) {
                stmt2.executeBatch();
                System.out.println("Final rows updated");
            }

        } finally {
            if (result != null) {
                result.close();
            }
            if (stmt != null) {
                stmt.close();
            }
            if (stmt2 != null) {
                stmt2.close();
            }
            if (dbHandler != null && con != null) {
                MySQLDBHandler.unLockTables(con);
                dbHandler.freeConnection(con);
                dbHandler.destroy();
            }
        }
        System.out.println("total time = " + (System.currentTimeMillis() - startTime));
    }
}
