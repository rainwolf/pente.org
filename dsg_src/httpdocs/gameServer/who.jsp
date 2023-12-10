<%@ page import="org.pente.game.*" %>

<% ActivityLogger activityLogger = (ActivityLogger)
   application.getAttribute(ActivityLogger.class.getName());
   SessionListener sessionListener = (SessionListener)
      application.getAttribute(SessionListener.class.getName());
   Resources resources = (Resources)
      application.getAttribute(Resources.class.getName());
   DSGPlayerStorer dsgPlayerStorer = resources.getDsgPlayerStorer();


   String nm = (String) request.getAttribute("name");
   int refresh = 0;
   if (nm != null) {
      DSGPlayerData dsgPlayerData = dsgPlayerStorer.loadPlayer(nm);
      refresh = 5;
      List prefs = dsgPlayerStorer.loadPlayerPreferences(
         dsgPlayerData.getPlayerID());
      for (Iterator it = prefs.iterator(); it.hasNext(); ) {
         DSGPlayerPreference p = (DSGPlayerPreference) it.next();
         if (p.getName().equals("refresh")) {
            refresh = ((Integer) p.getValue());
         }
      }
      if (refresh != 0) {
         response.setHeader("Refresh", refresh * 60 + "; URL=who.jsp");
      }
   }

   pageContext.setAttribute("title", "Who's Online");
%>

<%@ include file="begin.jsp" %>


<div style="font-family:Verdana, Arial, Helvetica, sans-serif;
                background:#fffbcc;
	        border:1px solid #e6db55;
	        padding:5px;
		margin-bottom:10px;
	        font-weight:bold;
	        width:90%;">
   Note: You can now see who's online from your <a href="/gameServer/index.jsp">Dashboard</a> page, this page will
   probably go away at some point.
</div>

<table border="0" cellpadding="0" cellspacing="0" width="350">
   <tr>
      <td valign="top"><font size="4">Who's Online</font></td>
      <td valign="bottom" align="right"><font size="-1">
         <% if (nm != null) { %>
         Refresh: <%= refresh == 0 ? "No refresh" : refresh + " minutes" %> -
         <a href="/gameServer/myprofile/prefs">Change</a>
         <% } %>
      </font></td>
      <td width="10">&nbsp;</td>
   </tr>
