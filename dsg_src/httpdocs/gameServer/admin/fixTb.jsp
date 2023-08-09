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

   try {

      String gidStr = request.getParameter("g");
      if (gidStr != null && !gidStr.equals("") {

         long g = Long.parseInt(gidStr);

         CacheTBStorer tbGameStorer = (CacheTBStorer) resources.getTbGameStorer();

         tbGameStorer.fixGame(g);
      }
    else{
         gidStr = "";
      }
%>

<html>
<head>
   <title>Fix TB Game</title>
</head>

<body>

<
<form name="fix" method="post" action="fixTb.jsp">
   <input type="text" name="g" size="14" value="<%= gidStr %>">
   <input type="submit" value="Fix Game">
</form>
<a href=".">Back to admin</a>

</body>
</html>

<% } catch (Throwable t) {
   t.printStackTrace();
} %>
