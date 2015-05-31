<%
/**
 *	$RCSfile: helpwin.jsp,v $
 *	$Revision: 1.1 $
 *	$Date: 2002/08/16 06:52:22 $
 */
%>

<%@ page import="com.jivesoftware.forum.*,
                 com.jivesoftware.forum.util.*,
                 com.jivesoftware.util.ParamUtils"
%>

<%  // Help file to include:
    String filename = ParamUtils.getParameter(request,"f");
    String hash = ParamUtils.getParameter(request,"hash");
%>

    <html>
    <head><title>Jive Forums Admin Help</title>
    <script language="JavaScript" type="text/javascript">
    window.focus();
    </script>
    </head>
    <frameset  rows="50,*">
        <frame name="top" src="help_top.jsp" marginwidth="2" marginheight="2" scrolling="no" frameborder="0">
        <%  if (filename != null) { %>
        <frame name="help_main" src="help_load.jsp?f=<%= filename %>#<%= hash %>" marginwidth="2" marginheight="2" scrolling="auto" frameborder="0">
        <%  } else { %>
        <frame name="help_main" src="help_load.jsp" marginwidth="2" marginheight="2" scrolling="auto" frameborder="0">
        <%  } %>
    </frameset>
    </html>