</table>
<br>
<table border="0" cellpadding="0" cellspacing="0" width="100%">
   <tr>
      <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
            <%  ActivityData d[] = activityLogger.getPlayers(); %>
            <%  if (d.length == 0) { %>
            <% pageContext.setAttribute("playText", "login"); %>
         No one is playing live right now. Why don't you
         <%@ include file="playLink.jspf" %>
         and get things started!<br>
            <%  }
              else {
                //sort into sections by room name, pname (serverid)
                Arrays.sort(d,(o1,o2)-> {
                    ActivityData d1 = (ActivityData) o1;
                    ActivityData d2 = (ActivityData) o2;
                    if (d1.getServerId() != d2.getServerId()) {
                        return (int) (d1.getServerId() - d2.getServerId());
                    }
                    else {
                        return d1.getPlayerName().compareTo(d2.getPlayerName());
                    }
                });
                ArrayList counts = new ArrayList();
                int currentCount = 0;
                long currentServerId = -1;
                for (int i = 0; i < d.length; i++) {
                    if (d[i].getServerId() != currentServerId) {
                        currentServerId = d[i].getServerId();
                        if (currentCount > 0) {
                            counts.add(Integer.valueOf(currentCount));
                        }
                        currentCount = 1;
                    }
                    else {
                        currentCount++;
                    }
                }
                // last room count
                counts.add(Integer.valueOf(currentCount));
                %>


            <%
                currentServerId = -1;
                int countIndex = 0;
                for (int i = 0; i < d.length; i++) {
                    DSGPlayerData dsgPlayerData =
                        dsgPlayerStorer.loadPlayer(d[i].getPlayerName());
                    int tourneyWinner = 0;
                    int totalGames = 0;
                    DSGPlayerGameData dsgPlayerGameData = null;
                    if (dsgPlayerData != null) {
                    	tourneyWinner = dsgPlayerData.getTourneyWinner();
	                    dsgPlayerGameData =
	                        dsgPlayerData.getPlayerGameData(GridStateFactory.PENTE);
	                    totalGames = dsgPlayerData.getTotalGames();
                    }
                    if (d[i].getServerId() != currentServerId) {
                      currentServerId = d[i].getServerId();
                      ServerData sd = resources.getServerData((int) currentServerId);
                      int count = ((Integer) counts.get(countIndex++)).intValue(); 
                      if (i > 0) { %>
</table>
<br>
<% } %>

<table border="1" cellpadding="1" cellspacing="0"
       bordercolor="black" width="350">
   <tr bgcolor="<%= bgColor1 %>">
      <td colspan="3">
         <font color="white">
            <b>Logged in to <%= sd.getName() %> (<%= count %>)</b>
         </font>
      </td>
   </tr>
   <tr>
      <td width="30%"><b>Name</b></td>
      <td><b>Pente Rating</b></td>
      <td><b>Total # Games</b></td>

   </tr>
   <% } %>
   <tr>
      <% if (dsgPlayerData != null) { %>
      <td><a href="<%= request.getContextPath() %>/gameServer/profile?viewName=<%= d[i].getPlayerName() %>">
         <%= d[i].getPlayerName() %>
      </a>
         <%@ include file="tournaments/crown.jspf" %>&nbsp;&nbsp;&nbsp;
      </td>
      <% } else { //guest %>
      <td><%= d[i].getPlayerName() %>
      </td>
      <% } %>
      <td>
         <% if (dsgPlayerGameData != null) { %>
         <%@ include file="ratings.jspf" %>
         <% } else { %>
         &nbsp;
         <% } %>
      </td>

      <td><%= nf.format(totalGames) %>
      </td>
   </tr>
   <% } %>
</table>
<% }%>

<br>
<%
   List<String> names = sessionListener.getActivePlayers();
   String width = (me != null && (me.equals("rainwolf") || me.equals("iostest"))) ? "500" : "350";
   String columns = (me != null && (me.equals("rainwolf") || me.equals("iostest"))) ? "4" : "3";
%>
<table border="1" cellpadding="1" cellspacing="0"
       bordercolor="black" width="<%= width %>">
   <tr bgcolor="<%= bgColor1 %>">
      <td colspan="<%= columns %>">
         <font color="white">
            <b>Browsing Pente.org (<%= names.size() %>)</b>
         </font>
      </td>
   </tr>
   <tr>
      <td width="30%"><b>Name</b></td>
      <td><b>Pente Rating</b></td>
      <td><b>Total # Games</b></td>

      <% if (me != null && (me.equals("rainwolf") || me.equals("iostest"))) { %>
      <td><b>Last Page</b></td>
      <% } %>
   </tr>
   <% for (String name : names) {
      DSGPlayerData dsgPlayerData =
         dsgPlayerStorer.loadPlayer(name);
      DSGPlayerGameData dsgPlayerGameData =
         dsgPlayerData.getPlayerGameData(GridStateFactory.PENTE); %>
   <tr>
      <td><a href="<%= request.getContextPath() %>/gameServer/profile?viewName=<%= name %>">
         <%= name %>
      </a>
         <% int tourneyWinner = dsgPlayerData.getTourneyWinner(); %>
         <%@ include file="tournaments/crown.jspf" %>&nbsp;&nbsp;&nbsp;
      </td>
      <td>
         <% if (dsgPlayerGameData != null) { %>
         <%@ include file="ratings.jspf" %>
         <% } else { %>
         &nbsp;
         <% } %>
      </td>
      <td><%= nf.format(dsgPlayerData.getTotalGames()) %>
      </td>
      <% if (me != null && (me.equals("rainwolf") || me.equals("iostest"))) { %>
      <td><%= sessionListener.getLastPage(name) %>
      </td>
      <% } %>
   </tr>
   <% } %>
</table>
</font>
</td>

<td valign="top" align="right">
   <%@ include file="rightAd.jsp" %>
</td>
</tr>
</table>

<%@ include file="end.jsp" %>
