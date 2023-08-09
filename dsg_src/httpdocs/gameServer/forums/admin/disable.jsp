<%
   /**
    *	$RCSfile: disable.jsp,v $
    *	$Revision: 1.4 $
    *	$Date: 2003/01/08 22:45:26 $
    */
%>

<%@ page import="java.util.*,
                 com.jivesoftware.forum.*,
                 com.jivesoftware.forum.util.*" %>

<%@ include file="global.jsp" %>

<% // Permission check
   if (!isSystemAdmin) {
      throw new UnauthorizedException("You don't have admin privileges to perform this operation.");
   }
%>

<% // Title of this page and breadcrumbs
   String title = "User &amp; Group Administration";
   String[][] breadcrumbs = null;

   // Get params
   boolean doDisable = request.getParameter("doDisable") != null;
   boolean success = request.getParameter("success") != null;
   if (doDisable) {
      JiveGlobals.setJiveProperty("userGroupAdmin.disabled", "true");
      response.sendRedirect("disable.jsp?success=true");
      return;
   }

   boolean isDisabled = "true".equals(JiveGlobals.getJiveProperty("userGroupAdmin.disabled"));
%>

<%@ include file="header.jsp" %>

<%@ include file="title.jsp" %>

<% if (success) {
   session.setAttribute("jive.admin.sidebarTab", "info");
%>

<script language="JavaScript" type="text/javascript">
   parent.frames['header'].location.reload();
   parent.frames['sidebar'].location.href = 'sidebar.jsp?sidebar=info';
</script>

<% } %>

<p>
   If you are using a custom user and/or group implementation, you may wish to disable
   user and group administration from this tool. If you have other tools outside of Jive Forums
   to administer your users and groups it is recommended to disable administration.
</p>

<p>
   Disabling user and group administration simply removes the "Users &amp; Groups" tab
   at the top. To re-enable administration simply remove the "userGroupAdmin.disabled" property
   from the jive_config.xml file, located in your jiveHome directory.
</p>

<p>
   <b>This is not intended for most users - only consider doing this if you are using a custom
      user or group implementation.</b>
</p>

<% if (!success && !isDisabled) { %>

<form action="disable.jsp"
      onsubmit="return confirm('Are you sure you want to disable user and group administration?');">
   <input type="submit" name="doDisable" value="Disable User &amp; Group Admin">
</form>

<% } else { %>

<b>
   User and group administration has been disabled. To re-enable it, follow these steps:
</b>
<ul>
   <li>Stop your appserver
   <li>Edit the jive_config.xml file located in <%= JiveGlobals.getJiveHome() %>.
   <li>Remove the property "userGroupAdmin.disabled"
   <li>Save the file, restart your appserver and reload the admin tool.
</ul>

<% } %>

<br><br>

<%@ include file="footer.jsp" %>
