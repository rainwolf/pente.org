package org.pente.tree;

import java.sql.*;

import org.apache.log4j.Category;
import org.pente.database.*;
import org.pente.game.GridState;
import org.pente.game.MoveData;


public class MySQLNodeSearcher implements NodeSearcher {

    private static final Category log4j = Category.getInstance(
            MySQLNodeSearcher.class.getName());

    private int game = 1; //pente
    private DBHandler dbHandler = null;
    private Node root;

    public MySQLNodeSearcher(DBHandler dbHandler) {
        this.dbHandler = dbHandler;
    }

    public Node loadAll() throws NodeSearchException {
        if (root == null) {
            root = loadPositionOnly(0);
        }
        return root;
    }

    public Node loadPosition(long hash) throws NodeSearchException {
        Node n = loadPositionOnly(hash);
        if (n == null) return null;
        try {
            Connection con = null;
            PreparedStatement stmt = null;
            ResultSet result = null;
            try {
                con = dbHandler.getConnection();
                stmt = con.prepareStatement(
                        "select next_key " +
                                "from node_next " +
                                "where hash_key = ?");
                stmt.setLong(1, hash);
                result = stmt.executeQuery();
                while (result.next()) {
                    n.addNextMove(loadPositionOnly(result.getLong(1)));
                }
            } finally {
                if (result != null) {
                    result.close();
                }
                if (stmt != null) {
                    stmt.close();
                }
                dbHandler.freeConnection(con);
            }
        } catch (SQLException s) {
            throw new NodeSearchException("loadPosition failed: " + hash, s);
        }
        return n;
    }

    private Node loadPositionOnly(long hash) throws NodeSearchException {
        Node n = null;
        try {
            Connection con = null;
            PreparedStatement stmt = null;
            ResultSet result = null;
            try {
                con = dbHandler.getConnection();
                stmt = con.prepareStatement("select player, " +
                        "position, rotation, depth, type, score, comment, parent_key " +
                        "from node " +
                        "where hash_key = ?");
                stmt.setLong(1, hash);
                result = stmt.executeQuery();
                if (result.next()) {
                    n = new SimpleNode();
                    n.setId(hash);
                    n.setHash(n.getId());
                    n.setPlayer(result.getInt(1));
                    n.setPosition(result.getInt(2));
                    n.setRotation(result.getInt(3));
                    n.setDepth(result.getInt(4));
                    n.setType(result.getInt(5));
                    n.setComment(result.getString(7));
                    n.setParentHash(result.getLong(8));
                    n.setStored(true);
                }
            } finally {
                if (result != null) {
                    result.close();
                }
                if (stmt != null) {
                    stmt.close();
                }
            }
        } catch (SQLException s) {
            throw new NodeSearchException("loadPosition failed: " + hash, s);
        }
        return n;
    }

    public Node loadPosition(GridState state) throws NodeSearchException {
        return loadPosition(state.getHash());
    }

    public void storeAll() throws NodeSearchException {
        // TODO Auto-generated method stub

    }

    public void storePosition(Node node) throws NodeSearchException {
        try {
            if (!node.isStored()) {
                log4j.info("mysql.storePosition(" + node.getHash() + ") insert");
                insertPosition(node);
            } else {
                if (node.nodeNeedsWrite()) {
                    log4j.info("mysql.storePosition(" + node.getHash() + ") update");
                    updatePosition(node);
                }
            }
            node.setStored(true);
        } catch (SQLException s) {
            throw new NodeSearchException("storePosition failed: " + node.getHash(), s);
        }
    }

    public void insertPosition(Node node) throws SQLException {

        Connection con = null;
        PreparedStatement stmt = null;
        try {
            con = dbHandler.getConnection();
            stmt = con.prepareStatement("insert into node" +
                    "(hash_key, parent_key, player, " +
                    "position, rotation, depth, type, score, comment) " +
                    "values(?, ?, ?, ?, ?, ?, ?, 0, ?)");
            stmt.setLong(1, node.getHash());
            stmt.setLong(2, node.getParent().getHash());
            stmt.setInt(3, node.getPlayer());
            stmt.setInt(4, node.getPosition());
            stmt.setInt(5, node.getRotation());
            stmt.setInt(6, node.getDepth());
            stmt.setInt(7, node.getType());
            stmt.setString(8, node.getComment());

            stmt.execute();
            stmt.close();
            stmt = con.prepareStatement("insert into node_next " +
                    "values(?, ?)");
            stmt.setLong(1, node.getParent().getHash());
            stmt.setLong(2, node.getHash());
            stmt.execute();
        } finally {
            if (stmt != null) {
                stmt.close();
            }
            dbHandler.freeConnection(con);
        }
    }

    public void updatePosition(Node node) throws SQLException {

        Connection con = null;
        PreparedStatement stmt = null;
        try {
            con = dbHandler.getConnection();
            stmt = con.prepareStatement("update node " +
                    "set type = ?, score = 0, comment = ? " +
                    "where hash_key = ?");
            stmt.setInt(1, node.getType());
            stmt.setString(2, node.getComment());
            stmt.setLong(3, node.getHash());

            stmt.execute();

        } finally {
            if (stmt != null) {
                stmt.close();
            }
            dbHandler.freeConnection(con);
        }
    }

    public void destroy() {
    }
}
