<%@ page import="java.util.*,
                 java.sql.*,
                 org.pente.gameServer.server.*,
                 org.pente.gameServer.core.*,
                 org.pente.game.*,
                 org.apache.log4j.*" %>

<%! private static Category log4j =
   Category.getInstance("org.pente.gameServer.web.client.jsp"); %>

<% Resources resources = (Resources) application.getAttribute(
   Resources.class.getName());

   String serverIDStr = request.getParameter("sid");
   int serverID = Integer.parseInt(serverIDStr);
   ServerData data = resources.getServerData(serverID);

   String action = request.getParameter("action");
   if (action != null && action.equals("update")) {
      String messages[] = request.getParameterValues("message");
      Vector newMessages = new Vector();
      for (int i = 0; i < messages.length; i++) {
         if (!messages[i].equals("")) {
            newMessages.add(messages[i]);
         }
      }
      data.setLoginMessages(newMessages);
      MySQLServerStorer.updateServerMessages(resources.getDbHandler(), data);
   }

%>

<html>
<head><title>Modify server messages</title></head>
<body>

<b>Modify server messages</b><br>
Server: <%= data.getName() %><br>
<a href="modifyServers.jsp">Back to Modify Servers</a><br>
<br>
<form name="modifyMessages" action="modifyServerMessages.jsp" method="post">
   <input type="hidden" name="action" value="update">
   <input type="hidden" name="sid" value="<%= serverIDStr %>">

   <% int maxMessages = 5;
      int numExistingMessages = data.getLoginMessages().size();
      for (int i = 0; i < maxMessages; i++) {
         String message = (i >= numExistingMessages) ? "" :
            (String) data.getLoginMessages().get(i); %>

   <input type="text" name="message" value="<%= message %>" size="100"><br>
   <% } %>

   <input type="submit" value="Update">
</form>

</body>
</html>