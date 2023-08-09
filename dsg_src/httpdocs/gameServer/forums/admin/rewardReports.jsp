<%
   /**
    *	$RCSfile: rewardReports.jsp,v $
    *	$Revision: 1.1 $
    *	$Date: 2002/08/16 06:52:22 $
    */
%>

<%@ page import="java.util.*,
                 java.text.*,
                 java.sql.*,
                 com.jivesoftware.util.*,
                 com.jivesoftware.forum.*,
                 com.jivesoftware.forum.database.*,
                 com.jivesoftware.forum.util.*,
                 com.jivesoftware.base.database.ConnectionManager"
         errorPage="error.jsp"
%>

<%@ include file="global.jsp" %>

<%! // Global vars, methods, etc

   private static final int THIS_MONTH = 1;
   private static final int SOME_MONTH = 2;
   private static final int SOME_YEAR = 3;
   private static final String POINT_EARNERS_SQL =
      "SELECT userID, SUM(rewardPoints) AS points FROM jiveReward " +
         "WHERE creationDate > ? AND creationDate <= ? " +
         "GROUP BY userID ORDER BY points desc";
   private static final SimpleDateFormat monthYearDateFormatter
      = new SimpleDateFormat("MMMM, yyyy");
%>

<% // Permission check
   if (!isSystemAdmin) {
      throw new UnauthorizedException("You don't have admin privileges to perform this operation.");
   }

   // get parameters
   int reportType = ParamUtils.getIntParameter(request, "reportType", -1);
   boolean generate = ParamUtils.getBooleanParameter(request, "generate");
   int month = ParamUtils.getIntParameter(request, "month", 1);
   int year1 = ParamUtils.getIntParameter(request, "year1", 2000);
   int year2 = ParamUtils.getIntParameter(request, "year2", 2000);

   UserManager userManager = forumFactory.getUserManager();
%>

<%@ include file="header.jsp" %>

<p>

      <%  // Title of this page and breadcrumbs
    String title = "Reward Reports";
    String[][] breadcrumbs = {
        {"Main", "main.jsp"},
        {title, "rewardReports.jsp"}
    };
%>
   <%@ include file="title.jsp" %>

   <font size="-1">
      Use the forums below to generate reports on reward point usage.
   </font>

<p>

<form action="rewardReports.jsp">
   <input type="hidden" name="generate" value="true">

   <font size="-1">Choose a report to generate:</font>
   <ul>
      <input type="radio" name="reportType" value="<%= THIS_MONTH %>" checked>
      <font size="-1">This month's top point earners</font>
      <p>

         <input type="radio" name="reportType" value="<%= SOME_MONTH %>">
         <font size="-1">Top point earners for the month of</font>
         <select size="1" name="month">
            <option value="0">January
            <option value="1">February
            <option value="2">March
            <option value="3">April
            <option value="4">May
            <option value="5">June
            <option value="6">July
            <option value="7">August
            <option value="8">September
            <option value="9">October
            <option value="10">November
            <option value="11">December
         </select>
         <select size="1" name="year1">
            <% int[] years = new int[]{1999, 2000, 2001, 2002, 2003};
               int curYear = Calendar.getInstance().get(Calendar.YEAR);
               for (int i = 0; i < years.length; i++) {
                  String selected = "";
                  if (curYear == years[i]) {
                     selected = " selected";
                  }
            %>
            <option value="<%= years[i] %>"<%= selected %>><%= years[i] %>
                  <%  } %>
         </select>
      <p>

         <input type="radio" name="reportType" value="<%= SOME_YEAR %>">
         <font size="-1">Top point earners for the year of</font>
         <select size="1" name="year2">
            <% for (int i = 0; i < years.length; i++) {
               String selected = "";
               if (curYear == years[i]) {
                  selected = " selected";
               }
            %>
            <option value="<%= years[i] %>"<%= selected %>><%= years[i] %>
                  <%  } %>
         </select>
   </ul>
   <input type="submit" value="Generate">
