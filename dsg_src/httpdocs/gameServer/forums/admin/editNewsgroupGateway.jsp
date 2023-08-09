<%
   /**
    *	$RCSfile: editNewsgroupGateway.jsp,v $
    *	$Revision: 1.4.4.1 $
    *	$Date: 2003/01/17 05:17:52 $
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
   boolean add = ParamUtils.getBooleanParameter(request, "add");
   boolean importOnce = ParamUtils.getBooleanParameter(request, "importOnce");
   boolean exportOnce = ParamUtils.getBooleanParameter(request, "exportOnce");
   boolean advanced = ParamUtils.getBooleanParameter(request, "advanced");
   boolean save = ParamUtils.getBooleanParameter(request, "save");
   boolean createNew = ParamUtils.getBooleanParameter(request, "createNew");
   boolean reload = ParamUtils.getBooleanParameter(request, "reload");
   boolean edit = ParamUtils.getBooleanParameter(request, "edit");
   int index = ParamUtils.getIntParameter(request, "index", -1);

   // form values
   String host = ParamUtils.getParameter(request, "host", false);
   String newsgroup = ParamUtils.getParameter(request, "newsgroup", false);
   String fromAddress = ParamUtils.getParameter(request, "fromAddress", false);
   int port = ParamUtils.getIntParameter(request, "port", 119);
   String username = ParamUtils.getParameter(request, "username", false);
   String password = ParamUtils.getParameter(request, "password", false);
   String organization = ParamUtils.getParameter(request, "organization", false);
   boolean emailPref = ParamUtils.getBooleanParameter(request, "emailPref", true);
   String tempParentBody = ParamUtils.getParameter(request, "tempParentBody", false);
   boolean debug = ParamUtils.getBooleanParameter(request, "debug");
   boolean attachments = ParamUtils.getBooleanParameter(request, "attachments", false);
   boolean subjectCheckEnabled = ParamUtils.getBooleanParameter(request, "subjectCheckEnabled", true);
   String exportAfter = ParamUtils.getParameter(request, "exportAfter", false);
   String importAfter = ParamUtils.getParameter(request, "importAfter", false);
   boolean updateMessageID = ParamUtils.getBooleanParameter(request, "updateMessageID", true);
   boolean allowExportAgain = ParamUtils.getBooleanParameter(request, "allowExportAgain", false);

   // Check for errors
   boolean errors = false;

   // Go back to the gateways page if "cancel" is clicked:
   String submitButton = ParamUtils.getParameter(request, "submitButton");
   if ("Cancel".equals(submitButton)) {
      if (importOnce) {
         response.sendRedirect("importGateway.jsp?forum=" + forumID);
      } else if (exportOnce) {
         response.sendRedirect("exportGateway.jsp?forum=" + forumID);
      } else {
         response.sendRedirect("gateways.jsp?forum=" + forumID);
      }
      return;
   }

   // Get the Forum
   Forum forum = forumFactory.getForum(forumID);
   // Get a GatewayManager from the forum
   GatewayManager gatewayManager = forum.getGatewayManager();

   // verify required fields
   if (save) {
      if (host == null || newsgroup == null) {
         errors = true;
         setOneTimeMessage(session, "newsgroupError",
            "Not all required newsgroup settings have been provided. <br>" +
               "Host and Newsgroup fields are required fields.");
      }

      // export settings
      if (!importOnce && (gatewayManager.isExportEnabled() || exportOnce)) {
         if (fromAddress == null) {
            errors = true;
            setOneTimeMessage(session, "exportError",
               "Default \"From\" address is a required field for exporting forum content.");
         }
      }
   }

   // Save properties of the gateway (or create a new gateway, and set its
   // properties). If importOnce, don't save the gateway using the gatewayManager
   // but redirect to the import jsp page. If exportOnce, don't save the gateway
   // using the gatewayManager but redirect to the export jsp page.
   if (!errors && save) {
      Gateway gateway = null;
      // create a new gateway
      if (importOnce || exportOnce) {
         gateway = new NewsgroupGateway(forumFactory, forum);
      } else if (createNew) {
         gateway = new NewsgroupGateway(forumFactory, forum);
         gatewayManager.addGateway(gateway);
      } else {
         // else, load the gateway
         gateway = (NewsgroupGateway) gatewayManager.getGateway(index);
      }

      NewsgroupImporter newsgroupImporter = (NewsgroupImporter) gateway.getGatewayImporter();
      NewsgroupExporter newsgroupExporter = (NewsgroupExporter) gateway.getGatewayExporter();

      if (host != null) {
         newsgroupImporter.setHost(host);
         newsgroupExporter.setHost(host);
      }
      if (newsgroup != null) {
         newsgroupImporter.setNewsgroup(newsgroup);
         newsgroupExporter.setNewsgroup(newsgroup);
      }
      if (fromAddress != null) {
         newsgroupExporter.setDefaultFromAddress(fromAddress);
      }

      newsgroupExporter.setOrganization(organization);
      newsgroupImporter.setTemporaryParentBody(tempParentBody);
      newsgroupImporter.setUsername(username);
      newsgroupExporter.setUsername(username);
      newsgroupImporter.setPassword(password);
      newsgroupExporter.setPassword(password);
      newsgroupImporter.setPort(port);
      newsgroupExporter.setPort(port);
      newsgroupImporter.setDebugEnabled(debug);
      newsgroupExporter.setDebugEnabled(debug);
      newsgroupImporter.setAttachmentsEnabled(attachments);
      newsgroupExporter.setAttachmentsEnabled(attachments);
      newsgroupExporter.setEmailPrefEnabled(emailPref);
      newsgroupExporter.setAllowExportAgain(allowExportAgain);
      newsgroupExporter.setUpdateMessageIDOnExport(updateMessageID);
      newsgroupImporter.setSubjectParentageCheckEnabled(subjectCheckEnabled);

      if (!importOnce && !exportOnce) {
         if (createNew) {
            // save the gateway
            gatewayManager.saveGateways();
         } else {
            gatewayManager.removeGateway(index);
            gatewayManager.addGateway(gateway, index);
         }

         // go back to the gateways page
         response.sendRedirect("gateways.jsp?forum=" + forumID);
      } else if (importOnce) {
         session.setAttribute("gateway", gateway);
         response.sendRedirect("importGatewayOnce.jsp?forum=" + forumID + "&importAfter=" + importAfter);
      } else if (exportOnce) {
         session.setAttribute("gateway", gateway);
         response.sendRedirect("exportGatewayOnce.jsp?forum=" + forumID + "&exportAfter=" + exportAfter);
      }
      return;
   }

   // if edit, then get the existing properties of the gateway from the
   // installed gateway
   if (edit && !reload) {
      NewsgroupGateway gateway = (NewsgroupGateway) gatewayManager.getGateway(index);
      NewsgroupImporter newsgroupImporter = (NewsgroupImporter) gateway.getGatewayImporter();
      NewsgroupExporter newsgroupExporter = (NewsgroupExporter) gateway.getGatewayExporter();

      host = newsgroupImporter.getHost();
      newsgroup = newsgroupImporter.getNewsgroup();
      port = newsgroupImporter.getPort();
      username = newsgroupImporter.getUsername();
      password = newsgroupImporter.getPassword();
      tempParentBody = newsgroupImporter.getTemporaryParentBody();
      debug = newsgroupImporter.isDebugEnabled();
      attachments = newsgroupImporter.isAttachmentsEnabled();
      subjectCheckEnabled = newsgroupImporter.isSubjectParentageCheckEnabled();
      fromAddress = newsgroupExporter.getDefaultFromAddress();
      organization = newsgroupExporter.getOrganization();
      emailPref = newsgroupExporter.isEmailPrefEnabled();
      allowExportAgain = newsgroupExporter.isAllowExportAgain();
      updateMessageID = newsgroupExporter.isUpdateMessageIDOnExport();

      if (username != null && username.equals("null")) {
         username = null;
      }
      if (password != null && password.equals("null")) {
         password = null;
      }
   }
%>

<%@ include file="header.jsp" %>

<p>

      <%  // Title of this page and breadcrumbs
    String title = null;
    if (importOnce) {
        title = "Import a Newsgroup Gateway";
    } else if (exportOnce) {
        title = "Export a Newsgroup Gateway";
    } else if (add) {
        title = "Add a Newsgroup Gateway";
    } else {
        title = "Edit Newsgroup Gateway Settings";
    }
    String[][] breadcrumbs = {
        {"Main", "main.jsp"},
        {"Forums", "forums.jsp"},
        {"Gateways", "gateways.jsp?forum="+forumID},
        {title, "editNewsgroupGateway.jsp?forum="+forumID+"&add="+add+"&edit="+edit+"&index="+index+"&exportOnce="+
                exportOnce+"&importOnce="+importOnce}
    };
%>
   <%@ include file="title.jsp" %>

   <font size="-1">
      <% if (importOnce) { %>
      Import a newsgroup gateway using the forms below.
      <% } else if (exportOnce) { %>
      Export this forum to a newsgroup gateway using the forms below.
      <% } else if (add) { %>
      Add a newsgroup gateway using the forms below.
      <% } else { %>
      Edit the newsgroup gateway settings using the forms below.
      <% } %>
   </font>

<p>

      <%  String message = getOneTimeMessage(session, "newsgroupError");
    if (message != null) {
%>
   <font size="-1" color="#bb0000"><b><%= message %>
   </b></font>

<p>
      <%  } %>

<form action="editNewsgroupGateway.jsp" name="postForm">
   <input type="hidden" name="forum" value="<%= forumID %>">
   <input type="hidden" name="save" value="true">
   <input type="hidden" name="add" value="<%= add %>">
   <input type="hidden" name="importOnce" value="<%= importOnce %>">
   <input type="hidden" name="exportOnce" value="<%= exportOnce %>">
   <input type="hidden" name="edit" value="<%= edit %>">
   <input type="hidden" name="index" value="<%= index %>">
   <input type="hidden" name="advanced" value="<%= advanced %>">
   <input type="hidden" name="reload" value="<%= reload %>">
   <% if (!advanced) { %>
   <input type="hidden" name="port" value="<%= port %>">
   <input type="hidden" name="username" value="<% if (username != null) { %><%= username %><% } %>">
   <input type="hidden" name="password" value="<% if (password != null) { %><%= password %><% } %>">
   <input type="hidden" name="organization" value="<% if (organization != null) { %><%= organization%><% } %>">
   <input type="hidden" name="emailPref" value="<%= emailPref %>">
   <input type="hidden" name="tempParentBody" value="<% if (tempParentBody != null) { %><%= tempParentBody %><% } %>">
   <input type="hidden" name="debug" value="<%= debug %>">
   <input type="hidden" name="exportAfter" value="<% if (exportAfter != null) { %><%= exportAfter %><% } %>">
   <input type="hidden" name="importAfter" value="<% if (importAfter != null) { %><%= importAfter %><% } %>">
   <input type="hidden" name="updateMessageID" value="<%= updateMessageID %>">
   <input type="hidden" name="allowExportAgain" value="<%= allowExportAgain %>">
   <input type="hidden" name="attachments" value="<%= attachments %>">
   <input type="hidden" name="subjectCheckEnabled" value="<%= subjectCheckEnabled %>">
   <% } %>
   <% if (add) { %>
   <input type="hidden" name="createNew" value="true">
   <% } %>

   <font size="-1"><b>Newsgroup Settings</b></font>
   <ul>
      <table cellpadding="3" cellspacing="0" border="0">
         <tr>
            <td><font size="-1">Host:</font></td>
            <td><input type="text" name="host" value="<% if (host != null) { %><%= host %><% } %>" size="30"
                       maxlength="100"></td>
            <td><a href="#" onclick="helpwin('newsGateway','host');return false;" title="Click for help"><img
               src="images/help-16x16.gif" width="16" height="16" border="0" hspace="8"></a></td>
         </tr>
         <tr>
            <td><font size="-1">Newsgroup:</font></td>
            <td><input type="text" name="newsgroup" value="<% if (newsgroup != null) { %><%= newsgroup %><% } %>"
                       size="30" maxlength="100"></td>
            <td><a href="#" onclick="helpwin('newsGateway','newsgroup');return false;" title="Click for help"><img
               src="images/help-16x16.gif" width="16" height="16" border="0" hspace="8"></a></td>
         </tr>
      </table>
   </ul>
   <% if (!importOnce) { %>

   <p>
         <%  message = getOneTimeMessage(session, "exportError");
    if (message != null) {
%>
      <font size="-1" color="#bb0000"><b><%= message %>
      </b></font>

   <p>
         <%  } %>

      <font size="-1"><b>Outgoing Newsgroup Settings</b></font>
   <ul>
      <table cellpadding="3" cellspacing="0" border="0">
         <tr>
            <td><font size="-1">Default "From" address:</font></td>
            <td><input type="text" name="fromAddress" value="<% if (fromAddress != null) { %><%= fromAddress%><% } %>"
                       size="30" maxlength="100"></td>
            <td><a href="#" onclick="helpwin('newsGateway','defaultfrom');return false;" title="Click for help"><img
               src="images/help-16x16.gif" width="16" height="16" border="0" hspace="8"></a></td>
         </tr>
      </table>
   </ul>
   <% } %>

   <script language="JavaScript" type="text/javascript">
      <!--
      function reloadForm(setAdvanced) {
         document.forms.postForm.save.value = "false";
         document.forms.postForm.reload.value = "true";
         if (setAdvanced) {
            document.forms.postForm.advanced.value = "<%= !advanced %>";
         }
         document.forms.postForm.submit();
         return false;
      }

      // -->
   </script>
   <font size="-1">
      <% if (!advanced) { %>
      <b>Advanced Settings</b>
      &nbsp;
      <a href="javascript:reloadForm(true);">Show</a>
      <img src="images/button_show_advanced.gif" width="10" height="9" alt="" border="0">
   </font>
   <% }
      if (advanced) { %>
   <p>
      <font size="-1">
         <b>Advanced Newsgroup Settings</b>
         <a href="javascript:reloadForm(true);">Hide</a>
         <img src="images/button_hide_advanced.gif" width="10" height="9" alt="" border="0">
      </font>
   <ul>
      <table cellpadding="3" cellspacing="0" border="0">
         <tr>
            <td><font size="-1">NNTP server port: </font></td>
            <td><input type="text" name="port" value="<%= port %>" size="3" maxlength="5"></td>
            <td><a href="#" onclick="helpwin('newsGateway','port');return false;" title="Click for help"><img
               src="images/help-16x16.gif" width="16" height="16" border="0" hspace="8"></a></td>
         </tr>
         <tr>
            <td><font size="-1">NNTP server username: </font></td>
            <td><input type="text" name="username" value="<% if (username != null) { %><%= username %><% } %>" size="15"
                       maxlength="100"></td>
            <td><a href="#" onclick="helpwin('newsGateway','username');return false;" title="Click for help"><img
               src="images/help-16x16.gif" width="16" height="16" border="0" hspace="8"></a></td>
         </tr>
         <tr>
            <td><font size="-1">NNTP server password: </font></td>
            <td><input type="text" name="password" value="<% if (password != null) { %><%= password %><% } %>" size="15"
                       maxlength="100"></td>
            <td><a href="#" onclick="helpwin('newsGateway','password');return false;" title="Click for help"><img
               src="images/help-16x16.gif" width="16" height="16" border="0" hspace="8"></a></td>
         </tr>
         <tr>
            <td><font size="-1">Debug: </font></td>
            <td><font size="-1"><input type="radio" name="debug" value="true" <% if (debug) { %>checked<% } %>>Enabled
               <input type="radio" name="debug" value="false" <% if (!debug) { %>checked<% } %>>Disabled</font></td>
            <td><a href="#" onclick="helpwin('newsGateway','debug');return false;" title="Click for help"><img
               src="images/help-16x16.gif" width="16" height="16" border="0" hspace="8"></a></td>
         </tr>
         <tr>
            <td><font size="-1">Allow import/export<br>of attachments:</font></td>
            <td><font size="-1"><input type="radio" name="attachments" value="true"
                                       <% if (attachments) { %>checked<% } %>>Enabled <input type="radio"
                                                                                             name="attachments"
                                                                                             value="false"
                                                                                             <% if (!attachments) { %>checked<% } %>>Disabled</font>
            </td>
            <td><a href="#" onclick="helpwin('newsGateway','attachments');return false;" title="Click for help"><img
               src="images/help-16x16.gif" width="16" height="16" border="0" hspace="8"></a></td>
         </tr>
      </table>
   </ul>
   <% if (!exportOnce) { %>
   <font size="-1"><b>Advanced Incoming Newsgroup Settings</b></font>
   <ul>
      <table cellpadding="3" cellspacing="0" border="0">
         <tr>
            <td><font size="-1">Enabled threading via<br>subject line matching:<br></font></td>
            <td><font size="-1"><input type="radio" name="subjectCheckEnabled" value="true"
                                       <% if (subjectCheckEnabled) { %>checked<% } %>>Enabled <input type="radio"
                                                                                                     name="subjectCheckEnabled"
                                                                                                     value="false"
                                                                                                     <% if (!subjectCheckEnabled) { %>checked<% } %>>Disabled</font>
            </td>
            <td><a href="#" onclick="helpwin('newsGateway','subjectcheck');return false;" title="Click for help"><img
               src="images/help-16x16.gif" width="16" height="16" border="0" hspace="8"></a></td>
         </tr>
         <tr>
            <td><font size="-1">Temporary Parent Body: </font></td>
            <td><textarea name="tempParentBody" cols="30" rows="5"
                          wrap="virtual"><% if (tempParentBody != null) { %><%= tempParentBody %><% } %></textarea></td>
            <td><a href="#" onclick="helpwin('newsGateway','tempparent');return false;" title="Click for help"><img
               src="images/help-16x16.gif" width="16" height="16" border="0" hspace="8"></a></td>
         </tr>
         <% if (importOnce) { %>
         <tr>
            <td><font size="-1">Import messages after: </font></td>
            <td><font size="-1"><input type="text" name="importAfter"
                                       value="<% if (importAfter != null) { %><%= importAfter%><% } %>" size="30"
                                       maxlength="75"> (in format dd/mm/yyyy)</font></td>
            <td><a href="#" onclick="helpwin('newsGateway','importafter');return false;" title="Click for help"><img
               src="images/help-16x16.gif" width="16" height="16" border="0" hspace="8"></a></td>
         </tr>
         <% } %>
      </table>
   </ul>
   <% }
      if (!importOnce) { %>
   <font size="-1"><b>Advanced Outgoing Newsgroup Settings</b></font>
   <ul>
      <table cellpadding="3" cellspacing="0" border="0">
         <tr>
            <td><font size="-1">Organization: </font></td>
            <td><input type="text" name="organization"
                       value="<% if (organization != null) { %><%= organization%><% } %>" size="30" maxlength="75"></td>
            <td><a href="#" onclick="helpwin('newsGateway','organization');return false;" title="Click for help"><img
               src="images/help-16x16.gif" width="16" height="16" border="0" hspace="8"></a></td>
         </tr>
         <tr>
            <td><font size="-1">Honor user email <br>preference: </font></td>
            <td><font size="-1"><input type="radio" name="emailPref" value="true" <% if (emailPref) { %>checked<% } %>>Yes
               <input type="radio" name="emailPref" value="false" <% if (!emailPref) { %>checked<% } %>>No</font></td>
            <td><a href="#" onclick="helpwin('newsGateway','honouremail');return false;" title="Click for help"><img
               src="images/help-16x16.gif" width="16" height="16" border="0" hspace="8"></a></td>
         </tr>
         <% if (exportOnce) { %>
         <tr>
            <td><font size="-1">Export messages after: </font></td>
            <td><font size="-1"><input type="text" name="exportAfter"
                                       value="<% if (exportAfter != null) { %><%= exportAfter%><% } %>" size="30"
                                       maxlength="75"> (in format dd/mm/yyyy)</font></td>
            <td><a href="#" onclick="helpwin('newsGateway','exportafter');return false;" title="Click for help"><img
               src="images/help-16x16.gif" width="16" height="16" border="0" hspace="8"></a></td>
         </tr>
         <tr>
            <td><font size="-1">Allow messages to be <br>exported again: </font></td>
            <td><font size="-1"><input type="radio" name="allowExportAgain" value="true"
                                       <% if (allowExportAgain) { %>checked<% } %>>Yes <input type="radio"
                                                                                              name="allowExportAgain"
                                                                                              value="false"
                                                                                              <% if (!allowExportAgain) { %>checked<% } %>>No</font>
            </td>
            <td><a href="#" onclick="helpwin('newsGateway','allowexportagain');return false;"
                   title="Click for help"><img src="images/help-16x16.gif" width="16" height="16" border="0" hspace="8"></a>
            </td>
         </tr>
         <tr>
            <td><font size="-1">Update NNTP message ID stored in Jive: </font></td>
            <td><font size="-1"><input type="radio" name="updateMessageID" value="true"
                                       <% if (updateMessageID) { %>checked<% } %>>Yes <input type="radio"
                                                                                             name="updateMessageID"
                                                                                             value="false"
                                                                                             <% if (!updateMessageID) { %>checked<% } %>>No</font>
            </td>
            <td><a href="#" onclick="helpwin('newsGateway','updatemessageid');return false;" title="Click for help"><img
               src="images/help-16x16.gif" width="16" height="16" border="0" hspace="8"></a></td>
         </tr>
         <% } %>
      </table>
   </ul>
   <% }
   }
   %>

   <p>

   <center>
      <% if (importOnce) { %>
      <input type="submit" name="submitButton" value="Import Messages">
      <% } else if (exportOnce) { %>
      <input type="submit" name="submitButton" value="Export Messages">
      <% } else if (add) { %>
      <input type="submit" name="submitButton" value="Add Gateway">
      <% } else { %>
      <input type="submit" name="submitButton" value="Save Settings">
      <% } %>
      <input type="submit" name="submitButton" value="Cancel">
   </center>

</form>

<p>

   <%@ include file="footer.jsp" %>

