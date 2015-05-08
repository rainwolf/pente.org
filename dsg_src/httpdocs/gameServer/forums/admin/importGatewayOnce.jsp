<%
    /**
     *	$RCSfile: importGatewayOnce.jsp,v $
     *	$Revision: 1.1.4.1 $
     *	$Date: 2003/02/05 21:28:51 $
     */
%>

<%@ page import="java.util.*,
                     java.text.*,
                     com.jivesoftware.forum.*,
                     com.jivesoftware.forum.gateway.*,
                     com.jivesoftware.forum.util.*,
                 com.jivesoftware.forum.Forum,
                 com.jivesoftware.util.ParamUtils,
                 com.jivesoftware.util.TaskEngine"
	errorPage="error.jsp"
%>

<%@	include file="global.jsp" %>

<%	// get parameters
        long forumID = ParamUtils.getLongParameter(request,"forum",-1L);
        long startTime = ParamUtils.getLongParameter(request,"startTime",-1L);
        String importAfter = ParamUtils.getParameter(request, "importAfter", false);
        boolean stopImport = ParamUtils.getBooleanParameter(request, "stopImport", false);
        Gateway gateway = (Gateway) session.getAttribute("gateway");

        Forum forum = forumFactory.getForum(forumID);
        GatewayImportTask importTask = null;

        if (session.getAttribute("gatewayImportTask") != null) {
            importTask = (GatewayImportTask) session.getAttribute("gatewayImportTask");

            if (stopImport) {
                importTask.stop();
                gateway = null;
            }
        }
        else {
            Date date = new Date(1);
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
                if (importAfter != null) {
                    date = sdf.parse(importAfter);
                }
            } catch (ParseException e) { }
            importTask = new GatewayImportTask(gateway, date);
            TaskEngine.addTask(importTask);

            session.setAttribute("gatewayImportTask", importTask);
            startTime = System.currentTimeMillis();
            response.sendRedirect("importGatewayOnce.jsp?forum="+forumID+"&startTime="+startTime);
            return;
        }
%>

<%@ include file="header.jsp" %>

<%  // Title of this page and breadcrumbs
        String title = "Gateway Import";
        String[][] breadcrumbs = {
        {"Main", "main.jsp"},
        {"Forums", "forums.jsp"},
        {"Gateways", "gateways.jsp?forum="+forumID},
        {title, "importGatewayOnce.jsp?forum="+forumID}
        };
%>
<%@ include file="title.jsp" %>

<font size="-1">
<% if (gateway instanceof EmailGateway) { %>
This gateway imports all messages from an email account into this forum.
<% } else if (gateway instanceof NewsgroupGateway)  { %>
This gateway imports all messages from a newsgroup into this forum.
<% } %>
</font>

<%  if (gateway == null || (importTask.hasRun() && !importTask.isBusy())) {
       session.removeAttribute("gateway");
       session.removeAttribute("gatewayImportTask");
       response.sendRedirect("gateways.jsp?forum="+forumID);
    }
    else {
    // is running task
%>

<script language="JavaScript" type="text/javascript">
<!--
function reloadPage() {
    self.location="importGatewayOnce.jsp?forum=<%=forumID%>&startTime=<%=startTime%>";
}
setTimeout(reloadPage,5000);
//-->
</script>

<p>
<font size="-1"><b>Importing...</b></font><p>
<ul>
    <font size="-1">
<%  GatewayImporter importer = gateway.getGatewayImporter();

     if (importer instanceof Pop3Importer) { %>
    Jive is currently importing messages into forum <i><%=forum.getName()%></i> from account <%= ((Pop3Importer) importer).getUsername() %>@<%= ((Pop3Importer) importer).getHost() %>  <br>
    <% } else if (importer instanceof NewsgroupImporter) { %>
    Jive is currently importing messages into forum <i><%=forum.getName()%></i> from newsgroup <%= ((NewsgroupImporter) importer).getNewsgroup() %>  <br>
    <% } %>
    <% if (startTime != -1) {%>
        ( <%= (int) ((System.currentTimeMillis() - startTime)/1000) %> seconds and counting)
    <% } %>
    </font>
</ul>
<p>
    <form action="importGatewayOnce.jsp">
    <input type="hidden" name="stopImport" value="true">
    <input type="hidden" name="forum" value="<%=forumID%>">
    <input type="hidden" name="startTime" value="<%=startTime%>">
    <input type="submit" name="submit" value="Stop Import">
    </form>
</p>
<%  } %>

<%@ include file="footer.jsp" %>
