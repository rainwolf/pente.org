<%@ page import="java.util.*,
                 java.sql.*,
                 org.pente.gameServer.tourney.*,
                 org.pente.gameServer.server.*,
                 org.pente.gameServer.core.*,
                 org.pente.game.*,
                 org.apache.log4j.*" %>

<%! private static Category log4j =
   Category.getInstance("org.pente.gameServer.web.client.jsp"); %>

<% Resources resources = (Resources) application.getAttribute(
   Resources.class.getName());

   String name = request.getParameter("name");
   if (name != null) {
      CacheDSGPlayerStorer d = (CacheDSGPlayerStorer) resources.getDsgPlayerStorer();
      d.refreshPlayer(name);
   }
%>

<html>
<head>
   <title>Refresh player</title>
</head>

<body>
<form name="refresh" method="post" action="refreshPlayer.jsp">
   Name: <input type="text" name="name"><br>
   <br>
   <input type="submit" value="Refresh">
   <br>
</form>

<a href=".">Back to admin</a>

</body>
</html>