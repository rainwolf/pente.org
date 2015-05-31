
<% 
    //TODO wasteful, keep track with sessionlistener perhaps
    int onlinePlayers = 0;
    List<String> seen = new ArrayList<String>();
    {
	    SessionListener sl = (SessionListener)
	        application.getAttribute(SessionListener.class.getName());
	     
	    Object sessions[] = sl.getActiveSessions().toArray();
	
		for (int i = 0; i < sessions.length; i++) {
		    HttpSession s = (HttpSession) sessions[i];
			try {
				if (s == null) continue;
				String n = (String) s.getAttribute("name");
			    if (n != null &&
			        !seen.contains(n)) {
			        onlinePlayers++;
			        seen.add(n);
		        }
		    } catch (IllegalStateException ignore) {}
		}
    } 
%>

<%! private static final DateFormat dsgDateDf = new SimpleDateFormat("MM/dd/yyyy"); %>
<%! private static final DateFormat dsgTimeDf = new SimpleDateFormat("HH:mm z"); %>
<%! private static final TimeZone dsgTz = TimeZone.getTimeZone("America/New_York"); %>
<%
int hoursDiff = 0;
if (me != null) { 
	DSGPlayerData meData = dsgPlayerStorer.loadPlayer(me);
	TimeZone tz = TimeZone.getTimeZone(meData.getTimezone());
	hoursDiff = (dsgTz.getRawOffset() - tz.getRawOffset()) / (1000 * 60 * 60);
}
%>
			  
  <tr>
    <td bgcolor="black">
      <table border="0" cellspacing="0" cellpadding="3" width="100%">
        <tr>
          <td bgcolor="<%= bgColor1 %>">
            <font face="Verdana, Arial, Helvetica, sans-serif" size="3" color="<%= textColor1 %>">
              <b>Site Stats</b>
            </font>
          </td>
        </tr>
        <tr>
          <td bgcolor="<%= bgColor2 %>">
            <a href="/gameServer/controller/search?quick_start=1">Games</a>:
              <%= numberFormat.format(siteStatsData.getNumGames()) %><br>
            <a href="/gameServer/statsMain.jsp">Players</a>:
             <%= numberFormat.format(siteStatsData.getNumPlayers()) %><br>
            &nbsp;&nbsp;<a href="/gameServer/who.jsp">Playing Live</a>:
              <%= siteStatsData.getNumCurrentPlayers() %><br>
            &nbsp;&nbsp;<a href="/gameServer/who.jsp">Online</a>:
              <%= onlinePlayers %><br>
            <font size="-2">Date: <%= dsgDateDf.format(new Date()) %><br>
            Time: <%= dsgTimeDf.format(new Date()) %>
	          <font color="<%= textColor2 %>"><b>
	          <% if (hoursDiff >= 0) { %>+<% } %><%= hoursDiff %></b></font>
            </font>
        </td></tr>
          </td>
        </tr>
      </table>
    </td>
  </tr>