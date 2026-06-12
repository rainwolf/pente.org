package org.pente.gameServer.core;

import java.sql.*;
import java.util.*;

import org.pente.game.*;
import org.pente.database.DBHandler;

import org.apache.log4j.*;

public class MySQLServerStorer {

    private static final Category log4j = Category.getInstance(
            MySQLServerStorer.class.getName());

    public static void addServer(DBHandler dbHandler, ServerData data)
            throws Throwable {

        Connection con = null;
        PreparedStatement stmt = null;
        ResultSet result = null;
        try {
            con = dbHandler.getConnection();
            stmt = con.prepareStatement(
                    "insert into dsg_server " +
                            "(name, port, tournament, active, creation_date, last_mod_date, private) " +
                            "values(?, ?, ?, 'Y', sysdate(), sysdate(), ?)");
            stmt.setString(1, data.getName());
            stmt.setInt(2, data.getPort());
            stmt.setString(3, data.isTournament() ? "Y" : "N");
            stmt.setString(4, data.isPrivateServer() ? "Y" : "N");
            stmt.executeUpdate();

            stmt.close();
            stmt = con.prepareStatement("select max(id) from dsg_server");
            result = stmt.executeQuery();
            result.next();
            data.setServerId(result.getInt(1));
            stmt.close();

            // insert into dsg_server_games
            stmt = con.prepareStatement("insert into dsg_server_game " +
                    "values(?, ?, ?)");
            for (Iterator it = data.getGameEvents().iterator(); it.hasNext(); ) {
                GameEventData g = (GameEventData) it.next();
                stmt.setInt(1, data.getServerId());
                stmt.setInt(2, g.getEventID());
                stmt.setInt(3, g.getGame());
                stmt.execute();
            }

            stmt.close();
            stmt = con.prepareStatement("insert into dsg_server_message " +
                    "values(?, ?, ?)");
            for (int i = 0; i < data.getLoginMessages().size(); i++) {
                String message = (String) data.getLoginMessages().get(i);
                stmt.setInt(1, data.getServerId());
                stmt.setInt(2, i);
                stmt.setString(3, message);
                stmt.execute();
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
        }
    }

    /**
     * Idempotently make a server offer each of the given games under the given
     * event name (e.g. "Live Game" or "King of the Hill").  Used at boot to keep
     * a server's offerings in sync with GridStateFactory, replacing the manual
     * dsg_server_game inserts in the old add_dummy_*.sql scripts.
     *
     * A game is added only if the server exists, a matching game_event row
     * exists, and the server does not already offer that game.  Where a
     * (game, name, site) has more than one game_event row (legacy duplicates),
     * min(eid) is used so the server still maps to a single offering.
     */
    public static void addServerGames(DBHandler dbHandler, int serverId,
            int siteId, int[] games, String eventName) throws Throwable {

        Connection con = null;
        PreparedStatement stmt = null;
        try {
            con = dbHandler.getConnection();
            stmt = con.prepareStatement(
                    "insert into dsg_server_game (server_id, event_id, game) " +
                    "select d.id, ge.eid, ge.game " +
                    "from dsg_server d " +
                    "join game_event ge on ge.site_id = ? and ge.game = ? " +
                    "  and ge.name = ? and ge.eid = (" +
                    "    select min(eid) from game_event " +
                    "    where site_id = ? and game = ? and name = ?) " +
                    "where d.id = ? " +
                    "and not exists (select 1 from dsg_server_game sg " +
                    "  where sg.server_id = d.id and sg.game = ?)");
            for (int game : games) {
                stmt.setInt(1, siteId);
                stmt.setInt(2, game);
                stmt.setString(3, eventName);
                stmt.setInt(4, siteId);
                stmt.setInt(5, game);
                stmt.setString(6, eventName);
                stmt.setInt(7, serverId);
                stmt.setInt(8, game);
                stmt.executeUpdate();
            }
        } finally {
            if (stmt != null) {
                stmt.close();
            }
            if (con != null) {
                dbHandler.freeConnection(con);
            }
        }
    }

    public static void removeServer(DBHandler dbHandler, long id)
            throws Throwable {

        Connection con = null;
        PreparedStatement stmt = null;
        ResultSet result = null;
        try {
            con = dbHandler.getConnection();
            stmt = con.prepareStatement(
                    "update dsg_server " +
                            "set active='N', last_mod_date=sysdate() " +
                            "where id=?");
            stmt.setLong(1, id);
            stmt.executeUpdate();

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
        }
    }

    public static List<ServerData> getActiveServers(DBHandler dbHandler,
                                        GameVenueStorer gameVenueStorer) throws Throwable {

        Connection con = null;
        PreparedStatement stmt = null;
        PreparedStatement stmt2 = null;
        PreparedStatement stmt3 = null;
        PreparedStatement stmt4 = null;
        ResultSet result = null;
        ResultSet result2 = null;
        ResultSet result3 = null;
        ResultSet result4 = null;
        List<ServerData> servers = new ArrayList<>();
        try {
            con = dbHandler.getConnection();
            stmt = con.prepareStatement(
                    "select id, name, port, tournament, private " +
                            "from dsg_server " +
                            "where active='Y'");
            stmt2 = con.prepareStatement(
                    "select g.game, g.event_id, e.name " +
                            "from dsg_server_game g, game_event e " +
                            "where g.server_id = ? " +
                            "and g.event_id = e.eid");
            stmt3 = con.prepareStatement(
                    "select message " +
                            "from dsg_server_message " +
                            "where server_id = ? " +
                            "order by message_seq");
            stmt4 = con.prepareStatement(
                    "select p.name " +
                            "from player p, dsg_server_access a " +
                            "where a.server_id = ? " +
                            "and a.pid = p.pid");

            result = stmt.executeQuery();
            while (result.next()) {
                ServerData d = new ServerData();
                d.setServerId(result.getInt(1));
                d.setName(result.getString(2));
                d.setPort(result.getInt(3));
                d.setTournament(result.getString(4).equals("Y"));
                d.setPrivateServer(result.getString(5).equals("Y"));
                servers.add(d);

                // get games played at server
                stmt2.setInt(1, d.getServerId());
                result2 = stmt2.executeQuery();
                while (result2.next()) {
                    GameEventData g = new SimpleGameEventData();
                    g.setGame(result2.getInt(1));
                    g.setEventID(result2.getInt(2));
                    g.setName(result2.getString(3));
                    d.addGameEvent(g);
                }
                result2.close();

                // get login messages
                stmt3.setInt(1, d.getServerId());
                result3 = stmt3.executeQuery();
                while (result3.next()) {
                    d.addLoginMessage(result3.getString(1));
                }
                result3.close();

                if (d.isPrivateServer()) {
                    stmt4.setInt(1, d.getServerId());
                    result4 = stmt4.executeQuery();
                    while (result4.next()) {
                        d.addPlayer(result4.getString(1));
                    }
                }
            }

        } finally {
            if (result != null) {
                result.close();
            }
            if (result2 != null) {
                result2.close();
            }
            if (result3 != null) {
                result3.close();
            }
            if (stmt != null) {
                stmt.close();
            }
            if (stmt2 != null) {
                stmt2.close();
            }
            if (stmt3 != null) {
                stmt3.close();
            }
            if (con != null) {
                dbHandler.freeConnection(con);
            }
        }
        return servers;
    }

    public static void addPlayerAccess(DBHandler dbHandler, ServerData data, DSGPlayerData player)
            throws SQLException {
        Connection con = null;
        PreparedStatement stmt = null;
        try {
            con = dbHandler.getConnection();
            stmt = con.prepareStatement(
                    "insert into dsg_server_access " +
                            "(server_id, pid) " +
                            "values(?, ?)");
            stmt.setInt(1, data.getServerId());
            stmt.setLong(2, player.getPlayerID());
            stmt.executeUpdate();
        } finally {

            if (stmt != null) {
                stmt.close();
            }

            if (con != null) {
                dbHandler.freeConnection(con);
            }
        }
    }

    public static void removePlayerAccess(DBHandler dbHandler, ServerData data, DSGPlayerData player)
            throws SQLException {
        Connection con = null;
        PreparedStatement stmt = null;
        try {
            con = dbHandler.getConnection();
            stmt = con.prepareStatement(
                    "delete from dsg_server_access " +
                            "where server_id = ? and pid = ?");
            stmt.setInt(1, data.getServerId());
            stmt.setLong(2, player.getPlayerID());
            stmt.executeUpdate();
        } finally {

            if (stmt != null) {
                stmt.close();
            }

            if (con != null) {
                dbHandler.freeConnection(con);
            }
        }
    }

    public static void updateServerMessages(DBHandler dbHandler, ServerData data)
            throws Throwable {

        Connection con = null;
        PreparedStatement stmt = null;
        try {
            con = dbHandler.getConnection();

            stmt = con.prepareStatement(
                    "delete from dsg_server_message " +
                            "where server_id = ?");
            stmt.setInt(1, data.getServerId());
            stmt.executeUpdate();

            stmt.close();

            stmt = con.prepareStatement("insert into dsg_server_message " +
                    "values(?, ?, ?)");
            for (int i = 0; i < data.getLoginMessages().size(); i++) {
                String message = (String) data.getLoginMessages().get(i);
                stmt.setInt(1, data.getServerId());
                stmt.setInt(2, i);
                stmt.setString(3, message);
                stmt.execute();
            }

        } finally {
            if (stmt != null) {
                stmt.close();
            }
            if (con != null) {
                dbHandler.freeConnection(con);
            }
        }
    }
}