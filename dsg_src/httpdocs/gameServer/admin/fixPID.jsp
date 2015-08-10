<%@ page import="org.pente.database.*, 
                 org.pente.gameServer.core.*, 
                 org.pente.gameServer.server.*,
                 java.sql.*, 
                 java.util.List,
                 org.apache.log4j.*" %>

<%! private static Category log4j = 
        Category.getInstance("org.pente.gameServer.web.client.jsp"); %>

<html>
<body>

<%
    String name = request.getParameter("name").toLowerCase();
    String password = request.getParameter("password");
    String token = request.getParameter("token");
    ServletContext ctx = getServletContext();
    String penteLiveAPNSkey = ctx.getInitParameter("penteLiveAPNSkey");
    String penteLiveAPNSpwd = ctx.getInitParameter("penteLiveAPNSpassword");
    boolean productionFlag = ctx.getInitParameter("penteLiveAPNSproductionFlag").equals("true");

    DBHandler dbHandler = (DBHandler) application.getAttribute(DBHandler.class.getName());
    LoginHandler loginHandler;
    loginHandler = new SmallLoginHandler(dbHandler);
    int loginResult = LoginHandler.INVALID;
    Connection con = null;
    PreparedStatement stmt = null;
    ResultSet rs = null;
    con = dbHandler.getConnection();
    if ((name != null) && (password != null)) {
        loginResult = loginHandler.isValidLogin(name, password);
        if (loginResult == LoginHandler.INVALID) {
            PasswordHelper passwordHelper;
            passwordHelper = (PasswordHelper) application.getAttribute(PasswordHelper.class.getName());
            password = passwordHelper.encrypt(password);
            loginResult = loginHandler.isValidLogin(name, password);

            if (loginResult == LoginHandler.INVALID) {
                %> <b>2nd login was not successful </b><br><br> <%
            } 
        } 

        if (loginResult == loginHandler.VALID) {
            DSGPlayerStorer dsgPlayerStorer = null;
    
    
            try {
                PreparedStatement stmt0 = null;
                PreparedStatement stmtPIDs = null;
                ResultSet rs0 = null;
                ResultSet rsPIDs = null;
                String tableName = null, columnName = null;
                String[][] tablesColumns = new String [2][54];
                String output = null;

                stmt = con.prepareStatement("SELECT COLUMN_NAME, TABLE_NAME  FROM INFORMATION_SCHEMA.COLUMNS  WHERE TABLE_SCHEMA='dsg' and (COLUMN_NAME LIKE '%pid%' or column_name = 'userID') and not column_name = 'groupID'");
                rs = stmt.executeQuery();

                int workTimes = 0;
                int i = 0;
                while (rs.next()) {
                    tableName = rs.getString("TABLE_NAME");
                    columnName = rs.getString("COLUMN_NAME");
                    tablesColumns[0][i] = tableName;
                    tablesColumns[1][i] = columnName;
                    i++;
                }
                rs.close();
                stmt.close();

                for ( i = 0; i < 54; i++ ) {
                    tableName = tablesColumns[0][i];
                    columnName = tablesColumns[1][i];
                    output = "<b> " + tableName + ", " + columnName + " </b> <br>";

                    stmt0 = con.prepareStatement("update " + tableName + " set " + columnName + " = 23000000030076 where " + columnName + " = 25298534884876");
                    workTimes = stmt0.executeUpdate();
                    if (workTimes > 0) {
                        output = output + "    " + workTimes + " replacements of 25298534884876 into 23000000030076 <br>";
                    }
                    stmt0.close();

                    stmt0 = con.prepareStatement("update " + tableName + " set " + columnName + " = 23000000030077 where " + columnName + " = 216194782113785317");
                    workTimes = stmt0.executeUpdate();
                    if (workTimes > 0) {
                        output = output + "    " + workTimes + " replacements of 216194782113785317 into 23000000030077 <br>";
                    }
                    stmt0.close();

                    stmt0 = con.prepareStatement("update " + tableName + " set " + columnName + " = 23000000030078 where " + columnName + " = 864713128455136177");
                    workTimes = stmt0.executeUpdate();
                    if (workTimes > 0) {
                        output = output + "    " + workTimes + " replacements of 864713128455136177 into 23000000030078 <br>";
                    }
                    stmt0.close();
                }
                %> 
                <%= output %><br>
                <%


                stmtPIDs = con.prepareStatement("select pid from player where pid > 864713128455136177");
                rsPIDs = stmtPIDs.executeQuery();
                while (rsPIDs.next()) {
                    long oldPID = rsPIDs.getLong("pid");
                    long newPID = oldPID - (864713128455139577L - 23000000030079L);
                    for ( i = 0; i < 54; i++ ) {
                        tableName = tablesColumns[0][i];
                        columnName = tablesColumns[1][i];
                        output = "<b> " + tableName + ", " + columnName + " </b> <br>";
                        stmt0 = con.prepareStatement("update " + tableName + " set " + columnName + " = " + newPID + " where " + columnName + " = " + oldPID);
                        workTimes = 0;
                        workTimes = stmt0.executeUpdate();
                        if (workTimes > 0) {
                            output = output + "    " + workTimes + " replacements of " + oldPID + " into " + newPID + " <br>";
                        }
                        stmt0.close();
                        %> 
                        <%= output %><br>
                        <%
                    }
                }

                %> 
                <%= output %><br>
                <%
                stmtPIDs.close();







            } catch (Throwable t) {
                throw t;
            } finally {
                if (rs != null) {
                    rs.close();
                }
                if (stmt != null) {
                    stmt.close();
                }
            }
        }

        stmt.close();



        if (con != null) {
            dbHandler.freeConnection(con);
        }


    } else {
        %> <b>Name, password, or token missing. </b><br><br> <%
    }


%>


</body>
</html>

