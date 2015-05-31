<%@ page import="org.pente.game.*, org.pente.turnBased.*" %>


<% pageContext.setAttribute("title", "error"); %>
<%@ include file="../begin.jsp" %>
 
<% String error = (String) request.getAttribute("error"); %>

Error: <%= error %>

<br>

<%@ include file="../end.jsp" %>
