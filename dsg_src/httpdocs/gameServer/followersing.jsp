<%@ page import="java.util.*,
                 org.pente.gameServer.core.*, 
                 com.jivesoftware.forum.*" %>

<%@ page import="com.jivesoftware.forum.action.SettingsAction,
                 java.util.Locale"%>
                 
<% pageContext.setAttribute("title", "My Profile"); %>
<% pageContext.setAttribute("current", "My Profile"); %>
<%@ include file="begin.jsp" %>
<link rel="stylesheet" type="text/css" href="<%= request.getContextPath() %>/gameServer/forums/style.jsp" />


<%  String selectedTab = "Social"; %>
<%@ include file="tabs.jsp" %>

<%
String name = (String) request.getAttribute("name");
if (name != null) {
List<Long> followers = (List<Long>) request.getAttribute("followers");
List<Long> following = (List<Long>) request.getAttribute("following");
int followersSize = followers.size(), followingSize = following.size();
int max = followersSize>followingSize?followersSize:followingSize;
ServletContext ctx = getServletContext();
int followingLimit = Integer.parseInt(ctx.getInitParameter("NONSUBSCRIBERFOLLOWINGLIMIT"));

%>
<br>

This page provides an overview of players you follow and players who follow you. Following someone means you subscribe to updates from that player. There are no updates implemented at the moment, but first one to be implemented is being able to notify followers that you are available to play in the live game room. Non-subscribers can follow up to <%= followingLimit %> players, <a href="https://pente.org/gameServer/subscriptions">subscribers</a> are not limited.

<center>
<table width="50%" border="0" colspacing="0" colpadding="0">


<tr>
 <td>
  <a name="followers"><h3>followers</h3></a>
 </td>
 <td>
  <a name="following"><h3>following</h3></a>
 </td>
</tr>

<% for (int i = 0; i < max; i++ ) { %>
<tr>
 <td>
<% if (i < followersSize) { 
  DSGPlayerData d = dsgPlayerStorer.loadPlayer(followers.get(i)); %>
      <%@ include file="playerLink.jspf" %> &nbsp;
<% } %>
 </td>
 <td>
<% if (i < followingSize) { 
  DSGPlayerData d = dsgPlayerStorer.loadPlayer(following.get(i)); %>
      <%@ include file="playerLink.jspf" %> &nbsp;
<% } %>
 </td>
</tr>

<% } %>

</table>
  
</center>
<br>
<br>


<% } else { %>
  <h3>Error: you have to login first</h3>
<% } %>


<%@ include file="end.jsp" %>