<%
   /**
    *	$RCSfile: querystats.jsp,v $
    *	$Revision: 1.3 $
    *	$Date: 2002/11/23 04:22:30 $
    */
%>

<%@ page
   import="java.sql.*,
           java.text.NumberFormat,
           com.jivesoftware.util.ParamUtils,
           com.jivesoftware.base.database.*" %>

<%@ include file="global.jsp" %>

<%! // page method

   /**
    * Returns a String representation of a double value in a specific locale's format, limited
    * to n decimal places
    *
    * @param value number to format
    * @param n decimal places to allow in output
    * @return a String with a max of n decimals
    */
   private static String buildStringValue(double value, int n) {
      NumberFormat nf = NumberFormat.getInstance(Locale.US);
      nf.setMaximumFractionDigits(n);
      nf.setMinimumFractionDigits(n);
      nf.setGroupingUsed(true);
      return nf.format(value);
   }
%>

<% // Permission check
   if (!isSystemAdmin) {
      throw new UnauthorizedException("You don't have admin privileges to perform this operation.");
   }

   // Get parameters
   boolean doClear = ParamUtils.getBooleanParameter(request, "doClear");
   boolean sortByTime = ParamUtils.getBooleanParameter(request, "sortByTime", false);
   String doLogStr = ParamUtils.getParameter(request, "doLog");
   int alternate = 0;

   // clear the statistics
   if (doClear) {
      ProfiledConnection.resetStatistics();
   }

   if (doLogStr != null && doLogStr.equals("true")) {
      ConnectionManager.setProfilingEnabled(true);
   } else if (doLogStr != null && doLogStr.equals("false")) {
      ConnectionManager.setProfilingEnabled(false);
   }

   boolean doLog = ConnectionManager.isProfilingEnabled();
%>


<%@ include file="header.jsp" %>

<p>

      <%  // Title of this page and breadcrumbs
    String title = "Query Statistics";
    String[][] breadcrumbs = {
        {"Main", "main.jsp"},
        {"Datasource Info", "datasource.jsp"},
        {"Query Stats", "querystats.jsp"}
    };
