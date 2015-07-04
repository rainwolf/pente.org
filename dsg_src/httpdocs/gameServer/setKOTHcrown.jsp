<%@ page import="org.pente.database.*, 
                 org.pente.gameServer.core.*, 
                 org.pente.gameServer.server.*,
                 java.sql.*,
                 java.util.*,
                 org.apache.log4j.*" %>

<%! private static Category log4j = 
        Category.getInstance("org.pente.gameServer.web.client.jsp"); %>

<html>
<body>

<%
    String sendername = request.getParameter("name");
    if (sendername.equals("invictus") || sendername.equals("rainwolf") || sendername.equals("katysmom")) {
        String name = request.getParameter("kothname");
        String name1 = null;
        if (name != null && !name.equals("")) {
    
            DBHandler dbHandler = null;
            DSGPlayerStorer dsgPlayerStorer = null;
    
            Connection con = null;
            PreparedStatement stmt = null;
            ResultSet result = null;
    
    
            try {
                dbHandler = (DBHandler) application.getAttribute(DBHandler.class.getName());
                dsgPlayerStorer = (DSGPlayerStorer) application.getAttribute(DSGPlayerStorer.class.getName());
                con = dbHandler.getConnection();
    
                DSGPlayerData data = dsgPlayerStorer.loadPlayer(name);
                if (data == null) {
                    %> <b>Player data not found</b><br><br> <%
                }
                else {
                    Resources resources = (Resources) application.getAttribute(Resources.class.getName());
                    CacheDSGPlayerStorer d = (CacheDSGPlayerStorer) resources.getDsgPlayerStorer();
        
                    stmt = con.prepareStatement("select name_lower from player where pid in (select pid from dsg_player_game where tourney_winner='4')");
                    ResultSet rs = stmt.executeQuery();
                    if (rs.next()) {
                       name1 = rs.getString("name_lower");
                    }

                    long lastPID = 0, streak = 0;
                    stmt = con.prepareStatement("select pid, max(date), streak from dsg_koth");
                    rs = stmt.executeQuery();
                    if (rs.next()) {
                       lastPID = rs.getLong("pid");
                       if (lastPID == data.getPlayerID()) {
                           streak = rs.getLong("streak");
                       }
                    }

                    long pid = data.getPlayerID();
                    stmt = con.prepareStatement(
                        "select level, paymentdate " +
                        "from dsg_subscribers " +
                        "where pid = ?");
                    stmt.setLong(1, pid);
                    result = stmt.executeQuery();
                    int level = 0;
                    Calendar paymentDate;
                    boolean update = false;
                    Calendar lastMonth = Calendar.getInstance();
                    lastMonth.add(java.util.Calendar.DATE, -31);
                    Calendar lastYear = Calendar.getInstance();
                    lastYear.add(java.util.Calendar.YEAR, -1);
                    if (!result.isBeforeFirst()) {
                        int subscriptionLvl = 0;
                        subscriptionLvl = (subscriptionLvl | org.pente.gameServer.core.MySQLDSGPlayerStorer.ONEMONTH);
                        subscriptionLvl = (subscriptionLvl | org.pente.gameServer.core.MySQLDSGPlayerStorer.UNLIMITEDTBGAMES);
                        subscriptionLvl = (subscriptionLvl | org.pente.gameServer.core.MySQLDSGPlayerStorer.NOADS);
                        subscriptionLvl = (subscriptionLvl | org.pente.gameServer.core.MySQLDSGPlayerStorer.DBACCESS);
                        stmt = con.prepareStatement("INSERT INTO dsg_subscribers (pid, level, paymentdate, transactionid, amount) VALUES (?, ?, NOW(), NOW(), 0)");
                        stmt.setLong(1, pid);
                        stmt.setInt(2, subscriptionLvl);
                        int worked = stmt.executeUpdate();
                        if (worked < 1) {
                            log4j.info(" KotH: inserting " + name + " failed");
                        } else {
                            log4j.info(" KotH: inserting " + name + " success");
                        }
                    }
                    while (result.next()) {
                        paymentDate = Calendar.getInstance();
                        paymentDate.setTime(result.getDate("paymentdate"));
                        int registeredLvl = result.getInt(1);
                        if ((registeredLvl & org.pente.gameServer.core.MySQLDSGPlayerStorer.ONEMONTH) != 0) {
                            if (paymentDate.after(lastMonth)) {
                                update = true;
                            }
                        } else  if ((registeredLvl & org.pente.gameServer.core.MySQLDSGPlayerStorer.ONEYEAR) != 0) {
                            if (paymentDate.after(lastYear)) {
                                update= true;
                            }
                        }
                        if (update) {
                            paymentDate.add(java.util.Calendar.DATE, 31);
                            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                            String dateTime = sdf.format(paymentDate.getTime());
                            stmt = con.prepareStatement("INSERT INTO dsg_subscribers (pid, level, paymentdate, transactionid, amount) VALUES (?, ?, ?, NOW(), 0)");
                            stmt.setLong(1, pid);
                            stmt.setInt(2, registeredLvl);
                            stmt.setString(3, dateTime);
                            int worked = stmt.executeUpdate();
                            if (worked < 1) {
                                log4j.info(" KotH: inserting " + name + " failed");
                            } else {
                                log4j.info(" KotH: inserting " + name + " success");
                            }
                            update = false;
                        }
                    }

                    if (data.getNameColorRGB() == 0) {
                        data.setNameColorRGB(-16751616);
                        dsgPlayerStorer.updatePlayer(data);
                    }
        
                    stmt = con.prepareStatement("update dsg_player_game set tourney_winner='0' where tourney_winner='4'");
                    stmt.executeUpdate();


                    stmt = con.prepareStatement("update dsg_player_game set tourney_winner='4' where pid=? and game=1 and computer='N'");
                    stmt.setLong(1, data.getPlayerID());
                    stmt.executeUpdate();

                    d.refreshPlayer(name);
                    d.refreshPlayer(name1);

                    stmt = con.prepareStatement("INSERT INTO dsg_koth (pid, date, streak) VALUES (?, NOW(), ?)");
                    stmt.setLong(1, data.getPlayerID());
                    stmt.setLong(2, streak + 1);
                    stmt.executeUpdate();
                    %> <b>Crown change from <%=name1%> to <%=name%> successful</b><br><br> <%
                }
            } catch (Throwable t) {
                throw t;
            } finally {
                if (result != null) {
                    result.close();
                }
                if (stmt != null) {
                    stmt.close();
                }
                if (con != null) {
                    dbHandler.freeConnection(con);
                }
            }
        }
    } else {
        %>
        <%@include file="../four04.jsp" %>
        <%
    }
%>

</body>
</html>

