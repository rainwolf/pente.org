<%
   /**
    *	$RCSfile: exportGateway.jsp,v $
    *	$Revision: 1.1.4.1 $
    *	$Date: 2003/02/05 21:28:51 $
    */
%>

<%@ page import="java.util.*,
                 java.text.*,
                 com.jivesoftware.util.*,
                 com.jivesoftware.forum.*,
                 com.jivesoftware.forum.gateway.*,
                 com.jivesoftware.forum.util.*"
%>

<%@ include file="global.jsp" %>

<% // get parameters
   long forumID = ParamUtils.getLongParameter(request, "forum", -1L);
   boolean doRunOnce = ParamUtils.getBooleanParameter(request, "doRunOnce");
   String installType = ParamUtils.getParameter(request, "installType");
   int index = ParamUtils.getIntParameter(request, "index", -1);

   // Go back to the gateways page if "cancel" is clicked:
   String submitButton = ParamUtils.getParameter(request, "submitButton");
   if ("Cancel".equals(submitButton)) {
      response.sendRedirect("gateways.jsp?forum=" + forumID);
      return;
   }

   if (doRunOnce) {
      if (installType == null) {
         // no choice selected, redirect back to this page
         response.sendRedirect("exportGateway.jsp?forum=" + forumID);
         return;
      } else {
         // redirect to the specific edit page
         if ("email".equals(installType)) {
            response.sendRedirect("editEmailGateway.jsp?forum=" + forumID + "&add=true&exportOnce=true");
         } else if ("news".equals(installType)) {
            response.sendRedirect("editNewsgroupGateway.jsp?forum=" + forumID + "&add=true&exportOnce=true");
         } else {
            response.sendRedirect("exportGateway.jsp?forum=" + forumID);
         }
         return;
      }
   }

   // Get the Forum
   Forum forum = forumFactory.getForum(forumID);
   // Get a GatewayManager from the forum
   GatewayManager gatewayManager = forum.getGatewayManager();

   // Check to see if any of the gateways are installed
   boolean isEmailGatewayInstalled = false;
   boolean isNewsGatewayInstalled = false;

   int gatewayCount = gatewayManager.getGatewayCount();
   for (int i = 0; i < gatewayCount; i++) {
      try {
         if (gatewayManager.getGateway(i) instanceof EmailGateway) {
            isEmailGatewayInstalled = true;
         } else if (gatewayManager.getGateway(i) instanceof NewsgroupGateway) {
            isNewsGatewayInstalled = true;
         }
      } catch (Exception ignored) {
      }
   }
%>

<%@ include file="header.jsp" %>

<p>

      <%  // Title of this page and breadcrumbs
        String title = "Export Data";
        String[][] breadcrumbs = {
            {"Main", "main.jsp"},
            {"Forums", "forums.jsp"},
            {"Edit Forum", "editForum.jsp?forum="+forumID},
            {"Gateways", "gateways.jsp?forum="+forumID},
            {"Export Data", "exportGateway.jsp?forum="+forumID}
        };
%>
   <%@ include file="title.jsp" %>
      <%  // Number of installed gateways for this forum. Only show this section
    // if there are gateways to display
    gatewayCount = gatewayManager.getGatewayCount();
%>
   <font size="-1">
      Export all messages from this forum to <% if (gatewayCount > 0) { %>an existing gateway or <% } %> a new gateway.
   </font>
