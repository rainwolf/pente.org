<%@ page import="org.pente.database.*, 
                 org.pente.gameServer.core.*, 
                 org.pente.gameServer.server.*,
                 java.sql.*, 
                 javapns.Push,
                 javapns.devices.*, 
                 java.util.Date,
                 java.util.Calendar,
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
    if ((name != null) && (password != null) && (token != null)) {
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
                stmt = con.prepareStatement("select pid from player where name_lower = ?");
                stmt.setString(1, name);
                rs = stmt.executeQuery();
                long pid = 0;
                if (rs.next()) {
                   pid = rs.getLong("pid");
                }
                boolean firstTime = true;
                stmt.close();
                stmt = con.prepareStatement("select lastping from notifications_android where pid=? and token=?");
                stmt.setLong(1, pid);
                stmt.setString(2, token);
                rs = stmt.executeQuery();
                if (rs.next()) {
                    stmt = con.prepareStatement("update notifications_android set lastping=NOW() where pid=? and token=?");
                    firstTime = false;
                    log4j.info("Notification: updating for " + name + ", " + pid + " with token " + token);
                } else {
                    stmt = con.prepareStatement("INSERT INTO notifications_android (pid, token, lastping) VALUES (?, ?, NOW())");
                    log4j.info("Notification: registering for " + name + ", " + pid + " with token " + token);
                }
                stmt.setLong(1, pid);
                stmt.setString(2, token);
                int worked = stmt.executeUpdate();
                stmt.close();

                if (worked < 1) {
                    %> <b>Something went wrong</b><br><br> <%
                } else {
                    %> <b>It seems to have worked</b><br><br> <%

                    if (firstTime == true) {
                        try{
                            Push.alert("Your device has been registered for notifications", penteLiveAPNSkey, penteLiveAPNSpwd, productionFlag, token);
                        } catch(Exception e){
                            return;            // Always must return something
                        }
                    }

                }
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


        if (con != null) {
            dbHandler.freeConnection(con);
        }


    } else {
        %> <b>Name, password, or token missing. </b><br><br> <%
    }


%>


</body>
</html>

