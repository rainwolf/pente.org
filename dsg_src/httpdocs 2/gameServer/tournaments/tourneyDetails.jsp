<%
DateFormat dateTimeFormat = new SimpleDateFormat("EEE, MMM dd, yyyy hh:mm:ss aa z Z");
{
  if (me != null) { // null for guests
      DSGPlayerData meData = dsgPlayerStorer.loadPlayer(me);
      TimeZone tz = TimeZone.getTimeZone(meData.getTimezone());
      dateTimeFormat.setTimeZone(tz);
  }
}
%>

<table border="0" cellpadding="1" cellspacing="0">
  <tr>
    <td>Tournament game:</td>
    <td><%= tourney.getGameName() %></td>
  </tr>
  <tr>
    <td>Format/Rules:</td>
    <td><a href="/help/helpWindow.jsp?file=tourney<%= tourney.getFormat().getName() %>">
      <%= tourney.getFormat().getName() %></a> / 
      <% if (tourney.getEventID() == 1182) { %>
      <a href="/help/helpWindow.jsp?file=tourneySeventh-Heaven">Official Rules</a></td>
      <% } else { %>
      <a href="/help/helpWindow.jsp?file=tournaments">Official Rules</a></td>
      <% } %>
  </tr>
  <tr>
  <% if (tourney.isSpeed()) { %>
    <td>Speed Tournament:</td>
    <td>Play whole tournament live!</td>
  <% }
     else { %>
    <td>Rounds last:</td>
    <td><%= tourney.getRoundLengthDays() %> Days</td>
  <% } %>
  </tr>
  <tr>
    <td>Game timers:</td>
    <td><%= tourney.getInitialTime() %> minutes initial / 
    <%= tourney.getIncrementalTime() %> seconds incremental</td>
  </tr>
  <tr>
    <td valign="top">Restrictions:</td>
    <td>
    <% if (tourney.getRestrictions().size() == 0) { %>
    None
    <% }
       else {
         for (Iterator it = tourney.getRestrictions().iterator(); it.hasNext();) {
           Restriction restriction = (Restriction) it.next();
           if (restriction.getType() == Restriction.RATING_RESTRICTION_BELOW) { %>
            Rating must be <b>below <%= restriction.getValue() %></b>.<br>
        <% } else if (restriction.getType() == Restriction.RATING_RESTRICTION_ABOVE) { %>
            Rating must be <b>above <%= restriction.getValue() %></b>.<br>
        <% } else if (restriction.getType() == Restriction.GAMES_RESTRICTION_ABOVE) { %>
            Must play at least <b><%= restriction.getValue() %></b> games.<br>
        <% }
         }
       } %>
    </td>
  </tr>
  <tr>
    <td>Signup ends:</td> 
    <td><%= dateTimeFormat.format(tourney.getSignupEndDate()) %></td>
  </tr>
  <tr>
    <td>Play starts:&nbsp;&nbsp;&nbsp;</td>
    <td><%= dateTimeFormat.format(tourney.getStartDate()) %></td>
  </tr>
  <% if (tourney.isComplete()) { %>
  <tr>
    <td>Completion date:&nbsp;&nbsp;&nbsp;</td>
    <td><%= dateTimeFormat.format(tourney.getEndDate()) %></td>
  </tr>
  <tr>
    <td><b>Winner:</b>&nbsp;&nbsp;&nbsp;</td>
    <td><b><a href="/gameServer/profile?viewName=<%= tourney.getWinner() %>">
      <%= tourney.getWinner() %></a> <img src="/gameServer/images/crown.gif"
          alt="Tournament Champ!"></td>
  </tr>
  <% } %>
  <tr>
    <td>Prize:</td>
    <td>
      <% if (tourney.getPrize() == null) { %>
        Respect
      <% } else if (tourney.getPrize().equals("gold")) { %>
        Gold crown <img src="/gameServer/images/crown.gif">
      <% } else if (tourney.getPrize().equals("silver")) { %>
        Silver crown <img src="/gameServer/images/scrown.gif">
      <% } else { %>
        <%= tourney.getPrize() %>
      <% } %>
    </td>
  </tr>
  <tr>
    <td>Director(s):</td>
    <td>
      <% for (int i = 0; i < tourney.getDirectors().size(); i++) {
          Long pid = (Long) tourney.getDirectors().get(i);
          DSGPlayerData d = dsgPlayerStorer.loadPlayer(pid.longValue()); %>
          <a href="/gameServer/profile?viewName=<%= d.getName() %>">
            <%= d.getName() %></a>
       <% if (i + 1 < tourney.getDirectors().size()) { %>,<% } %>
      <% } %>
    </td>
  </tr>
  <tr>
    <td>
     <% if (!tourney.isSpeed() && tourney.getForumID() != 0) { %>
         <a href="/gameServer/forums/forum.jspa?forumID=<%= tourney.getForumID() %>&start=0">
           Tournament Forum</a>&nbsp;&nbsp;
     <% } %>
    </td>
    <td><a href="index.jsp">Back to Tournaments</a></td>
  </tr>
</table>
<br>
