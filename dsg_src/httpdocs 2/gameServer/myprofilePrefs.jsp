<%@ page import="java.util.*,
                 org.pente.gameServer.core.*, 
                 com.jivesoftware.forum.*" %>

<%@ page import="com.jivesoftware.forum.action.SettingsAction,
                 java.util.Locale" %>
                 
<%
DSGPlayerData dsgPlayerData = (DSGPlayerData) request.getAttribute("dsgPlayerData");
if (dsgPlayerData == null) {
	throw new Exception("Illegal access attempted");
}
List prefs = (List) request.getAttribute("prefs");
List<Date> vacationDays = (List<Date>) request.getAttribute("vacationDays");
List<DSGIgnoreData> ignoreData = (List<DSGIgnoreData>) request.getAttribute("ignoreData");
String grs = "800";
boolean email = true;
boolean emailSent = false;
int weekend[]=new int[] { 7, 1 };
int refresh = 5;
if (prefs != null) {
	for (Iterator it = prefs.iterator(); it.hasNext();) {
		DSGPlayerPreference p = (DSGPlayerPreference) it.next();
		if (p.getName().equals("gameRoomSize")) {
			grs = (String) p.getValue();
		}
		else if (p.getName().equals("emailDsgMessages")) {
			email = ((Boolean) p.getValue()).booleanValue();
		}
		else if (p.getName().equals("emailSentDsgMessages")) {
			emailSent = ((Boolean) p.getValue()).booleanValue();
		}
		else if (p.getName().equals("weekend")) {
			weekend = (int[]) p.getValue();
		}
		else if (p.getName().equals("refresh")) {
		    refresh = ((Integer) p.getValue());
		}
	}
}

String changeProfileError = (String) request.getAttribute("changeProfileError");
String changeProfileSuccess = (String) request.getAttribute("changeProfileSuccess");


pageContext.setAttribute("title", "My Profile"); 
pageContext.setAttribute("current", "My Profile");
%>
<%@ include file="begin.jsp" %>
<link rel="stylesheet" type="text/css" href="<%= request.getContextPath() %>/gameServer/forums/style.jsp" />


<%  String selectedTab = "Preferences"; %>
<%@ include file="tabs.jsp" %>




<form enctype="multipart/form-data"
      name="change_profile_form"
      method="post"
      action="/gameServer/myprofile/prefs">


<table width="100%" border="0" colspacing="0" colpadding="0">


<tr>
 <td>
  <h2>Preferences</h2>
 </td>
</tr>


<% if (changeProfileError != null) { %>

<tr>
 <td>
  <b><font face="Verdana, Arial, Helvetica, sans-serif" size="2" color="<%= textColor2 %>">
   Changing profile failed: <%= changeProfileError %>
  </b></font>
 </td>
</tr>
   
<% } else if (changeProfileSuccess != null) { %>

<tr>
 <td>
  <font face="Verdana, Arial, Helvetica, sans-serif" size="2" color="<%= textColor2 %>">
   <b><%= changeProfileSuccess %></b>
  </font>
 </td>
</tr>

<%
   }
%>


<tr>
  <td>
  <a name="gameRoomSize"><h3>Game Room Size</h3></a>
   </td>
</tr>
<tr>
  <td>
    Size of the game room window:
    <select name="gameRoomSize">
      <option value="640" <% if (grs.equals("640")) { %>selected<% } %>>640x480 (default)</option>
      <option value="800" <% if (grs.equals("800")) { %>selected<% } %>>800x600</option>
    </select>
  </td>
</tr> 
<tr>
  <td>
  <a name="refresh"><h3>Page Refresh Frequency</h3></a>
   </td>
</tr>
<tr>
  <td>
    Refresh dashboard every:
    <select name="refresh">
      <% int[] refreshvals = new int[] { 0, 1, 2, 3, 4, 5, 10, 15, 20, 25, 30, 60 };
         for (int i = 0; i < refreshvals.length; i++) { %>
           <option value="<%= refreshvals[i] %>"
           <%= ((refresh == refreshvals[i]) ? " selected" : "") %>>
             <%= i == 0 ? "No refresh" : refreshvals[i] + " minutes" %>
           </option>
      <% } %>
    </select>
  </td>