<p>
      <% if (gatewayCount > 0) { %>
<p>
   <font size="-1"><b>Installed Gateway</b></font>
<ul>
   <font size="-1">Export all available messages to an existing gateway.</font>
   <p>
   <table bgcolor="<%= tblBorderColor %>" cellpadding="0" cellspacing="0" border="0" width="">
      <tr>
         <td>
            <table bgcolor="<%= tblBorderColor %>" cellpadding="3" cellspacing="1" border="0" width="100%">
               <tr bgcolor="#eeeeee">
                  <td align="center" colspan="2"><font size="-2" face="verdana"><b>SOURCE</b></font></td>
                  <td align="center"><font size="-2" face="verdana"><b>EXPORT</b></font></td>
               </tr>
               <% // Loop through the list of installed gateways, show some info about each
                  for (int i = 0; i < gatewayCount; i++) {
                     Gateway gateway = gatewayManager.getGateway(i);
                     boolean isEmailGateway = (gateway instanceof EmailGateway);
                     boolean isNewsgroupGateway = (gateway instanceof NewsgroupGateway);
               %>
               <tr bgcolor="#ffffff">
                  <%
                     String displayName = "";
                     if (isEmailGateway) {
                        EmailGateway emailGateway = (EmailGateway) gateway;
                        Pop3Importer pop3Importer = (Pop3Importer) emailGateway.getGatewayImporter();
                        displayName = pop3Importer.getHost();
                  %>
                  <td><img src="images/button_email.gif" width="17" height="17" alt="" border="0"></td>
                  <td>
                     <font size="-1">
                        <b>Email<% if (displayName != null) { %>:<% } %></b>
                        <% if (displayName != null) { %><%= displayName%><% } %>
                     </font>
                  </td>
                  <% } else if (isNewsgroupGateway) {
                     NewsgroupGateway newsgroupGateway = (NewsgroupGateway) gateway;
                     NewsgroupImporter newsgroupImporter = (NewsgroupImporter) newsgroupGateway.getGatewayImporter();
                     displayName = newsgroupImporter.getNewsgroup();
                     if (displayName == null) {
                        displayName = newsgroupImporter.getHost();
                     }
                  %>
                  <td><img src="images/button_newsgroup.gif" width="17" height="17" alt="" border="0"></td>
                  <td>
                     <font size="-1">
                        <b>News<% if (displayName != null) { %>:<% } %></b>
                        <% if (displayName != null) { %><%= displayName%><% } %>
                     </font>
                  </td>
                  <% } %>
                  <td align="center">
                     <% if (isEmailGateway) { %>
                     <a href="editEmailGateway.jsp?edit=true&exportOnce=true&forum=<%= forumID %>&index=<%= i %>"
                     ><img src="images/button_edit.gif" width="17" height="17" alt="Export using this gateway"
                           border="0"
                     ></a>
                     <% } else if (isNewsgroupGateway) { %>
                     <a href="editNewsgroupGateway.jsp?edit=true&exportOnce=true&forum=<%= forumID %>&index=<%= i %>"
                     ><img src="images/button_edit.gif" width="17" height="17" alt="Export using this gateway"
                           border="0"
                     ></a>
                     <% } %>
                  </td>
               </tr>
               <% } %>
            </table>
         </td>
      </tr>
   </table>
</ul>
<% } // end if gatewayCount > 0 %>

<p>
   <font size="-1"><b>Export Once</b></font>
<ul>
   <font size="-1">Export all available messages from this forum to a new gateway.</font>
   <p>
   <form action="exportGateway.jsp">
      <input type="hidden" name="forum" value="<%= forumID %>">
      <input type="hidden" name="doRunOnce" value="true">
      <table cellpadding="3" cellspacing="0" border="0">
         <tr>
            <td valign="top"><input type="radio" name="installType" value="email" id="rb04"></td>
            <td valign="top"><img src="images/button_addemail.gif" width="17" height="17" border="0"></td>
            <td><font size="-1"><label for="rb04">Email Gateway -- export all messages to an email account or mailing
               list.</label></font></td>
         </tr>
         <tr>
            <td valign="top"><input type="radio" name="installType" value="news" id="rb06"></td>
            <td valign="top"><img src="images/button_addnewsgroup.gif" width="17" height="17" border="0"></td>
            <td><font size="-1"><label for="rb06">Newsgroup Gateway -- export all messages to a NNTP newsgroup.</label></font>
            </td>
         </tr>
         <tr>
            <td>&nbsp;</td>
            <td colspan="2"><input type="submit" name="submitButton" value="Export"> <input type="submit"
                                                                                            name="submitButton"
                                                                                            value="Cancel"></td>
         </tr>
      </table>
   </form>
</ul>
<p>

   <%@ include file="footer.jsp" %>
