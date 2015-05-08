/** MySQLDBHandler.java
 *  Copyright (C) 2001 Dweebo's Stone Games (http://www.pente.org/)
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, you can find it online at
 *  http://www.gnu.org/copyleft/gpl.txt
 */


package org.pente.database;

import java.util.*;
import java.sql.*;
import javax.naming.*;
import javax.sql.*;

import org.apache.log4j.*;
import org.apache.commons.dbcp.*;

import com.mysql.jdbc.jdbc2.optional.MysqlDataSource;

/** An implementation of a DBHandler that can connect to a mySQL database.
 *  @since 0.2
 *  @author dweebo (dweebo@www.pente.org)
 *  @version 0.2 02/12/2001
 */
public class MySQLDBHandler implements DBHandler {

    /** A boolean true is stored in the database with this constant */
    public static final String YES =    "Y";

    /** A boolean false is stored in the database with this constant */
    public static final String NO =     "N";

    private static final Category log4j = Category.getInstance(
        MySQLDBHandler.class.getName());

    private DataSource dataSource;
    
    public MySQLDBHandler(String user, String password, String db) throws Throwable {
        this(user, password, db, "localhost");
    }

    /** This constructor is used by standalone programs */
    public MySQLDBHandler(String user, String password, String db, String host) throws Throwable {
//        BasicDataSource basicDataSource = new BasicDataSource();
//        basicDataSource.setMaxActive(20);
//        basicDataSource.setMaxIdle(5);
//        basicDataSource.setMaxWait(10000);
//        basicDataSource.setUsername(user);
//        basicDataSource.setPassword(password);
//        basicDataSource.setDriverClassName("com.mysql.jdbc.Driver");
//        basicDataSource.setUrl("jdbc:mysql://" + host + "/" + db + "?useServerPrepStmts=true");

//        dataSource = basicDataSource;
        log4j.info("MySQLDBHandler("+user+","+password+","+db+","+host);
    	MysqlDataSource ds = new MysqlDataSource();
    	ds.setUser(user);
    	ds.setPassword(password);
    	ds.setDatabaseName(db);
    	ds.setUseServerPreparedStmts(true);
    	ds.setUrl("jdbc:mysql://" + host + "/" + db);
    	
    	dataSource = ds;
    }

    public MySQLDBHandler(boolean jndi, String resourceName) throws Throwable {

        Context ctx = new InitialContext();
        if (ctx == null ) {
            throw new Exception("No Context in MySQLDBHandler");
        }
    
        log4j.info("Creating MySQLDBHandler.");
        dataSource = (DataSource) ctx.lookup("java:comp/env/jdbc/" + resourceName);
    }

    /** Destroy the broker */
    public void destroy() {

    }

    /** Get a connection to the database
     *  @return Connection The databae connection
     *  @exception Exception If a connection cannot be made
     */
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    /** Free a connection to the database
     *  @param con The database connection to free
     *  @exception If the connection cannot be freed
     */
    public void freeConnection(Connection con) throws SQLException {
        try {
            con.close();
        } catch (SQLException ex) {
            log4j.error("Error closing connection.", ex);
        }
    }

    /** Get a boolean value from the database
     *  @param result The string to convert to a boolean
     *  @return boolean The boolean value of the result
     */
    public static boolean getBooleanValueFromDBString(String result) {
        return result.equals(YES);
    }

    /** Get a string to store a boolean value in the database
     *  @param data The boolean value to strore
     *  @return String The boolean value string
     */
    public static String getDBStringFromBooleanValue(boolean data) {
        return (data) ? YES : NO;
    }

    /** Lock a table so no other connection can modify the table.  MySQL doesn't
     *  provide row level locking yet.
     *  @param table The table name to lock
     *  @param con The database connection to use to lock the tables
     *  @exception Exception If the table cannot be locked
     */
    public static void lockTable(String table, Connection con) throws Exception {
        Statement stmt = con.createStatement();
        stmt.executeUpdate("lock tables " + table + " write");
    }

    /** Locks a group of tables so no other connecton can modify the table.  MySQL
     *  doesn't provide row level locking yet.
     *  @param tables A vector of String table names
     *  @param con The database connection to use to lock the tables
     *  @exception Exception If the tables cannot be locked
     */
    public static void lockTables(Vector tables, Connection con) throws Exception {
        Statement stmt = con.createStatement();
        String lock = "lock tables";
        for (int i = 0; i < tables.size(); i++) {
            if (i != 0) {
                lock += ",";
            }
            lock += " " + (String) tables.elementAt(i) + " write";
        }

        stmt.executeUpdate(lock);
    }

    /** Unlock all tables that are locked by this connection
     *  @param con The database connection to use to unlock the tables
     *  @exception Exception If the tables cannot be unlocked
     */
    public static void unLockTables(Connection con) throws Exception {
        Statement stmt = con.createStatement();
        stmt.executeUpdate("unlock tables");
    }
}
