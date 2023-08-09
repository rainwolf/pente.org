package org.pente.tools;

import java.io.*;
import java.sql.*;

import org.pente.database.*;
import org.pente.gameServer.server.*;

public class EncryptDbPasswords {

    public static void main(String[] args) throws Throwable {

        DBHandler dbHandler = new MySQLDBHandler("dsg_rw", "dsg_rw", "dsg");
        PasswordHelper passwordHelper = new PasswordHelper(new File(
                "/dsg/dev/conf/cipher.key"));

        Connection con = null;
        PreparedStatement stmt = null;
        PreparedStatement stmt2 = null;
        ResultSet result = null;

        int count = 0;

        try {
            con = dbHandler.getConnection();
            stmt = con.prepareStatement("select pid, password from dsg_player");
            stmt2 = con.prepareStatement("update dsg_player set password = ? where pid = ?");
            result = stmt.executeQuery();
            while (result.next()) {
                String pid = result.getString(1);
                String password = result.getString(2);
                String newPassword = passwordHelper.encrypt(password);

                stmt2.setString(1, newPassword);
                stmt2.setString(2, pid);
                stmt2.executeUpdate();

                System.out.println(++count + " Encrypted password for: " + pid +
                        ", " + newPassword);
            }
        } finally {
            if (result != null) {
                result.close();
            }
            if (stmt != null) {
                stmt.close();
            }
            if (stmt2 != null) {
                stmt2.close();
            }
            dbHandler.freeConnection(con);
        }

        System.out.println("Done");

        dbHandler.destroy();

    }
}
