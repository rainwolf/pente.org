<%@ page import="java.util.*" %>
<%@ page import="java.text.*" %>
<%@ page import="org.pente.game.GridStateFactory" %>
<%@ page import="org.pente.gameServer.core.*" %>

<%!
private static final NumberFormat profileNF = NumberFormat.getPercentInstance();

private static Date regCutoff = null;
static {
    Calendar cal = Calendar.getInstance();
    cal.set(Calendar.YEAR, 2001);
    cal.set(Calendar.MONTH, 12);
    cal.set(Calendar.DATE, 17);
    regCutoff = cal.getTime();
}
%>


<%
String conditionalHeading = "Search For a Player";

// dsgPlayerData will be null if page was requested prior to login.
// ( login is forced before page is accessible, but no dsgPlayerData is passed. )

DSGPlayerData dsgPlayerData = (DSGPlayerData) request.getAttribute("dsgPlayerData");

if (dsgPlayerData != null){
    conditionalHeading = "Player Profile";
}

%>

<% pageContext.setAttribute("title", conditionalHeading); %>
<%@ include file="begin.jsp" %>

<%
int hoursDiff2 = 0;
DateFormat profileDateFormat = null;
TimeZone playerTimeZone = null;
if (dsgPlayerData != null) { 
	DSGPlayerData meData = dsgPlayerStorer.loadPlayer(me);
	TimeZone tz = TimeZone.getTimeZone(meData.getTimezone());
	profileDateFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm z");
	profileDateFormat.setTimeZone(tz);
	
	playerTimeZone = TimeZone.getTimeZone(dsgPlayerData.getTimezone());
	hoursDiff2 = (playerTimeZone.getRawOffset() - tz.getRawOffset()) / (1000 * 60 * 60);
}
%>

<table border="0" cellpadding="2" cellspacing="0" width="100%">
<tr>
 <td>
  <h3><%= conditionalHeading %></h3>
 </td>
</tr>

<%
   String viewProfileError = (String) request.getAttribute("viewProfileError");
   if (viewProfileError != null) { %>

<tr><td>&nbsp;</td></tr>
<tr>
 <td>
  <font face="Verdana, Arial, Helvetica, sans-serif" size="2" color="<%= textColor2 %>">
   Profile search failed: <%= viewProfileError %>
  </font>
 </td>
</tr>
<tr><td>&nbsp;</td></tr>

<%   
   }
%>