</form>

<% if (generate) { %>
<hr>

<center>

   <% if (reportType != THIS_MONTH
      && reportType != SOME_MONTH
      && reportType != SOME_YEAR) {
   %> <font size="-1"><i>Please pick a type of report to run</i></font>
   <% } else {
      Calendar cal = Calendar.getInstance();
      // "bottom" out the calendar by resetting it to the beginning day
      // in this month:
      cal.set(Calendar.DAY_OF_MONTH, 1);
      cal.set(Calendar.HOUR_OF_DAY, 0);
      cal.set(Calendar.MINUTE, 0);
      cal.set(Calendar.SECOND, 0);
      cal.set(Calendar.MILLISECOND, 0);
   %>

   <% if (reportType == THIS_MONTH) {
   %>

   <font size="+1">
      Top Reward Point Earners for <%= monthYearDateFormatter.format(cal.getTime()) %>
   </font>

   <% } else if (reportType == SOME_MONTH) {
      cal.set(Calendar.MONTH, month);
      cal.set(Calendar.YEAR, year1);
   %>

   <font size="+1">
      Top Reward Point Earners for <%= monthYearDateFormatter.format(cal.getTime()) %>
   </font>

   <% } else if (reportType == SOME_YEAR) {
      cal.set(Calendar.DAY_OF_YEAR, 1);
      cal.set(Calendar.YEAR, year2);
   %>

   <font size="+1">
      Top Reward Point Earners for the Year of <%= year2 %>
   </font>

   <% } else {
   } %>

   <p>

   <table cellpadding="3" cellspacing="0" border="1" width="80%">
      <tr>
         <td align="center" width="10%">&nbsp;</td>
         <td align="center" width="45%"><font size="-1"><b>User</b></font></td>
         <td align="center" width="45%"><font size="-1"><b>Points</b></font></td>
      </tr>

      <%
         Connection con = null;
         PreparedStatement pstmt = null;
         try {
            con = ConnectionManager.getConnection();
            pstmt = con.prepareStatement(POINT_EARNERS_SQL);
            if (reportType == THIS_MONTH) {
               pstmt.setString(1, StringUtils.dateToMillis(cal.getTime()));
               cal.add(Calendar.MONTH, 1);
               pstmt.setString(2, StringUtils.dateToMillis(cal.getTime()));
            } else if (reportType == SOME_MONTH) {
               pstmt.setString(1, StringUtils.dateToMillis(cal.getTime()));
               cal.add(Calendar.MONTH, 1);
               pstmt.setString(2, StringUtils.dateToMillis(cal.getTime()));
            } else if (reportType == SOME_YEAR) {
               pstmt.setString(1, StringUtils.dateToMillis(cal.getTime()));
               cal.add(Calendar.YEAR, 1);
               pstmt.setString(2, StringUtils.dateToMillis(cal.getTime()));
            }
            ResultSet rs = pstmt.executeQuery();
            int count = 0;
            boolean noData = true;
            while (rs.next() && (count++ < 10)) {
               noData = false;
               long userID = rs.getLong(1);
               int points = rs.getInt(2);
      %>
      <tr>
         <td align="right"><font size="-1"><%= count %>
         </font></td>
         <td align="center"><font size="-1"><%= userManager.getUser(userID).getUsername() %>
         </font></td>
         <td align="center"><font size="-1"><%= points %>
         </font></td>
      </tr>
      <% }
         if (noData) {
      %>
      <tr>
         <td colspan="3" align="center"><font size="-1"><i>No data for this time period.</i></font></td>
      </tr>
      <%
            }
         } catch (SQLException sqle) {
            sqle.printStackTrace();
         } finally {
            try {
               pstmt.close();
            } catch (Exception e) {
               e.printStackTrace();
            }
            try {
               con.close();
            } catch (Exception e) {
               e.printStackTrace();
            }
         }
      %>
   </table>

   <% }
   }
   %>

</center>

<%@ include file="footer.jsp" %>
