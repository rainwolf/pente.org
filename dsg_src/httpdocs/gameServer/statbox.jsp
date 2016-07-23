

<%! private static final DateFormat dsgDateDf = new SimpleDateFormat("MM/dd/yyyy"); %>
<%! private static final DateFormat dsgTimeDf = new SimpleDateFormat("HH:mm z"); %>
<%! private static final TimeZone dsgTz = TimeZone.getTimeZone("CEST"); %>
<%
int hoursDiff = 0;
if (me != null) { 
	DSGPlayerData meData1 = dsgPlayerStorer.loadPlayer(me);
	TimeZone tz = TimeZone.getTimeZone(meData1.getTimezone());
	hoursDiff = (dsgTz.getRawOffset() - tz.getRawOffset()) / (1000 * 60 * 60);
}
%>
<div class="box">
  <div class="boxhead">
    <h4>Site Stats</h4>
  </div>
  <div id="stats" class="boxcontents" style="padding-left:15px;">

      <a href="/gameServer/controller/search?quick_start=1">Games</a>:
        <%= numberFormat.format(siteStatsData.getNumGames()) %><br>
      <a href="/gameServer/statsMain.jsp">Players</a>:
       <%= numberFormat.format(siteStatsData.getNumPlayers()) %><br>
      <br>
      <font size="-2">Date: <%= dsgDateDf.format(new Date()) %><br>
      Time: <%= dsgTimeDf.format(new Date()) %>
     <font color="<%= textColor2 %>"><b>
     <% if (hoursDiff >= 0) { %>+<% } %><%= hoursDiff %></b></font>
      </font>
  </div>
</div>