</tr> 
<tr>
  <td>
  <a name="email"><h3>Email</h3></a>
   </td>
</tr>
<tr>
  <td>
    <input type="checkbox" name="email" value="Y" <% if (email) { %>checked<% } %>>Email me a copy of DSG messages I receive<br>
    <input type="checkbox" name="emailSent" value="Y" <% if (emailSent) { %>checked<% } %>>Email me a copy of DSG messages I send
  </td>
</tr>
<tr>
  <td>
  <a name="turnBased"><h3>Turn-based</h3></a>
   </td>
</tr>
<tr>
<%! private static final String days[] = new String[] {
		"", "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", 
		"Saturday"
	};
%>
  <td>
    <b>Vacation and Weekend Days</b><br>
    Vacation and weekend days protect your turn-based games from running out of time.
    You can choose up to 10 days a year for vacation.  You have <%= (10 - vacationDays.size()) %>
    vacation days left.
  </td>
</tr>
<tr>
  <td>
    <br>
    <b>Weekend days:</b> <select name="weekend1">
<% for (int i = 1; i < 8; i++) { %>
      <option value="<%= i %>" <% if (weekend[0] == i) { %>selected<% } %>><%= days[i] %>
<% } %>
    </select>&nbsp;&nbsp;&nbsp;
    <select name="weekend2">
<% for (int i = 1; i < 8; i++) { %>
      <option value="<%= i %>" <% if (weekend[1] == i) { %>selected<% } %>><%= days[i] %>
<% } %>
    </select>
    <br>
    <font size="-2">Be careful when updating the weekend days, if you have any games with less
    than 2 days left for you to move and you change the weekend days, its possible
    that you will force a timeout on yourself!</font>
  </td>
</tr>
<tr>
   <td><br>
     <b>Vacation days:</b><br>

     <%@ include file="calendar.jspf" %>
   </td>
</tr>
<tr>
  <td>
  <a name="ignore"><h3>Ignored Players</h3></a>
   </td>
</tr>
<tr>
 <td>
   The players you are ignoring are shown below.  To ignore invitations (both
   live and turn-based) or
   messages (both game room chat and through Pente.org website) from a player change the checkboxes below and save.  To ignore a
   new player, enter in the player name and checkboxes and save.
 </td>
</tr>
<tr>
  <td>
    <table border="1" cellpadding="3" cellspacing="0" bordercolor="gray">
     <tr bgcolor="<%= bgColor2 %>">
      <td><b>Name</font></td>
      <td><b>Ignore Invites</td>
      <td><b>Ignore Messages</td>
     </tr>
       <% for (DSGIgnoreData i : ignoreData) { 
              DSGPlayerData ignored = dsgPlayerStorer.loadPlayer(i.getIgnorePid()); %>
         <tr>
           <td><b><a href="../profile?viewName=<%= ignored.getName() %>"><%= ignored.getName() %></a></b></td>
           <td><input type="checkbox" name="<%= i.getIgnorePid() %>_invite"<%= i.getIgnoreInvite() ? " checked" : "" %> value="Y"></td>
           <td><input type="checkbox" name="<%= i.getIgnorePid() %>_chat"<%= i.getIgnoreChat() ? " checked" : "" %> value="Y"></td>
        </tr>
       <% } %>
      <tr>
        <td><input type="text" size="10" name="ignore_name"></td>
        <td><input type="checkbox" name="ignore_invite" value="Y"></td>
        <td><input type="checkbox" name="ignore_chat" value="Y"></td>
      </tr>
    </table>
  </td>
</tr>

<tr>
  <td>
    <br><br>
    <input type="submit" value="Save changes">
  </td>
</tr>
</table>
</form>

<%@ include file="end.jsp" %>