%>
   <%@ include file="title.jsp" %>

      <% if (doLog) { %>

   <font size="-1"><b>Select Query Statistics</b></font>

<ul>

   <table bgcolor="<%= tblBorderColor %>" cellpadding="0" cellspacing="0" border="0" width="600">
      <tr>
         <td>
            <table bgcolor="<%= tblBorderColor %>" cellpadding="3" cellspacing="1" border="0" width="100%">
               <tr bgcolor="#ffffff">
                  <td><font size="-1">Total # of selects</font></td>
                  <td><font size="-1"><%= ProfiledConnection.getQueryCount(ProfiledConnection.SELECT) %>
                  </font></td>
               </tr>
               <tr bgcolor="#ffffff">
                  <td><font size="-1">Total time for all selects (ms)</font></td>
                  <td><font size="-1"><%= ProfiledConnection.getTotalQueryTime(ProfiledConnection.SELECT) %>
                  </font></td>
               </tr>
               <tr bgcolor="#ffffff">
                  <td><font size="-1">Average time for all selects (ms)</font></td>
                  <td><font
                     size="-1"><%= buildStringValue(ProfiledConnection.getAverageQueryTime(ProfiledConnection.SELECT), 3) %>
                  </font></td>
               </tr>
               <tr bgcolor="#ffffff">
                  <td><font size="-1">Selects per second</font></td>
                  <td><font
                     size="-1"><%= buildStringValue(ProfiledConnection.getQueriesPerSecond(ProfiledConnection.SELECT), 3) %>
                  </font></td>
               </tr>
               <tr bgcolor="#ffffff">
                  <td><font size="-1">20 Most common selects</font></td>
                  <td bgcolor="#ffffff"><%
                     ProfiledConnectionEntry[] list = ProfiledConnection.getSortedQueries(ProfiledConnection.SELECT, sortByTime);

                     if (list == null || list.length < 1) {
                        out.println("<font size=\"-1\">No queries</font>");
                     } else { %>
                     &nbsp;
                  </td>
               </tr>
            </table>
         </td>
      </tr>
   </table>

   <br>

   <table bgcolor="<%= tblBorderColor %>" cellpadding="0" cellspacing="0" border="0" width="600">
      <tr>
         <td>
            <table bgcolor="<%= tblBorderColor %>" cellpadding="3" cellspacing="0" border="0" width="100%">
               <tr bgcolor="#ffffff">
                  <td>
                     <%
                           out.println("<table width=\"100%\" cellpadding=\"3\" cellspacing=\"1\" border=\"0\" bgcolor=\"" + tblBorderColor + "\"><tr><td bgcolor=\"#ffffff\" align=\"middle\"><font size=\"-1\"><b>Query</b></font></td>");
                           out.println("<td bgcolor=\"#ffffff\"><font size=\"-1\"><b><a href=\"javascript:location.href='querystats.jsp?sortByTime=false';\">Count</a></b></font></td>");
                           out.println("<td bgcolor=\"#ffffff\"><font size=\"-1\"><b>Total Time (ms)</b></font></td>");
                           out.println("<td bgcolor=\"#ffffff\"><font size=\"-1\"><b><a href=\"javascript:location.href='querystats.jsp?sortByTime=true';\">Average Time</a> (ms)</b></font></td></tr>");

                           for (int i = 0; i < ((list.length > 20) ? 20 : list.length); i++) {
                              ProfiledConnectionEntry pce = list[i];
                              out.println("<tr><td bgcolor=\"" + ((alternate % 2 == 0) ? "#dddddd" : "#ffffff") + "\"><font size=\"-1\">" + pce.sql + "</font></td>");
                              out.println("<td bgcolor=\"" + ((alternate % 2 == 0) ? "#dddddd" : "#ffffff") + "\"><font size=\"-1\">" + pce.count + "</font></td>");
                              out.println("<td bgcolor=\"" + ((alternate % 2 == 0) ? "#dddddd" : "#ffffff") + "\"><font size=\"-1\">" + pce.totalTime + "</font></td>");
                              out.println("<td bgcolor=\"" + ((alternate++ % 2 == 0) ? "#dddddd" : "#ffffff") + "\"><font size=\"-1\">" + pce.totalTime / pce.count + "</font></td></tr>");
                           }
                           out.println("</table>");
                        }
                     %></td>
               </tr>
            </table>
         </td>
      </tr>
   </table>

</ul>

<font size="-1"><b>Insert Query Statistics</b></font>

<ul>

   <table bgcolor="<%= tblBorderColor %>" cellpadding="0" cellspacing="0" border="0" width="600">
      <tr>
         <td>
            <table bgcolor="<%= tblBorderColor %>" cellpadding="3" cellspacing="1" border="0" width="100%">
               <tr bgcolor="#ffffff">
                  <td><font size="-1">Total # of inserts</font></td>
                  <td><font size="-1"><%= ProfiledConnection.getQueryCount(ProfiledConnection.INSERT) %>
                  </font></td>
               </tr>
               <tr bgcolor="#ffffff">
                  <td><font size="-1">Total time for all inserts (ms)</font></td>
                  <td><font size="-1"><%= ProfiledConnection.getTotalQueryTime(ProfiledConnection.INSERT) %>
                  </font></td>
               </tr>
               <tr bgcolor="#ffffff">
                  <td><font size="-1">Average time for all inserts (ms)</font></td>
                  <td><font
                     size="-1"><%= buildStringValue(ProfiledConnection.getAverageQueryTime(ProfiledConnection.INSERT), 3) %>
                  </font></td>
               </tr>
               <tr bgcolor="#ffffff">
                  <td><font size="-1">Inserts per second</font></td>
                  <td><font
                     size="-1"><%= buildStringValue(ProfiledConnection.getQueriesPerSecond(ProfiledConnection.INSERT), 3) %>
                  </font></td>
               </tr>
               <tr bgcolor="#ffffff">
                  <td><font size="-1">10 Most common inserts</font></td>
                  <td bgcolor="#ffffff"><%
                     list = ProfiledConnection.getSortedQueries(ProfiledConnection.INSERT, sortByTime);

                     if (list == null || list.length < 1) {
                        out.println("<font size=\"-1\">No queries</font>");
                     } else { %>
                     &nbsp;
                  </td>
               </tr>
            </table>
         </td>
      </tr>
   </table>

   <br>

   <table bgcolor="<%= tblBorderColor %>" cellpadding="0" cellspacing="0" border="0" width="600">
      <tr>
         <td>
            <table bgcolor="<%= tblBorderColor %>" cellpadding="3" cellspacing="0" border="0" width="100%">
               <tr bgcolor="#ffffff">
                  <td>
                     <%
                           out.println("<table width=\"100%\" cellpadding=\"3\" cellspacing=\"1\" border=\"0\" bgcolor=\"" + tblBorderColor + "\"><tr><td bgcolor=\"#ffffff\" align=\"middle\"><font size=\"-1\"><b>Query</b></font></td>");
                           out.println("<td bgcolor=\"#ffffff\"><font size=\"-1\"><b><a href=\"javascript:location.href='querystats.jsp?sortByTime=false';\">Count</a></b></font></td>");
                           out.println("<td bgcolor=\"#ffffff\"><font size=\"-1\"><b>Total Time (ms)</b></font></td>");
                           out.println("<td bgcolor=\"#ffffff\"><font size=\"-1\"><b><a href=\"javascript:location.href='querystats.jsp?sortByTime=true';\">Average Time</a> (ms)</b></font></td></tr>");

                           alternate = 0;

                           for (int i = 0; i < ((list.length > 10) ? 10 : list.length); i++) {
                              ProfiledConnectionEntry pce = list[i];
                              out.println("<tr><td bgcolor=\"" + ((alternate % 2 == 0) ? "#dddddd" : "#ffffff") + "\"><font size=\"-1\">" + pce.sql + "</font></td>");
                              out.println("<td bgcolor=\"" + ((alternate % 2 == 0) ? "#dddddd" : "#ffffff") + "\"><font size=\"-1\">" + pce.count + "</font></td>");
                              out.println("<td bgcolor=\"" + ((alternate % 2 == 0) ? "#dddddd" : "#ffffff") + "\"><font size=\"-1\">" + pce.totalTime + "</font></td>");
                              out.println("<td bgcolor=\"" + ((alternate++ % 2 == 0) ? "#dddddd" : "#ffffff") + "\"><font size=\"-1\">" + pce.totalTime / pce.count + "</font></td></tr>");
                           }
                           out.println("</table>");
                        }
                     %></td>
               </tr>
            </table>
         </td>
      </tr>
   </table>

</ul>

<font size="-1"><b>Update Query Statistics</b></font>

<ul>

   <table bgcolor="<%= tblBorderColor %>" cellpadding="0" cellspacing="0" border="0" width="600">
      <tr>
         <td>
            <table bgcolor="<%= tblBorderColor %>" cellpadding="3" cellspacing="1" border="0" width="100%">
               <tr bgcolor="#ffffff">
                  <td><font size="-1">Total # of updates</font></td>
                  <td><font size="-1"><%= ProfiledConnection.getQueryCount(ProfiledConnection.UPDATE) %>
                  </font></td>
               </tr>
               <tr bgcolor="#ffffff">
                  <td><font size="-1">Total time for all updates (ms)</font></td>
                  <td><font size="-1"><%= ProfiledConnection.getTotalQueryTime(ProfiledConnection.UPDATE) %>
                  </font></td>
               </tr>
               <tr bgcolor="#ffffff">
                  <td><font size="-1">Average time for all updates (ms)</font></td>
                  <td><font
                     size="-1"><%= buildStringValue(ProfiledConnection.getAverageQueryTime(ProfiledConnection.UPDATE), 3) %>
                  </font></td>
               </tr>
               <tr bgcolor="#ffffff">
                  <td><font size="-1">Updates per second</font></td>
                  <td><font
                     size="-1"><%= buildStringValue(ProfiledConnection.getQueriesPerSecond(ProfiledConnection.UPDATE), 3) %>
                  </font></td>
               </tr>
               <tr bgcolor="#ffffff">
                  <td><font size="-1">10 Most common updates</font></td>
                  <td bgcolor="#ffffff"><%
                     list = ProfiledConnection.getSortedQueries(ProfiledConnection.UPDATE, sortByTime);

                     if (list == null || list.length < 1) {
                        out.println("<font size=\"-1\">No queries</font>");
                     } else { %>
                     &nbsp;
                  </td>
               </tr>
            </table>
         </td>
      </tr>
   </table>

   <br>

   <table bgcolor="<%= tblBorderColor %>" cellpadding="0" cellspacing="0" border="0" width="600">
      <tr>
         <td>
            <table bgcolor="<%= tblBorderColor %>" cellpadding="3" cellspacing="0" border="0" width="100%">
               <tr bgcolor="#ffffff">
                  <td>
                     <%
                           out.println("<table width=\"100%\" cellpadding=\"3\" cellspacing=\"1\" border=\"0\" bgcolor=\"" + tblBorderColor + "\"><tr><td bgcolor=\"#ffffff\" align=\"middle\"><font size=\"-1\"><b>Query</b></font></td>");
                           out.println("<td bgcolor=\"#ffffff\"><font size=\"-1\"><b><a href=\"javascript:location.href='querystats.jsp?sortByTime=false';\">Count</a></b></font></td>");
                           out.println("<td bgcolor=\"#ffffff\"><font size=\"-1\"><b>Total Time (ms)</b></font></td>");
                           out.println("<td bgcolor=\"#ffffff\"><font size=\"-1\"><b><a href=\"javascript:location.href='querystats.jsp?sortByTime=true';\">Average Time</a> (ms)</b></font></td></tr>");

                           alternate = 0;

                           for (int i = 0; i < ((list.length > 10) ? 10 : list.length); i++) {
                              ProfiledConnectionEntry pce = list[i];
                              out.println("<tr><td bgcolor=\"" + ((alternate % 2 == 0) ? "#dddddd" : "#ffffff") + "\"><font size=\"-1\">" + pce.sql + "</font></td>");
                              out.println("<td bgcolor=\"" + ((alternate % 2 == 0) ? "#dddddd" : "#ffffff") + "\"><font size=\"-1\">" + pce.count + "</font></td>");
                              out.println("<td bgcolor=\"" + ((alternate % 2 == 0) ? "#dddddd" : "#ffffff") + "\"><font size=\"-1\">" + pce.totalTime + "</font></td>");
                              out.println("<td bgcolor=\"" + ((alternate++ % 2 == 0) ? "#dddddd" : "#ffffff") + "\"><font size=\"-1\">" + pce.totalTime / pce.count + "</font></td></tr>");
                           }
                           out.println("</table>");
                        }
                     %></td>
               </tr>
            </table>
         </td>
      </tr>
   </table>

</ul>

<font size="-1"><b>Delete Query Statistics</b></font>

<ul>

   <table bgcolor="<%= tblBorderColor %>" cellpadding="0" cellspacing="0" border="0" width="600">
      <tr>
         <td>
            <table bgcolor="<%= tblBorderColor %>" cellpadding="3" cellspacing="1" border="0" width="100%">
               <tr bgcolor="#ffffff">
                  <td><font size="-1">Total # of deletes</font></td>
                  <td><font size="-1"><%= ProfiledConnection.getQueryCount(ProfiledConnection.DELETE) %>
                  </font></td>
               </tr>
               <tr bgcolor="#ffffff">
                  <td><font size="-1">Total time for all deletes (ms)</font></td>
                  <td><font size="-1"><%= ProfiledConnection.getTotalQueryTime(ProfiledConnection.DELETE) %>
                  </font></td>
               </tr>
               <tr bgcolor="#ffffff">
                  <td><font size="-1">Average time for all deletes (ms)</font></td>
                  <td><font
                     size="-1"><%= buildStringValue(ProfiledConnection.getAverageQueryTime(ProfiledConnection.DELETE), 3) %>
                  </font></td>
               </tr>
               <tr bgcolor="#ffffff">
                  <td><font size="-1">Deletes per second</font></td>
                  <td><font
                     size="-1"><%= buildStringValue(ProfiledConnection.getQueriesPerSecond(ProfiledConnection.DELETE), 3) %>
                  </font></td>
               </tr>
               <tr bgcolor="#ffffff">
                  <td><font size="-1">10 Most common deletes</font></td>
                  <td bgcolor="#ffffff"><%
                     list = ProfiledConnection.getSortedQueries(ProfiledConnection.DELETE, sortByTime);

                     if (list == null || list.length < 1) {
                        out.println("<font size=\"-1\">No queries</font>");
                     } else { %>
                     &nbsp;
                  </td>
               </tr>
            </table>
         </td>
      </tr>
   </table>

   <br>

   <table bgcolor="<%= tblBorderColor %>" cellpadding="0" cellspacing="0" border="0" width="600">
      <tr>
         <td>
            <table bgcolor="<%= tblBorderColor %>" cellpadding="3" cellspacing="0" border="0" width="100%">
               <tr bgcolor="#ffffff">
                  <td>
                     <%
                           out.println("<table width=\"100%\" cellpadding=\"3\" cellspacing=\"1\" border=\"0\" bgcolor=\"" + tblBorderColor + "\"><tr><td bgcolor=\"#ffffff\" align=\"middle\"><font size=\"-1\"><b>Query</b></font></td>");
                           out.println("<td bgcolor=\"#ffffff\"><font size=\"-1\"><b><a href=\"javascript:location.href='querystats.jsp?sortByTime=false';\">Count</a></b></font></td>");
                           out.println("<td bgcolor=\"#ffffff\"><font size=\"-1\"><b>Total Time (ms)</b></font></td>");
                           out.println("<td bgcolor=\"#ffffff\"><font size=\"-1\"><b><a href=\"javascript:location.href='querystats.jsp?sortByTime=true';\">Average Time</a> (ms)</b></font></td></tr>");

                           alternate = 0;

                           for (int i = 0; i < ((list.length > 10) ? 10 : list.length); i++) {
                              ProfiledConnectionEntry pce = list[i];
                              out.println("<tr><td bgcolor=\"" + ((alternate % 2 == 0) ? "#dddddd" : "#ffffff") + "\"><font size=\"-1\">" + pce.sql + "</font></td>");
                              out.println("<td bgcolor=\"" + ((alternate % 2 == 0) ? "#dddddd" : "#ffffff") + "\"><font size=\"-1\">" + pce.count + "</font></td>");
                              out.println("<td bgcolor=\"" + ((alternate % 2 == 0) ? "#dddddd" : "#ffffff") + "\"><font size=\"-1\">" + pce.totalTime + "</font></td>");
                              out.println("<td bgcolor=\"" + ((alternate++ % 2 == 0) ? "#dddddd" : "#ffffff") + "\"><font size=\"-1\">" + pce.totalTime / pce.count + "</font></td></tr>");
                           }
                           out.println("</table>");
                        }
                     %></td>
               </tr>
            </table>
         </td>
      </tr>
   </table>

</ul>

<ul>
   <table bgcolor="<%= tblBorderColor %>" cellpadding="0" cellspacing="0" border="0" width="300">
      <tr>
         <td>
            <table bgcolor="<%= tblBorderColor %>" cellpadding="3" cellspacing="1" border="0" width="100%">
               <tr bgcolor="#ffffff">
                  <td colspan="2">
                     <form action="querystats.jsp">
                        <input type="hidden" name="doClear" value="true">
                        <input type="submit" value="Clear Query Statistics">
                     </form>
                  </td>
               </tr>
            </table>
         </td>
      </tr>
   </table>

</ul>

<p>
   <b>Query Statistics</b>
<p>

      <% } %>


   Enable query statistics to trace all database queries made by Jive Forums. This can
   be useful to debug issues and monitor database performance. However, it's not recommended that
   you leave query statistics permanently running, as they will cause performance to degrade slightly.

