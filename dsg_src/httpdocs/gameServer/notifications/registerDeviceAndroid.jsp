<%@ page import="org.pente.database.*, 
                 org.pente.gameServer.core.*, 
                 org.pente.gameServer.server.*,
                 java.sql.*, 
                 javapns.Push,
                 javapns.devices.*, 
                 java.util.Date,
                 java.util.Calendar,
                 java.util.List,
                 org.apache.log4j.*,
                    org.apache.commons.io.IOUtils,
                    org.json.JSONObject,
                    java.io.IOException,
                    java.io.InputStream,
                    java.io.OutputStream,
                    java.net.HttpURLConnection,
                    java.net.URL" %>

<%! private static Category log4j = 
        Category.getInstance("org.pente.gameServer.web.client.jsp"); %>

<html>
<body>

<%
    String name = request.getParameter("name").toLowerCase();
    String password = request.getParameter("password");
    String token = request.getParameter("token");
    ServletContext ctx = getServletContext();
    String penteLiveGCMkey = ctx.getInitParameter("penteLiveGCMkey");

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
                stmt = con.prepareStatement("select lastping from notifications_android where pid=? and token=?");
                stmt.setLong(1, pid);
                stmt.setString(2, token);
                rs = stmt.executeQuery();
                if (rs.next()) {
                    stmt = con.prepareStatement("update notifications_android set lastping=NOW() where pid=? and token=?");
                    firstTime = false;
                    log4j.info("Android Notification: updating for " + name + ", " + pid + " with token " + token);
                } else {
                    stmt = con.prepareStatement("INSERT INTO notifications_android (pid, token, lastping) VALUES (?, ?, NOW())");
                    log4j.info("Android Notification: registering for " + name + ", " + pid + " with token " + token);
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



                            JSONObject jGcmData = new JSONObject();
                            JSONObject jData = new JSONObject();
                            String message = "Your device has been registered for push notifications";
                            jData.put("message", message);
                            jGcmData.put("to", token);
                            jGcmData.put("data", jData);

                            try {

                                // Create connection to send GCM Message request.
                                URL url = new URL("https://android.googleapis.com/gcm/send");
                                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                                conn.setRequestProperty("Authorization", "key=" + penteLiveGCMkey);
                                conn.setRequestProperty("Content-Type", "application/json");
                                conn.setRequestMethod("POST");
                                conn.setDoOutput(true);

                                // Send GCM message content.
                                OutputStream outputStream = conn.getOutputStream();
                                outputStream.write(jGcmData.toString().getBytes());

                                // Read GCM response.
                                InputStream inputStream = conn.getInputStream();
                                String resp = IOUtils.toString(inputStream);
                                System.out.println("Android registration notification: " + resp);

                                if (resp.indexOf("InvalidRegistration") > -1 || resp.indexOf("NotRegistered") > -1 ) {
                                    PreparedStatement stmt1 = con.prepareStatement("DELETE from notifications_android where token=?");
                                    stmt1.setString(1, token);
                                    stmt1.executeUpdate();
                                    stmt1.close();
                                }
                            } catch (IOException e) {
                                System.out.println("Unable to send GCM message.");
                                System.out.println("Please ensure that API_KEY has been replaced by the server " +
                                        "API key, and that the device's registration token is correct (if specified).");
                                e.printStackTrace();
                            }





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





        stmt = con.prepareStatement("select lastping from notifications_android where token='1111'");
        rs = stmt.executeQuery();  
        if (rs.next()) {
            Timestamp lastPing = rs.getTimestamp("lastping");
            java.util.Date date = new java.util.Date();
            Timestamp lastWeekTimestamp = new Timestamp(date.getTime());
            long lastWeek = lastWeekTimestamp.getTime() - (14*1000*3600*24);
            lastWeekTimestamp.setTime(lastWeek);
            if (lastWeekTimestamp.after(lastPing)) {
                stmt = con.prepareStatement("update notifications_android set lastping=NOW() where token='1111'");
                stmt.executeUpdate();
                log4j.info("Android Notifications: it has been more than 2 weeks since I last checked the stale Android tokens.");
                stmt = con.prepareStatement("DELETE from notifications_android where lastping < ?");
                stmt.setTimestamp(1, lastWeekTimestamp);
                int deletedDevices = stmt.executeUpdate();
                    log4j.info("Android Notifications: I deleted " + deletedDevices + " stale Android tokens.");
                stmt.close();

                %><%=deletedDevices %> Android device tokens deleted.</b><br><br> <%
            }
        } else {
            stmt.close();
            stmt = con.prepareStatement("INSERT INTO notifications_android (pid, token, lastping) VALUES (0, ?, NOW())");
            stmt.setString(1,"1111");
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

