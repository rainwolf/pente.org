package org.pente.tools;

import java.sql.*;

import org.apache.log4j.*;

import org.pente.database.*;
import org.pente.game.*;
import org.pente.gameServer.core.*;

public class MatchSpeedName {

    public static void main(String[] args) throws Throwable {

        BasicConfigurator.configure();
        
        DBHandler dbHandler = null; 
        GameVenueStorer gameVenueStorer = null;
        DSGPlayerStorer dsgPlayerStorer = null;        
        Connection con = null;
        PreparedStatement matchEmailStmt = null;
        PreparedStatement matchPasswordStmt = null;
        ResultSet result = null;
        
        try {
            dbHandler = new MySQLDBHandler(
                "dsg_test_ro", "8ricky4", "dsg_test2", "pente.org");
            gameVenueStorer = new MySQLGameVenueStorer(dbHandler);
            dsgPlayerStorer = new 
                MySQLDSGPlayerStorer(dbHandler, gameVenueStorer);
            con = dbHandler.getConnection();
            
            matchEmailStmt = con.prepareStatement("select p.name, d.last_login_date," +
                "dg.wins + dg.losses, dg.rating " +
                "from player p, dsg_player d, dsg_player_game dg " +
                "where p.pid = d.pid " +
                "and p.pid = dg.pid " +
                "and dg.game = 1 " +
                "and dg.computer = 'N' " +
                "and d.email = ? and p.name != ?");
            matchPasswordStmt = con.prepareStatement("select p.name, d.last_login_date," +
                "dg.wins + dg.losses, dg.rating " +
                "from player p, dsg_player d, dsg_player_game dg " +
                "where p.pid = d.pid " +
                "and p.pid = dg.pid " +
                "and dg.game = 1 " +
                "and dg.computer = 'N' " +
                "and d.password = ? and p.name != ?");
            
            for (int i = 0; i < args.length; i++) {
                String name = args[i];
                DSGPlayerData data = dsgPlayerStorer.loadPlayer(name);
                if (data == null) {
                    System.out.println(name + " not found.");
                }
                System.out.print("lookup " + name + " " + data.getLastLoginDate() + " ");
                if (data.getPlayerGameData(1) != null) {
                    System.out.print(data.getPlayerGameData(1).getTotalGames() + " " + data.getPlayerGameData(1).getRating());
                }
                System.out.println();
                
                matchEmailStmt.setString(1, data.getEmail());
                matchEmailStmt.setString(2, name);
                result = matchEmailStmt.executeQuery();
                while (result.next()) {
                    System.out.println("match email on player " + 
                        result.getString(1) + " " + result.getTimestamp(2) + " " + result.getString(3) + " " + result.getString(4));
                }
                result.close();
    
                matchPasswordStmt.setString(1, data.getPassword());
                matchPasswordStmt.setString(2, name);
                result = matchPasswordStmt.executeQuery();
                while (result.next()) {
                    System.out.println("match password on player " + 
                        result.getString(1) + " " + result.getTimestamp(2) + " " + result.getString(3) + " " + result.getString(4));
                }
                System.out.println();
            }

        } finally {
            if (result != null) {
                result.close();
            }
            if (matchEmailStmt != null) {
                matchEmailStmt.close();
            }
            if (dbHandler != null) {
                dbHandler.freeConnection(con);
                dbHandler.destroy();
            }
        }
    }
}
