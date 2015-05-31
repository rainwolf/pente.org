<%@ page import="org.pente.gameServer.core.*" %>

<% pageContext.setAttribute("title", "Register"); %>
<%@ include file="begin.jsp" %>

<table width="100%" border="0" colspacing="0" colpadding="0">

<tr>
 <td><h3>Registration Confirmation</h3></td>
</tr>

<tr>
 <td>
  <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
    Welcome to Pente.org, <font color="<%= textColor2 %>"><%= request.getAttribute("name") %></font>!<br>
    <br>
    A confirmation email has been sent to you at <font color="<%= textColor2 %>">
    <%= request.getParameter("email") %></font>.
    To change your email address, or any other information you provided during
    registration, visit <b><a href="/gameServer/myprofile">My Profile</a></b>.<br>
    <br>
    Please read the <a href="/gameServer/help/helpWindow.jsp?file=gettingStarted">
    <b>Getting Started</b></a> documentation to learn how to use all of Pente.org, or
    <b><a href="/gameServer/play.jsp">Start Playing</a></b>.
  </font>
 </td>
</tr>
</table>


<%@ include file="end.jsp" %>