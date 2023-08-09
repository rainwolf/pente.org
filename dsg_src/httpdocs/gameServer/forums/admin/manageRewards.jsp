<%
   /**
    *	$RCSfile: manageRewards.jsp,v $
    *	$Revision: 1.2 $
    *	$Date: 2002/08/19 23:30:00 $
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

   private static final String USER_POINTS_SQL
      = "SELECT creationDate,rewardPoints,messageID,threadID FROM jiveReward WHERE userID=? ORDER BY creationDate DESC";
%>

<% // Permission check
   if (!isSystemAdmin) {
      throw new UnauthorizedException("You don't have admin privileges to perform this operation.");
   }

   // get parameters
   long userID = ParamUtils.getLongParameter(request, "userID", -1L);
   String username = ParamUtils.getParameter(request, "username");
   boolean loadUser = ParamUtils.getBooleanParameter(request, "loadUser");
   int numPoints = ParamUtils.getIntParameter(request, "numPoints", 0);
   boolean addPoints = ParamUtils.getBooleanParameter(request, "addPoints");
   int start = ParamUtils.getIntParameter(request, "start", 0);
   int range = ParamUtils.getIntParameter(request, "range", 2);
   boolean enable = "enable".equals(request.getParameter("toggle"));
   boolean disable = "disable".equals(request.getParameter("toggle"));
   boolean doToggle = request.getParameter("doToggle") != null;

   if (doToggle && enable) {
      JiveGlobals.setJiveProperty("rewards.enabled", "true");
      response.sendRedirect("manageRewards.jsp");
      return;
   }
   if (doToggle && disable) {
      JiveGlobals.deleteJiveProperty("rewards.enabled");
      response.sendRedirect("manageRewards.jsp");
      return;
   }

   boolean errors = false;
   String errorMessage = "";
   User user = null;
   if (loadUser) {
      UserManager userManager = forumFactory.getUserManager();
      try {
         user = userManager.getUser(userID);
      } catch (Exception ignored) {
      }
      if (user == null && username != null) {
         try {
            user = userManager.getUser(username);
         } catch (Exception ignored) {
         }
      }
      if (user == null) {
         errors = true;
         errorMessage = "Failed to find the user. Please make sure you entered "
            + "the correct username or user ID";
      }
   }

   int currentUserRewardPoints = 0;
   int totalUserRewardPoints = 0;
   if (!errors && user != null) {
      RewardManager rewardManager = forumFactory.getRewardManager();
      if (addPoints) {
         try {
            rewardManager.addPoints(user, numPoints);
         } catch (Exception e) {
            e.printStackTrace();
         }
         response.sendRedirect("manageRewards.jsp?userID=" + user.getID()
            + "&loadUser=true");
         return;
      }
      currentUserRewardPoints = rewardManager.getCurrentPoints(user);
      totalUserRewardPoints = rewardManager.getTotalPointsEarned(user);
   }

   enable = "true".equals(JiveGlobals.getJiveProperty("rewards.enabled"));
%>

<%@ include file="header.jsp" %>

<p>

      <%  // Title of this page and breadcrumbs
    String title = "Manage Reward Points";
    String[][] breadcrumbs = {
        {"Main", "main.jsp"},
        {"Manage Reward Points", "manageRewards.jsp"}
    };
%>
   <%@ include file="title.jsp" %>

   <font size="-1">
      Reward points are a virtual currency between users that can encourage people
      to answer each other's questions. Use the settings below to control this feature,
      but please note that reward points functionality is not in the default
      Jive skin.
   </font>

<p>

<form action="manageRewards.jsp">
   <font size="-1">
      To enable or disable rewards points, use the form below:
   </font>
   <p>
   <ul>
      <table cellpadding="2" cellspacing="0" border="0">
         <tr>
            <td><input type="radio" name="toggle" value="enable" id="rb01"<%= (enable?" checked":"") %>></td>
            <td><font size="-1">
               <label for="rb01">
                  Enable Reward Points
               </label>
            </font>
            </td>
         </tr>
         <tr>
            <td><input type="radio" name="toggle" value="disable" id="rb02"<%= (!enable?" checked":"") %>></td>
            <td><font size="-1">
               <label for="rb02">
                  Disable Reward Points
               </label>
            </font>
            </td>
         </tr>
      </table>
      <p>
         <input type="submit" name="doToggle" value="Save Settings">
      <p>
   </ul>
</form>

<% if (user == null) { %>

<form action="manageRewards.jsp">
   <input type="hidden" name="loadUser" value="true">

      <%  if (errors) { %>
   <i><%= errorMessage %>
   </i>
   <p>
         <%  } %>

      <font size="-1">
         Please enter the username <b>or</b> user ID of the user you'd like to
         manage:
         <p>
      </font>
   <table cellpadding="2" cellspacing="0" border="0" width="100%">
      <tr>
         <td width="10%" rowspan="99">&nbsp;</td>
         <td width="10%">
            <font size="-1">Username</font>
         </td>
         <td width="80%"><input type="text" name="username" value="" size="30"></td>
      </tr>
      <tr>
         <td width="10%">&nbsp;</td>
         <td width="80%"><font size="-1">or</font></td>
      </tr>
      <tr>
         <td width="10%">
            <font size="-1">User ID</font>
         </td>
         <td width="80%"><input type="text" name="userID" value="" size="6"></td>
      </tr>
      <tr>
         <td width="10%">&nbsp;</td>
         <td width="80%"><input type="submit" value="Manage"></td>
      </tr>
   </table>

      <%  } else { %>

   <font size="-1">
      Reward points for user <b><%= user.getUsername() %>
   </b>:
      <p>

      <ul>
         Current Reward Points: <b><%= currentUserRewardPoints %>
      </b><br>
         Total Points Ever Earned: <b><%= totalUserRewardPoints %>
      </ul>

      <form action="manageRewards.jsp">
         <input type="hidden" name="userID" value="<%= user.getID() %>">
         <input type="hidden" name="loadUser" value="true">
         <input type="hidden" name="addPoints" value="true">

         Give points to this user:
         <ul>
            Add <input type="text" name="numPoints" value="" size="5"> point(s) (value may be negative).
            <input type="submit" value="Add">
         </ul>
      </form>

      User reward points transaction history:
      <p>
   </font>
   <ul>
      <table bgcolor="<%= tblBorderColor %>" cellpadding="0" cellspacing="0" border="0" width="">
         <tr>
            <td>
               <table bgcolor="<%= tblBorderColor %>" cellpadding="3" cellspacing="1" border="0" width="100%">
                  <tr bgcolor="#eeeeee">
                     <td><font size="-2">&nbsp;</font></td>
                     <td align="center"><font size="-2" face="verdana"><b>POINTS</b></font></td>
                     <td align="center"><font size="-2" face="verdana"><b>DATE</b></font></td>
                     <td align="center"><font size="-2" face="verdana"><b>THREAD ID</b></font></td>
                     <td align="center"><font size="-2" face="verdana"><b>MESSAGE ID</b></font></td>
                  </tr>
                  <% Connection con = null;
                     PreparedStatement pstmt = null;
                     boolean less = false;
                     boolean more = false;
                     try {
                        con = ConnectionManager.getConnection();
                        pstmt = con.prepareStatement(USER_POINTS_SQL);
                        pstmt.setLong(1, user.getID());
                        ResultSet rs = pstmt.executeQuery();
                        int count = 0;
                        while (count < start) {
                           count++;
                           rs.next();
                           less = true;
                        }
                        while (rs.next() && (count < (start + range))) {
                           long date = rs.getLong(1);
                           int points = rs.getInt(2);
                           long mID = rs.getLong(3);
                           if (rs.wasNull()) {
                              mID = -1L;
                           }
                           long tID = rs.getLong(4);
                           if (rs.wasNull()) {
                              tID = -1L;
                           }

                  %>
                  <tr bgcolor="#ffffff">
                     <td><font size="-1"><%= ++count %>
                     </font></td>
                     <td align="center"><font size="-1"><%= points %>
                     </font></td>
                     <td align="center"><font
                        size="-1">&nbsp;<%= JiveGlobals.formatDateTime(new java.util.Date(date)) %>&nbsp;</font></td>
                     <td align="center"><font size="-1">
                        <% if (tID != -1L) { %>
                        <%= tID %>
                        <% } else { %>
                        &nbsp;
                        <% } %>
                     </font></td>
                     <td align="center"><font size="-1">
                        <% if (mID != -1L) { %>
                        <%= mID %>
                        <% } else { %>
                        &nbsp;
                        <% } %>
                     </font></td>
                  </tr>
                  <%
                        }
                        if (rs.next()) {
                           more = true;
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
            </td>
         </tr>
      </table>
   </ul>

   <br>

   <table cellpadding="3" cellspacing="0" border="0" width="80%" align="center">
      <tr>
         <td width="1%" nowrap>
            <% if (less) { %>
            &laquo;
            <a href="manageRewards.jsp?userID=<%= user.getID() %>&loadUser=true&start=<%= (start-range) %>&range=<%= range %>"
            ><font size="-1">Previous <%= range %> transactions</font></a>
            <% } %>
         </td>
         <td width="98%">&nbsp;</td>
         <td width="1%" nowrap>
            <% if (more) { %>
            <a href="manageRewards.jsp?userID=<%= user.getID() %>&loadUser=true&start=<%= (start+range) %>&range=<%= range %>"
            ><font size="-1">Next <%= range %> transactions</font></a>
            &raquo;
            <% } %>
         </td>
      </tr>
   </table>

      <%  } %>

   </body>
   </html>