<ul>

   <form action="querystats.jsp">
      <table bgcolor="<%= tblBorderColor %>" cellpadding="0" cellspacing="0" border="0" width="300">
         <tr>
            <td>
               <table bgcolor="<%= tblBorderColor %>" cellpadding="3" cellspacing="1" border="0" width="100%">
                  <tr bgcolor="#ffffff">
                     <td align="center" <%= ((doLog) ? "bgcolor=\"#99cc99\"" : "") %>>
                        <font size="-1">
                           <input type="radio" name="doLog" value="true" id="rb01" <%= ((doLog) ? "checked":"") %>>
                           <label for="rb01"><%= ((doLog) ? "<b>On</b>" : "On") %>
                           </label>
                        </font>
                     </td>
                     <td align="center" <%= ((!doLog) ? "bgcolor=\"#cc6666\"" : "") %>>
                        <font size="-1">
                           <input type="radio" name="doLog" value="false" id="rb02" <%= ((!doLog) ? "checked":"") %>>
                           <label for="rb02"><%= ((!doLog) ? "<b>Off</b>" : "Off") %>
                           </label>
                        </font>
                     </td>
                     <td align="center">
                        <input type="submit" value="Update">
                     </td>
                  </tr>
               </table>
            </td>
         </tr>
      </table>
   </form>

</ul>

</body>
</html>


