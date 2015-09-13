<% pageContext.setAttribute("title", "Subscriptions"); %>
<%@ include file="begin.jsp" %>

<%
String nm = (String) request.getAttribute("name");
DSGPlayerData dsgPlayerData = dsgPlayerStorer.loadPlayer(nm);

DateFormat profileDateFormat = null;
TimeZone playerTimeZone = null;
if (dsgPlayerData != null) { 
	TimeZone tz = TimeZone.getTimeZone(dsgPlayerData.getTimezone());
	profileDateFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm z");
	profileDateFormat.setTimeZone(tz);
}
%>

<h3>
Thank you for subscribing to Pente.org!
</h3>
You now have unlimited turn-based games<%=dsgPlayerData.databaseAccess()?", database access":""%><%=dsgPlayerData.showAds()?" but you will get advertisments":", and you will not see advertisements"%>.<br>
Your subscription ends on <%=dsgPlayerData.hasPlayerDonated()?""+profileDateFormat.format(dsgPlayerData.getSubscriptionExpiration()):""%>.
<br>
<br>
<% int floatingHourDays = dsgPlayerStorer.loadFloatingVacationDays(dsgPlayerData.getPlayerID()); 
%>
You have <%=floatingHourDays/24%> days and <%=floatingHourDays % 24%> hours left for <%=Calendar.getInstance().get(Calendar.YEAR)%>.
<br>
<br>


<%@ include file="end.jsp" %>


