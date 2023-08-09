<%@ page import="org.pente.admin.*,
                 org.pente.gameServer.server.*,
                 org.apache.log4j.*,
                 java.io.*,
                 java.text.*,
                 java.util.*" %>

<%! private static Category log4j =
   Category.getInstance("org.pente.gameServer.web.client.jsp"); %>
<%! private static final DateFormat df = new SimpleDateFormat("yyyy-MM-dd"); %>

<% Resources resources = (Resources) application.getAttribute(
   Resources.class.getName());

   String error = null;
   List<LogData> events = null;

   // keep logchecker in session to avoid scanning alot
   LogChecker l = (LogChecker) session.getAttribute("LogChecker");
   if (l == null) {
      // hard code log dir for now, don't have it as a parameter yet
      l = new LogChecker(new File("/var/log/dsg"));
      session.setAttribute("LogChecker", l);
   }

   String action = request.getParameter("action");
   String date = request.getParameter("date");

   if (action.equals("check")) {
      try {
         df.parse(date);
      } catch (ParseException ps) {
         log4j.info("Invalid date format.");
         error = "Invalid date format, use yyyy-MM-dd";
      }

      if (error == null) {
         try {
            events = l.scanFile(date);
         } catch (FileNotFoundException fnfe) {
            log4j.info("File not found.");
            error = "File not found, try a different date.";
         }
      }
   }


//2005-12-13 12:06:49,613 - 5: game match between [1:3 4186757 K10,J11,G10 r] and [1:1 4186757 K10,L9,N10 ur]
//2005-12-14 12:23:09,346 - 2: join potential match between [1 pizzalord, 66.207.121.2] and [1 jeremyh, 66.207.121.2]


%>

<html>
<head><title>Check Logs</title></head>
<body>

<h3>Check Logs for Suspicious Activity</h3>

<% if (error != null) { %>
<b><font color="red"><%= error %>
</font></b><br>
<a href="checkLogs.jsp">Try again</a>.
<% } else { %>

Scanned activity log file for <b><%= date %>
</b>.<br>
<a href="viewLog.jsp?date=<%= date %>">View the whole log file</a><br>
Found <b><font color="red"><%= events.size() %>
</font></b> suspicious events.<br>

<br>
<% for (LogData d : events) {
   String s = d.getEvent();
   String susp = s.substring(26, 29);
   if (susp.equals("2: ")) {
      int c1 = s.indexOf(',');
      c1 = s.indexOf(',', c1 + 1);
      int p1 = s.lastIndexOf(' ', c1);
      int b1 = s.indexOf(']', c1);
      int c2 = s.lastIndexOf(',');
      int p2 = s.lastIndexOf(' ', c2);
      String player1 = s.substring(p1 + 1, c1);
      String player2 = s.substring(p2 + 1, c2);
      String ip = s.substring(c1 + 2, b1);
      String linked = new String(s.substring(0, p1 + 1)) +
         "<a href=/gameServer/profile?viewName=" + player1 + ">" + player1 + "</a>, " +
         "<a href=\"viewIPs.jsp?ip=" + ip + "\">" + ip + "</a>" +
         s.substring(b1, p2 + 1) +
         "<a href=/gameServer/profile?viewName=" + player2 + ">" + player2 + "</a>, " +
         "<a href=\"viewIPs.jsp?ip=" + ip + "\">" + ip + "</a>]"; %>

<a href="viewLog.jsp?date=<%= date %>&s=<%= d.getStartLine() %>&l=<%= d.getLine() %>#l">Line <%= d.getLine() %>
</a>
<%= linked %><br>
<% } else { %>
<a href="viewLog.jsp?date=<%= date %>&s=<%= d.getStartLine() %>&l=<%= d.getLine() %>#l">Line <%= d.getLine() %>
</a>
<%= s %><br>
<% }
} %>

<% } %>
<br>

<table border="1" width="700">
   <tr>
      <td colspan="2"><b>Reference:</b></td>
   </tr>
   <tr>
      <td colspan="2">Click the line number next to events to view the event in
         context with the rest of the log file.
      </td>
   </tr>
   <tr>
      <td width="100px">Join match</td>
      <td>A join match event means two players were logged in at
         the same time to a game room from the same IP. It doesn't mean a player is
         definitely cheating since it could just be two players who live together logging
         in at the same time for example.
      </td>
   </tr>
   <tr>
      <td>Game match</td>
      <td>A game match event means there were two games being played at Pente.org at the same
         time that had the same sequence of moves. Again it doesn't mean cheating definitely
         since it could just be a common opening or a random match. Also, the game match currently doesn't
         provide much context, you have to go and find the players involved by looking in
         the log file.
      </td>
   </tr>
</table>

<br>
<a href="checkLogs.jsp">Check another date</a><br>
<a href="index.jsp">Back to admin</a>

</body>
</html>