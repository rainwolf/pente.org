<%
   /**
    *	$RCSfile: editRewards.jsp,v $
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
                 com.jivesoftware.forum.util.*"
         errorPage="error.jsp"
%>

<%@ include file="global.jsp" %>

<% // Permission check
   if (!isSystemAdmin) {
      throw new UnauthorizedException("You don't have admin privileges to perform this operation.");
   }

   // get parameters
   int maxPoints = ParamUtils.getIntParameter(request, "maxPoints", -1);
   boolean setProp = ParamUtils.getBooleanParameter(request, "setProp");

   RewardManager rewardManager = forumFactory.getRewardManager();

   boolean errors = false;
   String errorMessage = "";
   if (setProp) {
      if (maxPoints > -1) {
         rewardManager.setMaxPoints(maxPoints);
         response.sendRedirect("editRewards.jsp");
         return;
      } else {
         errors = true;
         errorMessage = "Please enter a valid number greater than zero.";
      }
   }
%>

<%@ include file="header.jsp" %>

<p>

      <%  // Title of this page and breadcrumbs
    String title = "Configure Points";
    String[][] breadcrumbs = {
        {"Main", "main.jsp"},
        {"Configure Points", "editRewards.jsp"}
    };
%>
   <%@ include file="title.jsp" %>

   <font size="-1">
      Set the maximum number of assignable points per thread.
   </font>

<p>

      <%  if (errors) { %>
   <font size="-1"><i><%= errorMessage %>
   </i></font>
<p>
      <%  } %>

<form action="editRewards.jsp">
   <input type="hidden" name="setProp" value="true">
   <% int numMaxPoints = rewardManager.getMaxPoints(); %>
   <font size="-1">
      <% if (numMaxPoints == Integer.MAX_VALUE) { %>
      The current value is: <i>Not Set</i>
      <% } else { %>
      The current value is: <b><%= rewardManager.getMaxPoints() %>
   </b>
      <% } %>
      <ul>
         <input type="text" size="5" name="maxPoints"
            <%  if (numMaxPoints == Integer.MAX_VALUE) { %>
                value="">
         <% } else { %>
         value="<%= rewardManager.getMaxPoints() %>">
         <% } %>

         <input type="submit" value="Set">
      </uL>
   </font>
</form>

<%@ include file="footer.jsp" %>
