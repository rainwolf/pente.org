package org.pente.tools;

import org.apache.log4j.BasicConfigurator;
import org.pente.database.DBHandler;
import org.pente.database.MySQLDBHandler;
import org.pente.game.GameVenueStorer;
import org.pente.game.MySQLGameVenueStorer;
import org.pente.gameServer.core.*;
import org.pente.gameServer.event.DSGPreferenceEvent;

import java.sql.*;

public class RatingsDrawFixer {


    public static void main(String[] args) throws Throwable {

        BasicConfigurator.configure();

        DBHandler dbHandler = new MySQLDBHandler(
                args[0], args[1], args[2], args[3]);

        GameVenueStorer gvs = new MySQLGameVenueStorer(dbHandler);
        DSGPlayerStorer dsgPlayerStorer = new MySQLDSGPlayerStorer(dbHandler, gvs);

        Connection con = null;
        PreparedStatement stmt = null;
        ResultSet result = null;
        long pid = Long.parseLong(args[4]);
        try {
            con = dbHandler.getConnection();
            stmt = con.prepareStatement(
                    "select g.gid, g.player1_pid, g.player2_pid, g.player1_rating, " +
                            "g.player2_rating, g.play_date, g.winner " +
                            "from pente_game g " +
                            "where (g.player1_pid = ? or g.player2_pid = ?) " +
                            "and g.rated = 'Y' " +
                            "and g.game = 1 " +
                            "and g.gid < 50000000000000 " +
                            "and g.play_date > '2008-08-01' " +
                            "order by g.play_date desc");

            stmt.setLong(1, pid);
            stmt.setLong(2, pid);

            result = stmt.executeQuery();
            int lastR = -1;
            long lastGid = -1;
            int lastWinner = -1;
            long lastP1 = -1;
            long lastP2 = -1;
            int lastPos = -1;
            int lastR1 = -1;
            int lastR2 = -1;
            while (result.next()) {
                long gid = result.getLong(1);
                long p1 = result.getLong(2);
                long p2 = result.getLong(3);
                int r1 = result.getInt(4);
                int r2 = result.getInt(5);
                int winner = result.getInt(7);
                if ((p1 == pid && ((lastP1 == p1 && r1 == lastR1 && r2 == lastR2) || (lastP1 == p2 && r1 == lastR2 && r2 == lastR1))) ||
                        (p2 == pid && ((lastP2 == p2 && r2 == lastR2 && r1 == lastR1) || (lastP2 == p1 && r2 == lastR1 && r1 == lastR2)))) {

                    boolean win = true;
                    if (lastPos != lastWinner) win = false;
                    System.out.println("no ratings change: " + lastGid + ", " + (win ? "won" : "lost"));

                    DSGPlayerData p1D = dsgPlayerStorer.loadPlayer(lastP1);
                    DSGPlayerData p2D = dsgPlayerStorer.loadPlayer(lastP2);
                    DSGPlayerGameData p1Gd = p1D.getPlayerGameData(1);
                    DSGPlayerGameData p2Gd = p2D.getPlayerGameData(1);

                    p1Gd.setDraws(0);
                    p2Gd.setDraws(0);

                    double cr1 = p1Gd.getRating();
                    double cr2 = p2Gd.getRating();
                    p1Gd.setRating(lastR1);
                    p2Gd.setRating(lastR2);

                    DSGPlayerGameData p1GdCopy =
                            p1Gd.getCopy();

                    if (lastWinner == 1) {
                        p1Gd.gameOver(DSGPlayerGameData.WIN, p2Gd, 32);
                        p2Gd.gameOver(DSGPlayerGameData.LOSS, p1GdCopy, 32);
                    } else {
                        p1Gd.gameOver(DSGPlayerGameData.LOSS, p2Gd, 32);
                        p2Gd.gameOver(DSGPlayerGameData.WIN, p1GdCopy, 32);
                    }

                    System.out.println("ratings change for p1: " + lastP1 + " " + lastR1 + " to " + p1Gd.getRating());
                    System.out.println("ratings change for p2: " + lastP2 + " " + lastR2 + " to " + p2Gd.getRating());

                    // load both player's data from dsgPlayerStorer
                    // set draws to 0 for game=pente
                    // set rating as rating at time of draw
                    // update ratings for both and store
                    //break;
                }

                if (p1 == pid) {
                    lastR = r1;
                    lastPos = 1;
                } else {
                    lastR = r2;
                    lastPos = 2;
                }
                lastP1 = p1;
                lastP2 = p2;
                lastR1 = r1;
                lastR2 = r2;
                lastGid = gid;
                lastWinner = winner;
                System.out.println("check " + gid + "ok " + lastP1 + ", " + lastP2 + ", " + lastR1 + ", " + lastR2 + ", " + result.getTimestamp(6));
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
            dbHandler.destroy();
        }

    }

}
