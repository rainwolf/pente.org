package org.pente.jive;

import java.sql.*;

import javax.servlet.http.*;

import com.jivesoftware.base.*;
import org.apache.log4j.*;

import org.pente.database.*;

public class DSGAuthFactory extends AuthFactory {

    private static final Category log4j =
        Category.getInstance(DSGAuthFactory.class.getName());
        
    private static final String GET_AUTHORIZATION_SQL =
        "select player.pid " +
        "from player, dsg_player " +
        "where player.pid = dsg_player.pid " +
        "and player.name = ? " +
        "and dsg_player.status = 'A' " +
        "and dsg_player.password = ?";

    private DBHandler dbHandler;
    public DSGAuthFactory() {
        dbHandler = new JiveDBHandler();
    }

    // not used
    public AuthToken createAnonymousAuthToken() {
        log4j.debug("createAnonymousToken()");
        return new DSGAuthToken(-1L);
    }

    public AuthToken createAuthToken(
        HttpServletRequest request, HttpServletResponse response)
        throws UnauthorizedException  {

        String name = (String) request.getAttribute("name");
        String password = (String) request.getAttribute("password");
		log4j.debug("createAuthToken(request, response), name=" + name + ", password="+password);
		
        return createAuthToken(name, password);
    }
    
    public AuthToken createAuthToken(String username, String password)
        throws UnauthorizedException {

		log4j.debug("createAuthToken(" + username + "," + password + ")");
        if (username == null || password == null) {
            throw new UnauthorizedException();
        }

        Connection con = null;
        PreparedStatement stmt = null;
        ResultSet result = null;
        long userId = -1;
        
		try {
            con = dbHandler.getConnection();
            stmt = con.prepareStatement(GET_AUTHORIZATION_SQL);
            stmt.setString(1, username);
            stmt.setString(2, password);

            result = stmt.executeQuery();
            if (result.next()) {
	            userId = result.getLong(1);
            }
        }
        catch (Throwable t) {
            throw new UnauthorizedException(t);
        }
        finally {
            try {
                if (result != null) {
                    result.close();
                }
            } catch (SQLException ex) {
            }
            try {
                if (stmt != null) {
                    stmt.close();
                }
            } catch (SQLException ex) {
            }
            try {
                if (con != null) {
                    con.close();
                }
            } catch (SQLException ex) {
            }
        }
		if (userId == -1) {
			throw new UnauthorizedException();
		}
        return new DSGAuthToken(userId);
    }
}