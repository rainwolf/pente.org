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
                stmt = con.prepareStatement("select pid from player where name_lower = ? and site_id = 2");
                stmt.setString(1, name);
                rs = stmt.executeQuery();
                long pid = 0;
                if (rs.next()) {
                   pid = rs.getLong("pid");
                }
                boolean firstTime = true;
                stmt.close();
                stmt = con.prepareStatement("select lastping from notifications where pid=? and token=?");
                stmt.setLong(1, pid);
                stmt.setString(2, token);
                rs = stmt.executeQuery();
                if (rs.next()) {
                    stmt = con.prepareStatement("update notifications set lastping=NOW() where pid=? and token=?");
                    firstTime = false;
                    log4j.info("iOS Notification: updating for " + name + ", " + pid + " with token " + token);
                } else {
                    stmt = con.prepareStatement("INSERT INTO notifications (pid, token, lastping) VALUES (?, ?, NOW())");
                    log4j.info("iOS Notification: registering for " + name + ", " + pid + " with token " + token);
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

        stmt = con.prepareStatement("select lastping from notifications where token='1111'");
        rs = stmt.executeQuery();  
        if (rs.next()) {
            Timestamp lastPing = rs.getTimestamp("lastping");
            java.util.Date date = new java.util.Date();
            Timestamp lastWeekTimestamp = new Timestamp(date.getTime());
            long lastWeek = lastWeekTimestamp.getTime() - (14*1000*3600*24);
            lastWeekTimestamp.setTime(lastWeek);
            if (lastWeekTimestamp.after(lastPing)) {
                log4j.info("iOS Notifications: it has been more than 2 weeks since I last checked the stale iOS tokens.");
                stmt = con.prepareStatement("update notifications set lastping=NOW() where token='1111'");
                stmt.executeUpdate();
                stmt = con.prepareStatement("DELETE from notifications where lastping < ?");
                stmt.setTimestamp(1, lastWeekTimestamp);
                int deletedDevices = stmt.executeUpdate();
                    log4j.info("iOS Notifications: I deleted " + deletedDevices + " stale iOS tokens.");
                stmt.close();

                %><%=deletedDevices %> iOS device tokens deleted.</b><br><br> <%
            }
        } else {
            stmt.close();
            stmt = con.prepareStatement("INSERT INTO notifications (pid, token, lastping) VALUES (0, ?, NOW())");
            stmt.setString(1,"1111");
            stmt.executeUpdate();
        }
        stmt.close();


        stmt = con.prepareStatement("select lastping from notifications where token='0000'");
        rs = stmt.executeQuery();  
        if (rs.next()) {
            Calendar local = Calendar.getInstance();
            local.clear();
            local.set(1970, Calendar.JANUARY, 1, 0, 0, 0);
            long offSet = local.getTimeInMillis();

            Timestamp lastPing = rs.getTimestamp("lastping");
            java.util.Date date = new java.util.Date();
            Timestamp yesterdayTimestamp = new Timestamp(date.getTime());
            long yesterday = yesterdayTimestamp.getTime() - (1000*3600*24);
            yesterdayTimestamp.setTime(yesterday);
            if (yesterdayTimestamp.after(lastPing)) {
                log4j.info("Notification: it has been more than a day since I last checked the feedback server.");
                List<Device> inactiveDevices = Push.feedback(penteLiveAPNSkey, penteLiveAPNSpwd, productionFlag);
                Device nonactiveDevice;
                int deletedDevices = 0;
                int idx = 0;
                while (idx < inactiveDevices.size()) {
                    nonactiveDevice = inactiveDevices.get(idx);
                    stmt = con.prepareStatement("select pid,lastping from notifications where token=?");
                    stmt.setString(1, nonactiveDevice.getToken());
                    rs = stmt.executeQuery();
                    if (rs.next()) {
                        lastPing = rs.getTimestamp("lastping");
                        long lastPingLong = lastPing.getTime() - offSet;
                        lastPing.setTime(lastPingLong);
                        if (nonactiveDevice.getLastRegister().after(lastPing)) {
                            log4j.info("Notification: removing the token " + nonactiveDevice.getToken() + " from pid: " + rs.getLong("pid"));
                            stmt = con.prepareStatement("DELETE from notifications where token=?");
                            stmt.setString(1, nonactiveDevice.getToken());
                            if (stmt.executeUpdate() > 0) {
                                deletedDevices++;
                            }
                        }

                    } 
                    idx++;
                    stmt.close();
                }
                stmt = con.prepareStatement("update notifications set lastping=NOW() where token='0000'");
                stmt.executeUpdate();

                %> <b><%=inactiveDevices.size() %> inactive devices received, <%=deletedDevices %> device tokens deleted.</b><br><br> <%
            }
        } else {
            stmt.close();
            stmt = con.prepareStatement("INSERT INTO notifications (pid, token, lastping) VALUES (0, ?, NOW())");
            stmt.setString(1,"0000");
            stmt.executeUpdate();
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

