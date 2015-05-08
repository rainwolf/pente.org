package org.pente.jive;

import java.sql.*;

import org.pente.database.DBHandler;

import com.jivesoftware.base.database.*;

public class JiveDBHandler implements DBHandler {

	public Connection getConnection() throws SQLException {
		return ConnectionManager.getConnection();
	}

    // jive code doesn't do this...
	public void freeConnection(Connection con) throws SQLException {
        try {
            con.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
	}

	public void destroy() {
	}
}
