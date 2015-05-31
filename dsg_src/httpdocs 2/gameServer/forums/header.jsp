<%
/**
 * $RCSfile: header.jsp,v $
 * $Revision: 1.15.2.1 $
 * $Date: 2003/01/25 16:58:57 $
 */
%>
<%@ page import="com.jivesoftware.base.JiveGlobals" %>

<%  // Set the content type
    response.setContentType("text/html; charset=" + JiveGlobals.getCharacterEncoding());
%>

<%@ taglib uri="jivetags" prefix="jive" %>

<%@ include file="title.jsp" %>


<% String pg = request.getParameter("pageWidth");
   if (pg == null) { pg = "1024"; }
   String c = request.getParameter("current");
   if (c == null) { c = "Forums"; }
   pageContext.setAttribute("title", "Forums");
   pageContext.setAttribute("current", c);
   pageContext.setAttribute("pageWidth", pg); 
   pageContext.setAttribute("leftNav", false); 
%>
<%@ include file="..\begin.jsp" %>

<link rel="stylesheet" type="text/css" href="<%= request.getContextPath() %>/gameServer/forums/style.jsp" />

<% if (pg.equals("1024")) { %>
<style type="text/css">
#wrapper { width:1044px; }
#footer { width:1014px; padding-right:10px;margin-bottom:5px; }
#header { width:900px; }
#main { table-layout:fixed; }
</style>
<% } %>
