package org.pente.tools;

//import org.apache.log4j.BasicConfigurator;
import org.pente.database.DBHandler;
import org.pente.database.MySQLDBHandler;
import org.pente.game.*;
import org.pente.gameDatabase.swing.PlunkGameData;
import org.pente.gameDatabase.swing.importer.SGFGameFormat;

import java.io.File;
import java.io.FileWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class SGFExporter {

    private File        gameDir;
    private SGFGameFormat gameFormat;
    private GameStorer  gameStorer;
    private DBHandler   dbHandler;

    public static void main(String[] args) throws Throwable {

//        BasicConfigurator.configure();

        SGFExporter importer = new SGFExporter();
        
        String user = args[0];
        String password = args[1];
        String db = args[2];
        String host = args[3];
        importer.dbHandler = new MySQLDBHandler(user, password, db, host);
        importer.gameDir = new File(args[4]);

		final GameVenueStorer gvs = new MySQLGameVenueStorer(importer.dbHandler);

		MySQLPenteGameStorer p = new MySQLPenteGameStorer(
	        importer.dbHandler, gvs);
        importer.gameStorer = p;
        importer.gameFormat = new SGFGameFormat(); 
        
        List<Long> gids = importer.getGIDs();
        
        for (int i = 0; i < gids.size(); i++) {
            long gid = gids.get(i);
            try {
                GameData gameData = importer.gameStorer.loadGame(gid, null);
                PlunkGameData plunkGameData = new PlunkGameData(gameData);
                FileWriter out = new FileWriter(importer.gameDir+"/"+gid+".sgf");
                importer.gameFormat.format(plunkGameData, "dsg", out);
                out.close();
                System.out.println((i+1)+" of " + gids.size() + " written..");
            } catch (Exception e) {
                System.out.println("Ooooooops, not written..");
            }
        }

        importer.gameStorer.destroy();
    }
    
    public List<Long> getGIDs() throws Exception {
        Connection con = this.dbHandler.getConnection();
        PreparedStatement stmt = con.prepareStatement("SELECT gid FROM pente_game " + 
                "WHERE game < 3 AND " + 
                "(player1_rating > 1849 AND player2_rating > 1849) AND " + 
                "NOT (player1_pid = 23000000018079 or player2_pid = 23000000018079)");
        ResultSet result = stmt.executeQuery();
        List<Long> gidList = new ArrayList<>();
        while (result.next()) {
            long gid = result.getLong(1);
            gidList.add(gid);
        }
        result.close();
        stmt.close();
        con.close();
        return gidList;
    }
}
