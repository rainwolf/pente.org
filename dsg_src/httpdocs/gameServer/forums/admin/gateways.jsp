<%
   /**
    *	$RCSfile: gateways.jsp,v $
    *	$Revision: 1.4 $
    *	$Date: 2002/12/02 21:38:50 $
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
   boolean doInstall = ParamUtils.getBooleanParameter(request, "doInstall");
   boolean doImport = ParamUtils.getBooleanParameter(request, "doImport");
   String installType = ParamUtils.getParameter(request, "installType");
   String footer = ParamUtils.getParameter(request, "footer", true);
   boolean newIsImportEnabled = ParamUtils.getBooleanParameter(request, "importEnabled");
   boolean newIsExportEnabled = ParamUtils.getBooleanParameter(request, "exportEnabled");
   boolean doSetGlobalSettings = ParamUtils.getBooleanParameter(request, "doSetGlobalSettings");
   boolean remove = ParamUtils.getBooleanParameter(request, "remove");
   int index = ParamUtils.getIntParameter(request, "index", -1);

   // redirect to the install page for the specific type of gateway
   if (doInstall) {
      if (installType == null) {
         // no choice selected, redirect back to this page
         response.sendRedirect("gateways.jsp?forum=" + forumID);
         return;
      } else {
         // redirect to the specific edit page
         if ("email".equals(installType)) {
            response.sendRedirect("editEmailGateway.jsp?forum=" + forumID + "&add=true");
         } else if ("news".equals(installType)) {
            response.sendRedirect("editNewsgroupGateway.jsp?forum=" + forumID + "&add=true");
         } else {
            response.sendRedirect("gateways.jsp?forum=" + forumID);
         }
         return;
      }
   }
   if (doImport) {
      if (installType == null) {
         // no choice selected, redirect back to this page
         response.sendRedirect("gateways.jsp?forum=" + forumID);
         return;
      } else {
         // redirect to the specific edit page
         if ("import".equals(installType)) {
            response.sendRedirect("importGateway.jsp?forum=" + forumID);
         } else if ("export".equals(installType)) {
            response.sendRedirect("exportGateway.jsp?forum=" + forumID);
         } else {
            response.sendRedirect("gateways.jsp?forum=" + forumID);
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

   // Current global settings
   boolean isImportEnabled = gatewayManager.isImportEnabled();
   boolean isExportEnabled = gatewayManager.isExportEnabled();
   int importInterval = gatewayManager.getImportInterval();

   boolean errors = false;

   // Save the global settings if requested
   if (doSetGlobalSettings) {
      // verify that all gateways are configured correctly for import/export
      if (gatewayCount > 0 && newIsImportEnabled || newIsExportEnabled) {
         for (int i = 0; i < gatewayCount; i++) {
            Gateway gateway = gatewayManager.getGateway(i);

            if (!errors && gateway instanceof EmailGateway) {
               Pop3Importer importer = (Pop3Importer) gateway.getGatewayImporter();
               SmtpExporter exporter = (SmtpExporter) gateway.getGatewayExporter();

               if (newIsImportEnabled) {
                  if (importer.getHost() == null || "".equals(importer.getHost()) ||
                     importer.getUsername() == null || "".equals(importer.getUsername()) ||
                     importer.getPassword() == null || "".equals(importer.getPassword())) {
                     errors = true;
                     setOneTimeMessage(session, "gatewayConfigError",
                        "Not all required properties have been set for one or more " +
                           "of the current gateways.<br>Please verify the configuration " +
                           "of all the current gateways and try again.");
                  }
               }
               if (!errors && newIsExportEnabled) {
                  if (exporter.getHost() == null ||
                     exporter.getDefaultFromAddress() == null ||
                     exporter.getToAddress() == null) {
                     errors = true;
                     setOneTimeMessage(session, "gatewayConfigError",
                        "Not all required properties have been set for one or more " +
                           "of the current gateways.<br>Please verify the configuration " +
                           "of all the current gateways and try again.");
                  }
               }
            } else if (!errors && gateway instanceof NewsgroupGateway) {
               NewsgroupImporter importer = (NewsgroupImporter) gateway.getGatewayImporter();
               NewsgroupExporter exporter = (NewsgroupExporter) gateway.getGatewayExporter();

               if (newIsImportEnabled) {
                  if (importer.getHost() == null || "".equals(importer.getHost()) ||
                     importer.getNewsgroup() == null || "".equals(importer.getNewsgroup())) {
                     errors = true;
                     setOneTimeMessage(session, "gatewayConfigError",
                        "Not all required properties have been set for one or more " +
                           "of the current gateways.<br>Please verify the configuration " +
                           "of all the current gateways and try again.");
                  }
               }
               if (!errors && newIsExportEnabled) {
                  if (exporter.getHost() == null || "".equals(exporter.getHost()) ||
                     exporter.getNewsgroup() == null || "".equals(exporter.getNewsgroup()) ||
                     exporter.getDefaultFromAddress() == null) {
                     errors = true;
                     setOneTimeMessage(session, "gatewayConfigError",
                        "Not all required properties have been set for one or more " +
                           "of the current gateways.<br>Please verify the configuration " +
                           "of all the current gateways and try again.");
                  }
               }
            }

         }
      }

      // Compare old values to new ones (parameter values). If they've changed,
      // set the new property values
      if (!errors) {
         if (isImportEnabled != newIsImportEnabled) {
            gatewayManager.setImportEnabled(newIsImportEnabled);
         }
         if (isExportEnabled != newIsExportEnabled) {
            gatewayManager.setExportEnabled(newIsExportEnabled);
         }
         int newImportInterval = ParamUtils.getIntParameter(request, "importInterval", importInterval);
         if (importInterval != newImportInterval && newImportInterval > 0) {
            gatewayManager.setImportInterval(newImportInterval);
         }

         if (footer != null) {
            if ("".equals(footer)) {
               gatewayManager.setExportFooter(null);
            } else {
               gatewayManager.setExportFooter(footer);
            }
         }
         // Set message
         setOneTimeMessage(session, "jive.admin.message", "Settings saved.");

         // done saving, so redirect back to this page
         response.sendRedirect("gateways.jsp?forum=" + forumID);
         return;
      }
   }

   // Remove a gateway if requested
   if (remove) {
      if (index > -1 && index < gatewayManager.getGatewayCount()) {
         gatewayManager.removeGateway(index);
         // redirect back to this page
         response.sendRedirect("gateways.jsp?forum=" + forumID);
         return;
      }
   }

   if (!errors) {
      // get the current value of the gateway footer
      footer = gatewayManager.getExportFooter();
   }
%>

<%@ include file="header.jsp" %>

<p>

      <%  // Title of this page and breadcrumbs
    String title = "Gateways";
    String[][] breadcrumbs = {
        {"Main", "main.jsp"},
        {"Forums", "forums.jsp"},
        {"Edit Forum", "editForum.jsp?forum="+forumID},
        {"Gateways", "gateways.jsp?forum="+forumID}
    };
%>
   <%@ include file="title.jsp" %>

   <font size="-1">
      Gateways allow you to synchronize your forum with an external data source. For
      example, use the newsgroup gateway to mirror the content of an NNTP newsgroup.
   </font>

<p>

      <%  // Check for a one time message
    String message = getOneTimeMessage(session, "jive.admin.message");
    if (message != null) {
%>
   <font size="-1" color="#006600"><i><%= message %>
   </i></font>
<p>
      <%  }
    message = getOneTimeMessage(session, "gatewayConfigError");
    if (message != null) {
%>
   <font size="-1" color="#bb0000"><b><%= message %>
   </b></font>
<p>
      <%  } %>

      <%  // Number of installed gateways for this forum. Only show this section
    // if there are gateways to display
    gatewayCount = gatewayManager.getGatewayCount();
    if (gatewayCount > 0) {
%>
<p>
   <font size="-1"><b>Installed Gateways</b></font>
<ul>
   <table bgcolor="<%= tblBorderColor %>" cellpadding="0" cellspacing="0" border="0" width="">
      <tr>
         <td>
            <table bgcolor="<%= tblBorderColor %>" cellpadding="3" cellspacing="1" border="0" width="100%">
               <tr bgcolor="#eeeeee">
                  <td align="center" colspan="2"><font size="-2" face="verdana"><b>SOURCE</b></font></td>
                  <td align="center"><font size="-2" face="verdana"><b>EDIT</b></font></td>
                  <td align="center"><font size="-2" face="verdana"><b>DELETE</b></font></td>
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
                        if (gateway instanceof EmailGateway) {
                           EmailGateway emailGateway = (EmailGateway) gateway;
                           Pop3Importer pop3Importer = (Pop3Importer) emailGateway.getGatewayImporter();
                           displayName = pop3Importer.getHost();
                        }
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
                     <a href="editEmailGateway.jsp?edit=true&forum=<%= forumID %>&index=<%= i %>"
                     ><img src="images/button_edit.gif" width="17" height="17" alt="Edit the properties of this gateway"
                           border="0"
                     ></a>
                     <% } else if (isNewsgroupGateway) { %>
                     <a href="editNewsgroupGateway.jsp?edit=true&forum=<%= forumID %>&index=<%= i %>"
                     ><img src="images/button_edit.gif" width="17" height="17" alt="Edit the properties of this gateway"
                           border="0"
                     ></a>
                     <% } %>
                  </td>
                  <td align="center">
                     <a href="gateways.jsp?remove=true&forum=<%= forumID %>&index=<%= i %>"
                     ><img src="images/button_delete.gif" width="17" height="17" alt="Delete this gateway" border="0"
                     ></a>
                  </td>
               </tr>
               <% } %>
            </table>
         </td>
      </tr>
   </table>
</ul>
<% } // end if gatewayCount > 0 %>

<% if (!isEmailGatewayInstalled || !isNewsGatewayInstalled) { %>
<font size="-1"><b>Add a Gateway</b></font>
<ul>
   <form action="gateways.jsp">
      <input type="hidden" name="forum" value="<%= forumID %>">
      <input type="hidden" name="doInstall" value="true">
      <table cellpadding="3" cellspacing="0" border="0">
         <% if (!isEmailGatewayInstalled) { %>
         <tr>
            <td valign="top"><input type="radio" name="installType" value="email" id="rb01"></td>
            <td valign="top"><img src="images/button_addemail.gif" width="17" height="17" border="0"></td>
            <td><font size="-1"><label for="rb01">Email Gateway -- Synchronizes the forum with an email account or
               mailing list.</label></font></td>
         </tr>
         <% } %>
         <% if (!isNewsGatewayInstalled) { %>
         <tr>
            <td valign="top"><input type="radio" name="installType" value="news" id="rb03"></td>
            <td valign="top"><img src="images/button_addnewsgroup.gif" width="17" height="17" border="0"></td>
            <td><font size="-1"><label for="rb03">Newsgroup Gateway -- Synchronizes the forum with a NNTP
               newsgroup.</label></font></td>
         </tr>
         <% } %>
         <tr>
            <td colspan="3"><input type="submit" value="Add Gateway"></td>
         </tr>
      </table>
   </form>
</ul>
<p>
      <%  } %>

   <font size="-1"><b>Run a Gateway Once</b></font>
<ul>
   <form action="gateways.jsp">
      <input type="hidden" name="forum" value="<%= forumID %>">
      <input type="hidden" name="doImport" value="true">
      <table cellpadding="3" cellspacing="0" border="0">
         <tr>
            <td valign="top"><input type="radio" name="installType" value="import" id="rbimport"></td>
            <td><font size="-1"><label for="rbimport">Import -- Import all messages from a gateway.</label></font></td>
         </tr>
         <tr>
            <td valign="top"><input type="radio" name="installType" value="export" id="rbexport"></td>
            <td><font size="-1"><label for="rbexport">Export -- Export all messages from this forum.</label></font></td>
         </tr>
         <tr>
            <td colspan="3"><input type="submit" value="Continue"></td>
         </tr>
      </table>
   </form>
</ul>
<p>

   <font size="-1"><b>Global Gateway Settings for this Forum</b></font>
<ul>
   <form action="gateways.jsp" method="post">
      <input type="hidden" name="forum" value="<%= forumID %>">
      <input type="hidden" name="doSetGlobalSettings" value="true">
      <table cellpadding="3" cellspacing="0" border="0">
         <tr>
            <td nowrap><font size="-1">Gateway importing enabled:</font></td>
            <td>
               <font size="-1">
                  <input type="radio" name="importEnabled" value="true" id="rb03"
                         <% if (isImportEnabled) { %>checked<% } %>> <label for="rb03">Yes</label>
                  <input type="radio" name="importEnabled" value="false" id="rb04"
                         <% if (!isImportEnabled) { %>checked<% } %>> <label for="rb04">No</label>
               </font>
            </td>
         </tr>
         <tr>
            <td nowrap><font size="-1">Gateway exporting enabled:</font></td>
            <td>
               <font size="-1">
                  <input type="radio" name="exportEnabled" value="true" id="rb05"
                         <% if (isExportEnabled) { %>checked<% } %>> <label for="rb05">Yes</label>
                  <input type="radio" name="exportEnabled" value="false" id="rb06"
                         <% if (!isExportEnabled) { %>checked<% } %>> <label for="rb06">No</label>
               </font>
            </td>
         </tr>
         <tr>
            <td nowrap><font size="-1">Time between imports (minutes):</font></td>
            <td><input type="text" name="importInterval" value="<%= importInterval %>" size="5" maxlength="10"></td>
         </tr>
         <tr>
            <td nowrap valign="top"><font size="-1">Outgoing Message Footer:</font>
               <p>
                  <font color="#006600">
                     <tt>
                        {threadID} {threadName}
                        <br>
                        {forumID} {forumName}
                        <br>
                        {messageID}
                     </tt>
                  </font>
            </td>
            <td valign="top"><textarea name="footer" cols="30" rows="5"
                                       wrap="virtual"><% if (footer != null) { %><%=footer%><% } %></textarea></td>
         </tr>
         <tr>
            <td colspan="2" align="center"><br><input type="submit" value="Save Settings"></td>
         </tr>
      </table>
   </form>
</ul>
<%@ include file="footer.jsp" %>