<% if (dsgPlayerData != null) {
%>

<tr>
 <td>
   <table border="0" colspacing="1" colpadding="1">
   
     <% String name = (String) request.getAttribute("name");
        if (dsgPlayerData.getName().equals(name)) { %>
        
     <tr>
      <td colspan="2">
       <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
        <b><a href="myprofile">Edit my profile</a></b>
       </font>
      </td>
        
     <% } %>
   
     <tr>
      <td>
       <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
        <b>Name:</b>
       </font>
      </td>
     <% int tourneyWinner = dsgPlayerData.getTourneyWinner(); %>
     <% if (name.equals("invictus") || name.equals("rainwolf") || name.equals("katysmom")) { %>
	  <td colspan="2">
       <font face="Verdana, Arial, Helvetica, sans-serif" size="2" color="<%= textColor2 %>"><b>
            <form method="GET" action="setKOTHcrown.jsp">
            <input type="hidden" name="kothname" value="<%= dsgPlayerData.getName() %>" />
            <input type="hidden" name="name" value="<%= name %>" />
         <%= dsgPlayerData.getName() %>
         <% if (dsgPlayerData.isAdmin()) { %> (admin) <% } %>
         <%@ include file="tournaments/crown.jspf" %>
                <input type="submit" value="make KoTH winner">
            </form>
       </b></font>
      </td>
     <% } else { %>
      <td>
       <font face="Verdana, Arial, Helvetica, sans-serif" size="2" color="<%= textColor2 %>"><b>
         <%= dsgPlayerData.getName() %>
         <% if (dsgPlayerData.isAdmin()) { %> (admin) <% } %>
         <%@ include file="tournaments/crown.jspf" %>
       </b></font>
      </td>
     <% } %>
     </tr>
     <% if (name != null && name.equals("rainwolf")) { %>
     <tr>
       <td>
         <font face="Verdana, Arial, Helvetica, sans-serif" size="2"><b>ID:</b></font>
       </td>
       <td>
         <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
           <%= dsgPlayerData.getPlayerID() %>
         </font>
       </td>
     </tr>
     <% } %>
     <% if (dsgPlayerData.isComputer()) { %>
     <tr>
      <td>
       <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
        <b>Type:</b>
       </font>
      </td>
      <td>
       <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
         Computer Opponent
       </font>
      </td>
     </tr>
     <% } %>
     <tr>
      <td>
       <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
        <b><a href="subscriptions">Subscriber</a>:</b>
       </font>
      </td>
      <td>
       <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
         <% if (dsgPlayerData.hasPlayerDonated()) { %>
         Yes
         <% } else if (!dsgPlayerData.getName().equals(name)) { %>

         <form method="POST" action="subscriptions">
            <input type="hidden" name="gifter" value="<%=name %>" />
            <input type="hidden" name="name" value="<%= dsgPlayerData.getName() %>" />
         No 
                <input type="submit" value="Gift <%= dsgPlayerData.getName() %> a subscription">
            </form>
         <% } else { %> 
         No
         <%}%>
       </font>
      </td>
     </tr>
     <tr>
      <td>
       <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
        <b>Member since:</b>
       </font>
      </td>
      <td>
       <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
         <%   // data not kept prior to 2001-12-16
             if (regCutoff.after(dsgPlayerData.getRegisterDate())) {
               out.println("Sometime before Pente.org kept records (12/16/2001)");
             }
             else {
               out.println(dateFormat.format(dsgPlayerData.getRegisterDate()));
             }
         %>
       </font>
      </td>
     </tr>
     <tr>
      <td>
       <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
        <b>Logins:</b>
       </font>
      </td>
      <td>
       <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
         <%= dsgPlayerData.getLogins() %>
       </font>
      </td>
     </tr>
     <tr>
      <td>
       <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
        <b>Last Login Date:</b>
       </font>
      </td>
      <td>
       <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
         <%= profileDateFormat.format(dsgPlayerData.getLastLoginDate()) %>
       </font>
      </td>
     </tr>
     <tr>
      <td>
       <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
        <b>Time Zone:</b>
       </font>
      </td>
      <td>
       <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
         <%= playerTimeZone.getID() %> 
         <font color="<%= textColor2 %>"><b><% if (hoursDiff2 >= 0) { %>+<% } %><%= hoursDiff2 %></b> hours from you</font>
       </font>
      </td>
     </tr>
     <tr>
      <td valign="top">
       <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
        <b>Email:</b>
       </font>
      </td>
      <td>
       <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
        <%-- do not show email addresses to spiders --%>
        <%
        if (request.getAttribute("spider") == null && 
            (dsgPlayerData.getEmailVisible() || (name != null && name.equals("rainwolf")))) {
            out.println("<b><a href=\"mailto:" + dsgPlayerData.getEmail() + "\">" + dsgPlayerData.getEmail() + "</a></b>");
        }
        else {
            out.println("Email address not visible");
        }
        %>
       </font>
      </td>
     </tr>
     <tr>
      <td>
       <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
        <b>Location:</b>
       </font>
      </td>
      <td>
       <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
        <% String location = dsgPlayerData.getLocation() != null ? dsgPlayerData.getLocation() : ""; %>
        <%= location %>
       </font>
      </td>
     </tr>
     <tr>
      <td>
       <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
        <b>Sex:</b>
       </font>
      </td>
      <td>
       <% // should have made computer have sex=U, oh well
          if (dsgPlayerData.isHuman()) { %>
       <font face="Verdana, Arial, Helvetica, sans-serif" size="2" color="#ffffff">
         <% 
            if (dsgPlayerData.getSex() == DSGPlayerData.MALE) {
                out.println("Male");
            } else if (dsgPlayerData.getSex() == DSGPlayerData.FEMALE) {
                out.println("Female");
            }
         %>
       </font>
       <% } %>
      </td>
     </tr>
     <tr>
      <td>
       <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
        <b>Age:</b>
       </font>
      </td>
      <td>
       <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
        <% String age = dsgPlayerData.getAge() == 0 ? "" : Integer.toString(dsgPlayerData.getAge()); %>
        <%= age %>
       </font>
      </td>
     </tr>
     <tr>
      <td>
       <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
        <b>Home page:&nbsp;</b>
       </font>
      </td>
      <td>
       <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
        <% String homepage = dsgPlayerData.getHomepage() != null ? dsgPlayerData.getHomepage() : ""; 
           if (homepage != null) {
               homepage = "<b><a rel=\"nofollow\" href=\"" + homepage + "\">" + homepage + "</a></b>";
               out.println(homepage);
           }
        %>
       </font>
      </td>
     </tr>
     <tr>
      <td>
       <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
        <b>Note:&nbsp;</b>
       </font>
      </td>
      <td>
        <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
          <% String note = ((dsgPlayerData.getNote() != null)  && dsgPlayerData.hasPlayerDonated()) ? dsgPlayerData.getNote() : ""; %>
          <%= note %>
        </font>
      </td>
     </tr>
     <tr>
      <td>
       <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
        <b>Remaining vacation (<%=Calendar.getInstance().get(Calendar.YEAR)%>):&nbsp;</b>
       </font>
      </td>
      <td>
        <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
        <% int floatingHourDays = dsgPlayerStorer.loadFloatingVacationDays(dsgPlayerData.getPlayerID()); %>
        <%=floatingHourDays/24%> days and <%=floatingHourDays % 24%> hours
        </font>
      </td>
     </tr>
     
  <tr>
    <td colspan="5">
	<% if (!dsgPlayerData.getName().equals(name)) { %>
        <input type="button" value="Send Message"
         onclick="javascript:window.location='/gameServer/newMessage.jsp?to=<%= dsgPlayerData.getName() %>';">
        <input type="button" value="Invite to Turn-based Game"
         onclick="javascript:window.location='/gameServer/tb/new.jsp?invitee=<%= dsgPlayerData.getName() %>';">
    <% } %>
<% if ("rainwolf".equals(name)) { %>
        <input type="button" value="Refresh Player Cache"
         onclick="javascript:window.location='/gameServer/admin/refreshPlayer.jsp?name=<%= dsgPlayerData.getName() %>';">
        <input type="button" value="View TB Cache"
         onclick="javascript:window.location='/gameServer/admin/tb/player.jsp?pid=<%= dsgPlayerData.getPlayerID() %>';">
<%}%>
      </td>
    </tr>    

<% if ("rainwolf".equals(name)) { %>
<tr>
  <td colspan="2">
    <div style="font-family:Verdana, Arial, Helvetica, sans-serif;
     background:#fffbcc;
     border:1px solid #e6db55;
     padding:5px;
     font-weight:bold;
     width:90%;">
<% if (dsgPlayerData.hasPlayerDonated()) { %> 
<%=dsgPlayerData.getName()%> has unlimited <%=(dsgPlayerData.unlimitedMobileTBGames() && (!dsgPlayerData.unlimitedTBGames()))?"(mobile-only) ":""%>turn-based games<%=dsgPlayerData.databaseAccess()?", database access":""%><%=dsgPlayerData.showAds()?" but will see advertisements":", and will not see advertisements"%>.<br>
This subscription ends on <%=dsgPlayerData.hasPlayerDonated()?""+profileDateFormat.format(dsgPlayerData.getSubscriptionExpiration()):""%>.
<br>
<%}%>
    </div>
   </td>
</tr>
<%}%>
        
   </table>
  </td>
<% if (dsgPlayerData.hasAvatar() && dsgPlayerData.hasPlayerDonated()) { %>
  <td valign="top">
    <img src="<%= request.getContextPath() %>/gameServer/avatar?name=<%= dsgPlayerData.getName() %>">
  </td>
<% } %>
</tr>
</table>

<br>

<%@ include file="playerstatsbox.jsp" %>

<% } %>

<br>

 </td>
</tr>

</table>


<%@ include file="end.jsp" %>