package org.pente.tools;

import java.sql.*;
import java.io.*;
import java.util.*;

import org.apache.log4j.*;

import org.pente.database.*;

public class InsertSpeedNames {

    public static void main(String[] args) throws Throwable {

        File map = new File(args[0]);
        BufferedReader in = new BufferedReader(new FileReader(map));

        BasicConfigurator.configure();

        DBHandler dbHandler = null;
        Connection con = null;
        PreparedStatement stmt = null;
        ResultSet result = null;

        try {
            dbHandler = new MySQLDBHandler(
                    "dsg_test_rw", "***REMOVED***", "dsg_test2", "pente.org");

            con = dbHandler.getConnection();

            stmt = con.prepareStatement("insert into speed_mapping " +
                    "values(" +
                    "(select pid from player where name = ? and site_id=2), " +
                    "(select pid from player where name = ? and site_id=2))");

            String line = null;
            while ((line = in.readLine()) != null) {
                StringTokenizer st = new StringTokenizer(line, "=");
                String speed = st.nextToken().trim();
                String normal = st.nextToken().trim();
                System.out.println("starting map of:" + speed + "=" + normal + ".");

                stmt.setString(1, normal);
                stmt.setString(2, speed);

                stmt.executeUpdate();
            }

        } finally {
            if (result != null) {
                result.close();
            }
            if (stmt != null) {
                stmt.close();
            }
            if (dbHandler != null) {
                dbHandler.freeConnection(con);
                dbHandler.destroy();
            }
        }
    }
}
