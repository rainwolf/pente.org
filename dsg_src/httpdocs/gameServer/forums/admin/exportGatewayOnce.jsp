<%
   /**
    *	$RCSfile: exportGatewayOnce.jsp,v $
    *	$Revision: 1.1 $
    *	$Date: 2002/08/16 06:52:22 $
    */
%>

<%@ page import="java.util.*,
                 java.text.*,
                 com.jivesoftware.forum.*,
                 com.jivesoftware.forum.gateway.*,
                 com.jivesoftware.forum.util.*,
                 com.jivesoftware.forum.Forum,
                 java.io.StringWriter,
                 java.io.PrintWriter,
                 com.jivesoftware.util.*"
         errorPage="error.jsp"
%>

<%@   include file="global.jsp" %>

<% // get parameters
   long forumID = ParamUtils.getLongParameter(request, "forum", -1L);
   long startTime = ParamUtils.getLongParameter(request, "startTime", -1L);
   String exportAfter = ParamUtils.getParameter(request, "exportAfter", false);
   boolean stopExport = ParamUtils.getBooleanParameter(request, "stopExport", false);
   Gateway gateway = (Gateway) session.getAttribute("gateway");

   Forum forum = forumFactory.getForum(forumID);
   GatewayExportTask exportTask = null;

   if (session.getAttribute("gatewayExportTask") != null) {
      exportTask = (GatewayExportTask) session.getAttribute("gatewayExportTask");

      if (stopExport) {
         exportTask.stop();
         gateway = null;
      }
   } else {
      Date date = new Date(1);
      try {
         SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
         if (exportAfter != null) {
            date = sdf.parse(exportAfter);
         }
      } catch (ParseException e) {
      }

      exportTask = new GatewayExportTask(forum, gateway, date);
      TaskEngine.addTask(exportTask);

      session.setAttribute("gatewayExportTask", exportTask);
      startTime = System.currentTimeMillis();
      response.sendRedirect("exportGatewayOnce.jsp?forum=" + forumID + "&startTime=" + startTime);
      return;
   }
%>

<%@ include file="header.jsp" %>

<% // Title of this page and breadcrumbs
   String title = "Gateway Export";
   String[][] breadcrumbs = {
      {"Main", "main.jsp"},
      {"Forums", "forums.jsp"},
      {"Gateways", "gateways.jsp?forum=" + forumID},
      {title, "exportGatewayOnce.jsp?forum=" + forumID}
   };
%>
<%@ include file="title.jsp" %>

<font size="-1">
   This gateway exports messages from a Jive gateway.
</font>

<% if (gateway == null || (exportTask.hasRun() && !exportTask.isBusy())) {
   session.removeAttribute("gateway");
   session.removeAttribute("gatewayExportTask");
   response.sendRedirect("gateways.jsp?forum=" + forumID);
} else {
   // is running task
%>

<script language="JavaScript" type="text/javascript">
   <!--
   function reloadPage() {
      self.location = "exportGatewayOnce.jsp?forum=<%=forumID%>&startTime=<%=startTime%>";
   }

   setTimeout(reloadPage, 5000);
   //-->
</script>

<p>
   <font size="-1"><b>Exporting...</b></font>
<p>
<ul>
   <%
      int total = exportTask.getTotalMessageCount();
      int count = exportTask.getCurrentMessageCount();
      int percentage = (int) (((double) count /
         (double) total) * 100.0);
      percentage = (percentage < 0) ? 0 : percentage;
   %>
   <font size="-1">
      <b><%= percentage %>% complete (<%= count %> of approximately <%= total %> messages)</b><br>
      Jive is currently exporting messages from forum <i><%=forum.getName()%>
   </i><br>
      <% if (startTime != -1) {%>
      (<%= (int) ((System.currentTimeMillis() - startTime) / 1000) %> seconds and counting)
      <% } %>
   </font>
</ul>
<p>
<form action="exportGatewayOnce.jsp">
   <input type="hidden" name="stopExport" value="true">
   <input type="hidden" name="forum" value="<%=forumID%>">
   <input type="hidden" name="startTime" value="<%=startTime%>">
   <input type="submit" name="submit" value="Stop Import">
</form>
</p>
<% } %>

<%@ include file="footer.jsp" %>
