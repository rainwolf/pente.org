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

   String date = request.getParameter("date");//assume valid
   String startLineStr = request.getParameter("s");
   String endLineStr = request.getParameter("e");
   String lineStr = request.getParameter("l");
   int s = (startLineStr != null) ? Integer.parseInt(startLineStr) : 0;
   int e = (endLineStr != null) ? Integer.parseInt(endLineStr) : s + 1000;
   int ll = (lineStr != null) ? Integer.parseInt(lineStr) : -1;

   // keep logchecker in session to avoid scanning alot
   LogChecker l = (LogChecker) session.getAttribute("LogChecker");
   if (l == null) {
      // hard code log dir for now, don't have it as a parameter yet
      l = new LogChecker(new File("/var/log/dsg"));
      session.setAttribute("LogChecker", l);
   }

   String lines[] = l.getFileSection(date, s, e);
   int num = l.getFileLength(date);
%>

<html>
<head><title>View Activity Log</title></head>

<h3>View Activity Log</h3>

View File Section:
<% for (int i = 0; i < (num / 1000) + 1; i++) {
   int ss = i * 1000;
   int ee = (i + 1) * 1000;
   if (ee > num) ee = num;
   if (ss == s) { %>
<%= i + 1 %>
<% } else { %>
<a href="viewLog.jsp?date=<%= date %>&s=<%= ss %>&e=<%= ee %>"><%= i + 1 %>
</a>
<% }
   if (i < (num / 1000)) { %>|<% } %>
<% } %>
<br>
<hr>
<br>

<% for (int i = 0; i < lines.length; i++) {
   if (lines[i] != null) {
      if (ll == s + i) { %> <font color="red"><a name="l"><%= lines[i] %>
</a></font><br> <% } else { %> <%= lines[i] %><br> <% } %>
<% } %>
<% } %>
<br>
<a href=".">Back to admin</a>

</body>
</html>