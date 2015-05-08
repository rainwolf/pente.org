package org.pente.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class HSQLDBHandler implements DBHandler {

	private String path;
	
	public HSQLDBHandler(String path) throws Exception {
		this.path = path;
		Class.forName("org.hsqldb.jdbcDriver");
	}
	
	public void destroy() {
		Connection con = null;
		try {
			con = getConnection();
			Statement st = con.createStatement();

			st.execute("SHUTDOWN");
			con.close();
			
		} catch (SQLException se) {}

	}

	public void freeConnection(Connection con) throws SQLException {
		con.close();
	}

	public Connection getConnection() throws SQLException {
		return DriverManager.getConnection(
			"jdbc:hsqldb:file:" + path,"sa",""); 
	}

}
