<%
/**
 *	$RCSfile: dataStatus.jsp,v $
 *	$Revision: 1.2 $
 *	$Date: 2002/11/21 23:46:53 $
 */
%>

<%@ page import="java.io.*,
                 java.util.*,
				 java.text.*,
				 com.jivesoftware.util.*,
                 com.jivesoftware.forum.*,
				 com.jivesoftware.forum.database.*,
				 com.jivesoftware.forum.util.*"
    errorPage="error.jsp"
%>

<%@ include file="global.jsp" %>

<%	// Permission check
    if (!isSystemAdmin) {
        throw new UnauthorizedException("You don't have admin privileges to perform this operation.");
    }

    // Get a handle on a DbForumFactory object (we can do that because we're
    // the system admin:
    DbForumFactory dbForumFactory = DbForumFactory.getInstance();

    // Get a DbDataExport instance:
    DbDataExport exporter = DbDataExport.getInstance(dbForumFactory);

    // Get the "type" parameter - tells us which status to look up:
    String type = ParamUtils.getParameter(request,"type");

    // If nothing is running, redirect back to data.jsp:
    if ("export".equals(type)) {
        if (!exporter.isRunning()) {
            response.sendRedirect("data.jsp");
            return;
        }
    }
    else if ("import".equals(type)) {
        /*
        if (!importer.isRunning()) {
            response.sendRedirect("data.jsp");
            return;
        }
        */
    }
%>

<%@ include file="header.jsp" %>
<meta http-equiv="refresh" content="4; URL=dataStatus.jsp?type=<%= type %>">

<p>

<%  // Title of this page and breadcrumbs
    String title = "Data Import &amp; Export";
    String[][] breadcrumbs = {
        {"Main", "main.jsp"},
        {title, "data.jsp"}
    };
%>
<%@ include file="title.jsp" %>

<%  if ("export".equals(type)) { %>

    <%  if (exporter.isRunning()) { %>

    <font size="-1">
    Export currently running... Please be patient (this page will refresh in 4 seconds).
    Once an export is finished, you will go back to the main import/export page.
    </font>

    <%  } %>

<%  } %>

<%@ include file="footer.jsp" %>
