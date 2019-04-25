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

String gameStr = (String) request.getParameter("game");
int gameInt = 51;
boolean allow_followers_be_notified = false, allow_notification_online_from_following = false;
DSGPlayerData meData = dsgPlayerStorer.loadPlayer(name);
List<DSGPlayerPreference> prefs = dsgPlayerStorer.loadPlayerPreferences(meData.getPlayerID());
for (DSGPlayerPreference pref: prefs) {
    if ("socialGame".equals(pref.getName())) {
        gameInt = (Integer) pref.getValue();
    }
    if ("allow_followers_be_notified".equals(pref.getName())) {
        allow_followers_be_notified = ((Boolean) pref.getValue()).booleanValue();
    }
    if ("allow_notification_online_from_following".equals(pref.getName())) {
        allow_notification_online_from_following = ((Boolean) pref.getValue()).booleanValue();
    }
}

    final int game = gameInt;
List<DSGPlayerData> followersData = new ArrayList<>();
for (long pid: followers) {
    followersData.add(dsgPlayerStorer.loadPlayer(pid));
}
List<DSGPlayerData> followingData = new ArrayList<>();
for (long pid: following) {
    followingData.add(dsgPlayerStorer.loadPlayer(pid));
}
Collections.sort(followersData, new Comparator<DSGPlayerData>() {
  @Override
  public int compare(DSGPlayerData o1, DSGPlayerData o2) {
      return Double.compare(o2.getPlayerGameData(game).getRating(), o1.getPlayerGameData(game).getRating());
  }
});
Collections.sort(followingData, new Comparator<DSGPlayerData>() {
  @Override
  public int compare(DSGPlayerData o1, DSGPlayerData o2) {
      return Double.compare(o2.getPlayerGameData(game).getRating(), o1.getPlayerGameData(game).getRating());
  }
});
%>
<br>

This page provides an overview of players you follow and players who follow you. Following someone means you subscribe to updates from that player. There are no updates implemented at the moment, but first one to be implemented is being able to notify followers that you are available to play in the live game room. Non-subscribers can follow up to <%= followingLimit %> players, <a href="https://pente.org/gameServer/subscriptions">subscribers</a> are not limited.

<center>
<br>
   <form name="new_game_form" method="post" 
         action="<%= request.getContextPath() %>/gameServer/social">
         <input type="hidden" name="social">
         <select size="1" id="game" name="game" onchange="this.form.submit()">
         <% Game games[] = GridStateFactory.getAllGames();
            for (int i = 1; i < games.length; i++) { %>
               <option <% if (game == games[i].getId()) { %>selected <% } %> value="<%= games[i].getId() %>"><%= GridStateFactory.getDisplayName(games[i].getId()) %></option>
<%--
               <option <% if (i == 0) { %>selected <% } %>value="<%= games[i].getId() %>"><%= games[i].getName() %></option>
--%>               
         <% } %>
         </select>
<noscript><input type="submit" value="Submit"></noscript>
<br>
       <label>
           <input id="allow_followers_be_notified" <%=(allow_followers_be_notified?"checked":"")%> onchange="this.form.submit()" name="allow_followers_be_notified" type="checkbox" value="true"/> 
           Allow followers be notified when I appear in a live game room. 
       </label>
       <br>
       <label>
           <input id="allow_notification_online_from_following" <%=(allow_notification_online_from_following?"checked":"")%> onchange="this.form.submit()" name="allow_notification_online_from_following" type="checkbox" value="true"/> 
           Send me a notification when people I follow enter a live game room <br> (at most 1 notification per person per 10 minutes.) 
       </label>
       <br>
   </form>
<table width="50%" border="0" colspacing="0" colpadding="0">


<tr>
 <td colspan="2" align="center">
  <a name="followers"><h3>followers</h3></a>
 </td>
 <td colspan="2" align="center">
  <a name="following"><h3>following</h3></a>
 </td>
</tr>

<% for (int i = 0; i < max; i++ ) { %>
<tr>
 <td align="center">
<% if (i < followersSize) { 
  DSGPlayerData d = followersData.get(i); 
  DSGPlayerGameData dsgPlayerGameData = d.getPlayerGameData(game); %>
      <%@ include file="playerLink.jspf" %>
      </td>
      <td>
      <%@ include file="ratings.jspf" %>
<% } else { %>
      </td>
      <td>
<% } %>
 </td>
 <td align="center">
<% if (i < followingSize) { 
  DSGPlayerData d = followingData.get(i);
  DSGPlayerGameData dsgPlayerGameData = d.getPlayerGameData(game); %>
      <%@ include file="playerLink.jspf" %>
      </td>
      <td>
<%@ include file="ratings.jspf" %> 
<% } else { %>
      </td>
      <td>
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