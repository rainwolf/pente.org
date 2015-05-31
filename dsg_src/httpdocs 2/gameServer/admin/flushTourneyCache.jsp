<%@ page import="java.util.*, java.sql.*,
                 org.pente.gameServer.tourney.*, 
                 org.pente.gameServer.server.*,
                 org.pente.gameServer.core.*,
                 org.pente.game.*,
                 org.apache.log4j.* "%>

<%! private static Category log4j = 
        Category.getInstance("org.pente.gameServer.web.client.jsp"); %>
    
<% Resources resources = (Resources) application.getAttribute(
       Resources.class.getName());

   CacheTourneyStorer t = (CacheTourneyStorer) resources.getTourneyStorer();
   t.flushCache();
%>

<html>
<head>
 <title>Flush tournament cache</title>
</head>

<body>

Tournament cache flushed successfully.<br>
<br>
<a href=".">Back to admin</a>

</body>
</html>