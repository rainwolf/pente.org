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

   // keep logchecker in session to avoid scanning alot
   LogChecker l = (LogChecker) session.getAttribute("LogChecker");
   if (l == null) {
      // hard code log dir for now, don't have it as a parameter yet
      l = new LogChecker(new File("/var/log/dsg"));
      session.setAttribute("LogChecker", l);
   }

   String minDate = df.format(l.getMinDate());
   String maxDate = df.format(l.getMaxDate());
%>

<html>
<head><title>Check Logs</title></head>
<body>

<h3>Check Logs for Suspicious Activity</h3>

<form name="check" action="scanLog.jsp" method="post">
   <input type="hidden" name="action" value="check">
   <table border="0" cellspacing="0" cellpadding="0">
      <tr>
         <td valign="top">Activity Log Min Date:&nbsp;&nbsp;</td>
         <td><%= minDate %>
         </td>
      </tr>
      <tr>
         <td valign="top">Activity Log Max Date:&nbsp;&nbsp;</td>
         <td><%= maxDate %>
         </td>
      </tr>
      <tr>
         <td valign="top">Date to Check (yyyy-MM-dd):&nbsp;&nbsp;</td>
         <td><input type="text" name="date"></td>
      </tr>

      <tr>
         <td>&nbsp;</td>
         <td valign="top">
            <input type="submit" value="Check Log">
         </td>
      </tr>
   </table>
</form>

<a href=".">Back to admin</a>

</body>
</html>