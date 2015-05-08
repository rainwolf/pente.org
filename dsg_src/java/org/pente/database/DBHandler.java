/** DBHandler.java
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

import java.sql.*;

/** An interface to a class that can connect to a database
 *  @since 0.2
 *  @author dweebo (dweebo@www.pente.org)
 *  @version 0.2 02/12/2001
 */
public interface DBHandler {

    /** Get a connection to the database
     *  @return Connection The databae connection
     *  @exception Exception If a connection cannot be made
     */
    public Connection getConnection() throws SQLException;

    /** Free a connection to the database
     *  @param con The database connection to free
     *  @exception If the connection cannot be freed
     */
    public void freeConnection(Connection con) throws SQLException;

    /** Clean up any resources */
    public void destroy();
}