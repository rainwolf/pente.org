<%
   /**
    *	$RCSfile: help_load.jsp,v $
    *	$Revision: 1.1 $
    *	$Date: 2002/08/16 06:52:22 $
    */
%>

<%@ page import="com.jivesoftware.forum.*,
                 com.jivesoftware.forum.util.*,
                 com.jivesoftware.util.ParamUtils"
%>

<% // Help file to include:
   String filename = ParamUtils.getParameter(request, "f");
   if (filename == null) {
      filename = "help_index.jsp";
   } else {
      filename = "help_" + filename + ".html";
   }
   String onload = "";

%>

<%@ include file="header.jsp" %>

<jsp:include page="<%= filename %>" flush="true"/>

<br><br><br><br><br><br><br><br>
<br><br><br><br><br><br><br><br>
<br><br><br><br><br><br><br><br>
<br><br><br><br><br><br><br><br>

<%@ include file="footer.jsp" %>
