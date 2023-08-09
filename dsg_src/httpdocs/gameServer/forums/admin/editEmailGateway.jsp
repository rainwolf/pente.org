<%
   /**
    *	$RCSfile: editEmailGateway.jsp,v $
    *	$Revision: 1.5.4.2 $
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

<%! // Global variables, methods, etc.
   String POP3_GATEWAY = "pop3Gateway";
%>

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

   // form values - inbound
   String gatewayType = ParamUtils.getParameter(request, "gatewayType", true);
   String inboundHost = ParamUtils.getParameter(request, "inboundHost", false);
   int inboundPort = ParamUtils.getIntParameter(request, "inboundPort", 110);
   String inboundUsername = ParamUtils.getParameter(request, "inboundUsername", false);
   String inboundPassword = ParamUtils.getParameter(request, "inboundPassword", false);
   String inboundFolder = ParamUtils.getParameter(request, "inboundFolder", false);
   String tempParentBody = ParamUtils.getParameter(request, "tempParentBody", false);
   boolean deleteEnabled = ParamUtils.getBooleanParameter(request, "deleteEnabled", false);
   boolean inboundAttach = ParamUtils.getBooleanParameter(request, "inboundAttach", false);
   boolean subjectCheckEnabled = ParamUtils.getBooleanParameter(request, "subjectCheckEnabled", true);
   String importAfter = ParamUtils.getParameter(request, "importAfter", false);

   // form values - outbound
   String outboundHost = ParamUtils.getParameter(request, "outboundHost", false);
   int outboundPort = ParamUtils.getIntParameter(request, "outboundPort", 25);
   String outboundUsername = ParamUtils.getParameter(request, "outboundUsername", false);
   String outboundPassword = ParamUtils.getParameter(request, "outboundPassword", false);
   String fromAddress = ParamUtils.getParameter(request, "fromAddress", false);
   String replyToAddress = ParamUtils.getParameter(request, "replyToAddress", true);
   String toAddress = ParamUtils.getParameter(request, "toAddress", false);
   String organization = ParamUtils.getParameter(request, "organization", false);
   boolean emailPref = ParamUtils.getBooleanParameter(request, "emailPref", true);
   boolean fromPref = ParamUtils.getBooleanParameter(request, "fromPref", true);
   boolean outboundAttach = ParamUtils.getBooleanParameter(request, "outboundAttach", false);
   String exportAfter = ParamUtils.getParameter(request, "exportAfter", false);
   boolean updateMessageID = ParamUtils.getBooleanParameter(request, "updateMessageID", true);
   boolean allowExportAgain = ParamUtils.getBooleanParameter(request, "allowExportAgain", false);

   // form values - common
   boolean debug = ParamUtils.getBooleanParameter(request, "debug", false);

   // Check for errors
   boolean errors = false;

   // Go back to the gateways page if "cancel" is clicked:
   String submitButton = ParamUtils.getParameter(request, "submitButton");
   if ("Cancel".equals(submitButton)) {
      if (importOnce) {
         response.sendRedirect("importGateway.jsp?forum=" + forumID);
      }
      if (exportOnce) {
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
      // import settings
      if (!exportOnce && (gatewayManager.isImportEnabled() || importOnce)) {
         if (inboundHost == null || inboundUsername == null || inboundPassword == null) {
            errors = true;
            setOneTimeMessage(session, "importError",
               "Not all required incoming mail settings have been provided. <br>" +
                  "Host, Username and Password fields are required for importing mail.");
         }
      }
      // export settings
      if (!importOnce && (gatewayManager.isExportEnabled() || exportOnce)) {
         if (outboundHost == null || fromAddress == null || toAddress == null) {
            errors = true;
            setOneTimeMessage(session, "exportError",
               "Not all required outgoing mail settings have been provided. <br>" +
                  "Host, Default \"From\" address and \"To\" address fields are required " +
                  "for exporting forum content.");
         }
      }
   }

   // Save properties of the gateway (or create a new gateway, and set its
   // properties). If importOnce, don't save the gateway using the gatewayManager
   // but redirect to the import jsp page. If exportOnce, don't save the gateway
   // using the gatewayManager but redirect to the export jsp page.
   if (!errors && save) {
      Gateway gateway = null;

      // create a new gateway/get the existing gateway
      if (importOnce || exportOnce || createNew) {
         gateway = new EmailGateway(forumFactory, forum);

         // save gateway if we are creating a new gateway
         if (!importOnce && !exportOnce && createNew) {
            gatewayManager.addGateway(gateway);
         }
      } else {
         gateway = gatewayManager.getGateway(index);
         gateway = new EmailGateway(forumFactory, forum);
         gatewayManager.removeGateway(index);
         gatewayManager.addGateway(gateway, index);
      }

      // set inbound gateway properties
      Pop3Importer pop3Importer = (Pop3Importer) gateway.getGatewayImporter();
      pop3Importer.setHost(inboundHost);
      pop3Importer.setPort(inboundPort);
      pop3Importer.setUsername(inboundUsername);
      pop3Importer.setPassword(inboundPassword);
      pop3Importer.setTemporaryParentBody(tempParentBody);
      pop3Importer.setDeleteEnabled(deleteEnabled);
      pop3Importer.setAttachmentsEnabled(inboundAttach);
      pop3Importer.setSubjectParentageCheckEnabled(subjectCheckEnabled);
      pop3Importer.setDebugEnabled(debug);

      if (importOnce) {
         session.setAttribute("gateway", gateway);
         response.sendRedirect("importGatewayOnce.jsp?forum=" + forumID + "&importAfter=" + importAfter);
         return;
      }

      // Set smtp properties
      SmtpExporter smtpExporter = (SmtpExporter) gateway.getGatewayExporter();
      smtpExporter.setHost(outboundHost);
      smtpExporter.setPort(outboundPort);
      smtpExporter.setUsername(outboundUsername);
      smtpExporter.setPassword(outboundPassword);
      smtpExporter.setDefaultFromAddress(fromAddress);
      smtpExporter.setReplyToAddress(replyToAddress);
      smtpExporter.setToAddress(toAddress);
      smtpExporter.setOrganization(organization);
      smtpExporter.setDebugEnabled(debug);
      smtpExporter.setAttachmentsEnabled(outboundAttach);
      smtpExporter.setEmailPrefEnabled(emailPref);
      smtpExporter.setFromAddressOnly(fromPref);
      smtpExporter.setAllowExportAgain(allowExportAgain);
      smtpExporter.setUpdateMessageIDOnExport(updateMessageID);

      if (createNew) {
         // save the gateway
         gatewayManager.saveGateways();
      } else {
         gatewayManager.removeGateway(index);
         gatewayManager.addGateway(gateway, index);
      }

      if (exportOnce) {
         session.setAttribute("gateway", gateway);
         response.sendRedirect("exportGatewayOnce.jsp?forum=" + forumID + "&exportAfter=" + exportAfter);
         return;
      } else {
         // go back to the gateways page
         response.sendRedirect("gateways.jsp?forum=" + forumID);
         return;
      }
   }

   // if edit, then get the existing properties of the gateway from the installed gateway
   if (edit && !reload) {
      Gateway gateway = gatewayManager.getGateway(index);

      gatewayType = POP3_GATEWAY;

      // retrieve properties
      Pop3Importer pop3Importer = (Pop3Importer) gateway.getGatewayImporter();
      inboundHost = pop3Importer.getHost();
      inboundPort = pop3Importer.getPort();
      inboundUsername = pop3Importer.getUsername();
      inboundPassword = pop3Importer.getPassword();
      tempParentBody = pop3Importer.getTemporaryParentBody();
      deleteEnabled = pop3Importer.isDeleteEnabled();
      debug = pop3Importer.isDebugEnabled();
      inboundAttach = pop3Importer.isAttachmentsEnabled();
      subjectCheckEnabled = pop3Importer.isSubjectParentageCheckEnabled();

      if (inboundUsername != null && inboundUsername.equals("null")) {
         inboundUsername = null;
      }
      if (inboundPassword != null && inboundPassword.equals("null")) {
         inboundPassword = null;
      }

      SmtpExporter smtpExporter = (SmtpExporter) gateway.getGatewayExporter();
      outboundHost = smtpExporter.getHost();
      outboundPort = smtpExporter.getPort();
      outboundUsername = smtpExporter.getUsername();
      outboundPassword = smtpExporter.getPassword();
      fromAddress = smtpExporter.getDefaultFromAddress();
      replyToAddress = smtpExporter.getReplyToAddress();
      toAddress = smtpExporter.getToAddress();
      organization = smtpExporter.getOrganization();
      emailPref = smtpExporter.isEmailPrefEnabled();
      fromPref = smtpExporter.isFromAddressOnly();
      outboundAttach = smtpExporter.isAttachmentsEnabled();
      allowExportAgain = smtpExporter.isAllowExportAgain();
      updateMessageID = smtpExporter.isUpdateMessageIDOnExport();
   }
%>

<%@ include file="header.jsp" %>

<p>

      <%  // Title of this page and breadcrumbs
    String title = null;
    if (importOnce) {
        title = "Import an Email Gateway";
    } else if (exportOnce) {
        title = "Export an Email Gateway";
    } else if (add) {
        title = "Add an Email Gateway";
    } else {
        title = "Edit Email Gateway Settings";
    }
    String[][] breadcrumbs = {
        {"Main", "main.jsp"},
        {"Forums", "forums.jsp"},
        {"Gateways", "gateways.jsp?forum="+forumID},
        {title, "editEmailGateway.jsp?forum="+forumID+"&add="+add+"&edit="+edit+"&index="+index+"&exportOnce="+
            exportOnce+"&importOnce="+importOnce}
    };
%>
   <%@ include file="title.jsp" %>

   <font size="-1">
      <% if (importOnce) { %>
      Import an email gateway using the forms below.
      <% } else if (exportOnce) { %>
      Export to an email gateway using the forms below.
      <% } else if (add) { %>
      Add an email gateway using the forms below.
      <% } else { %>
      Edit the email gateway settings using the forms below.
      <% } %>
   </font>

<p>

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

      function popupHelp(page) {
         var win = window.open(page, 'newWindow', 'width=350,height=400,menubar=yes,location=no,personalbar=no,scrollbars=yes,resize=yes');
      }

      //-->
   </script>
   </script>


<form action="editEmailGateway.jsp" name="postForm">
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
   <input type="hidden" name="inboundPort" value="<%= inboundPort %>">
   <input type="hidden" name="inboundFolder" value="<% if (inboundFolder != null) { %><%= inboundFolder %><% } %>">
   <input type="hidden" name="tempParentBody" value="<% if (tempParentBody != null) { %><%= tempParentBody %><% } %>">
   <input type="hidden" name="deleteEnabled" value="<%= deleteEnabled %>">
   <input type="hidden" name="inboundAttach" value="<%= inboundAttach %>">
   <input type="hidden" name="subjectCheckEnabled" value="<%= subjectCheckEnabled %>">

   <input type="hidden" name="outboundPort" value="<%= outboundPort %>">
   <input type="hidden" name="outboundUsername"
          value="<% if (outboundUsername != null) { %><%= outboundUsername %><% } %>">
   <input type="hidden" name="outboundPassword"
          value="<% if (outboundPassword != null) { %><%= outboundPassword %><% } %>">
   <input type="hidden" name="organization" value="<% if (organization != null) { %><%= organization %><% } %>">
   <input type="hidden" name="debug" value="<%= debug %>">
   <input type="hidden" name="exportAfter" value="<% if (exportAfter != null) { %><%= exportAfter %><% } %>">
   <input type="hidden" name="importAfter" value="<% if (importAfter != null) { %><%= importAfter %><% } %>">
   <input type="hidden" name="updateMessageID" value="<%= updateMessageID %>">
   <input type="hidden" name="allowExportAgain" value="<%= allowExportAgain %>">
   <input type="hidden" name="outboundAttach" value="<%= outboundAttach %>">
   <input type="hidden" name="emailPref" value="<%= emailPref %>">
   <input type="hidden" name="fromPref" value="<%= fromPref %>">

   <% }
      if (add) { %>
   <input type="hidden" name="createNew" value="true">
   <% }
      if (!exportOnce) {
         String message = getOneTimeMessage(session, "importError");
         if (message != null) {
   %>
   <font size="-1" color="#bb0000"><b><%= message %>
   </b></font>

   <p>
         <%      } %>

      <font size="-1"><b>Incoming Mail Settings</b></font>
   <ul>
      <table cellpadding="3" cellspacing="0" border="0">
         <tr>
            <td><font size="-1">Host:</font></td>
            <td><input type="text" name="inboundHost" value="<% if (inboundHost != null) { %><%= inboundHost %><% } %>"
                       size="30" maxlength="100"></td>
            <td><a href="#" onclick="helpwin('emailGateway','inboundhost');return false;" title="Click for help"><img
               src="images/help-16x16.gif" width="16" height="16" border="0" hspace="8"></a></td>
         </tr>
         <tr>
            <td><font size="-1">Username: </font></td>
            <td><input type="text" name="inboundUsername"
                       value="<% if (inboundUsername != null) { %><%= inboundUsername %><% } %>" size="15"
                       maxlength="100"></td>
            <td><a href="#" onclick="helpwin('emailGateway','inboundusername');return false;"
                   title="Click for help"><img src="images/help-16x16.gif" width="16" height="16" border="0" hspace="8"></a>
            </td>
         </tr>
         <tr>
            <td><font size="-1">Password: </font></td>
            <td><input type="text" name="inboundPassword"
                       value="<% if (inboundPassword != null) { %><%= inboundPassword %><% } %>" size="15"
                       maxlength="100"></td>
            <td><a href="#" onclick="helpwin('emailGateway','inboundpassword');return false;"
                   title="Click for help"><img src="images/help-16x16.gif" width="16" height="16" border="0" hspace="8"></a>
            </td>
         </tr>
      </table>
   </ul>
   <% }
      if (!importOnce) {
         String message = getOneTimeMessage(session, "exportError");
         if (message != null) {
   %>
   <font size="-1" color="#bb0000"><b><%= message %>
   </b></font>

   <p>
         <%      } %>
      <font size="-1"><b>Outgoing Mail Settings</b></font>
   <ul>
      <table cellpadding="3" cellspacing="0" border="0">
         <tr>
            <td><font size="-1">Host:</font></td>
            <td><input type="text" name="outboundHost"
                       value="<% if (outboundHost != null) { %><%= outboundHost %><% } %>" size="30" maxlength="100">
            </td>
            <td><a href="#" onclick="helpwin('emailGateway','outboundhost');return false;" title="Click for help"><img
               src="images/help-16x16.gif" width="16" height="16" border="0" hspace="8"></a></td>
         </tr>
         <tr>
            <td><font size="-1">Default "From" address:</font></td>
            <td><input type="text" name="fromAddress" value="<% if (fromAddress != null) { %><%= fromAddress %><% } %>"
                       size="30" maxlength="100"></td>
            <td><a href="#" onclick="helpwin('emailGateway','defaultfrom');return false;" title="Click for help"><img
               src="images/help-16x16.gif" width="16" height="16" border="0" hspace="8"></a></td>
         </tr>
         <tr>
            <td><font size="-1">"Reply-To" address:</font></td>
            <td><input type="text" name="replyToAddress"
                       value="<% if (replyToAddress != null) { %><%= replyToAddress %><% } %>" size="30"
                       maxlength="100"></td>
            <td><a href="#" onclick="helpwin('emailGateway','replyto');return false;" title="Click for help"><img
               src="images/help-16x16.gif" width="16" height="16" border="0" hspace="8"></a></td>
         </tr>
         <tr>
            <td><font size="-1">"To" address:</font></td>
            <td><input type="text" name="toAddress" value="<% if (toAddress != null) { %><%= toAddress %><% } %>"
                       size="30" maxlength="100"></td>
            <td><a href="#" onclick="helpwin('emailGateway','toaddress');return false;" title="Click for help"><img
               src="images/help-16x16.gif" width="16" height="16" border="0" hspace="8"></a></td>
         </tr>
      </table>
   </ul>
   <% }
      if (!advanced) { %>
   <font size="-1">
      <b>Advanced Settings</b>
      &nbsp;
      <a href="javascript:reloadForm(true);">Show</a>
      <img src="images/button_show_advanced.gif" width="10" height="9" alt="" border="0">
   </font>
   <% }
      if (advanced) { %>
   <p>
         <% if (!exportOnce) { %>
      <font size="-1">
         <b>Advanced Incoming Mail Settings</b>
         <a href="javascript:reloadForm(true);">Hide</a>
         <img src="images/button_hide_advanced.gif" width="10" height="9" alt="" border="0">
      </font>

   <ul>
      <table cellpadding="3" cellspacing="0" border="0">
         <tr>
            <td><font size="-1">POP3 server port: </font></td>
            <td><input type="text" name="inboundPort" value="<%= inboundPort %>" size="3" maxlength="5"></td>
            <td><a href="#" onclick="helpwin('emailGateway','inboundport');return false;" title="Click for help"><img
               src="images/help-16x16.gif" width="16" height="16" border="0" hspace="8"></a></td>
         </tr>
         <tr>
            <td><font size="-1">Delete messages from<br>server:</font></td>
            <td><font size="-1"><input type="radio" name="deleteEnabled" value="true"
                                       <% if (deleteEnabled) { %>checked<% } %>>Enabled <input type="radio"
                                                                                               name="deleteEnabled"
                                                                                               value="false"
                                                                                               <% if (!deleteEnabled) { %>checked<% } %>>Disabled</font>
            </td>
            <td><a href="#" onclick="helpwin('emailGateway','delete');return false;" title="Click for help"><img
               src="images/help-16x16.gif" width="16" height="16" border="0" hspace="8"></a></td>
         </tr>
         <tr>
            <td><font size="-1">Allow import of<br>attachments:<br></font></td>
            <td><font size="-1"><input type="radio" name="inboundAttach" value="true"
                                       <% if (inboundAttach) { %>checked<% } %>>Enabled <input type="radio"
                                                                                               name="inboundAttach"
                                                                                               value="false"
                                                                                               <% if (!inboundAttach) { %>checked<% } %>>Disabled</font>
            </td>
            <td><a href="#" onclick="helpwin('emailGateway','attachments');return false;" title="Click for help"><img
               src="images/help-16x16.gif" width="16" height="16" border="0" hspace="8"></a></td>
         </tr>
         <tr>
            <td><font size="-1">Enabled threading via<br>subject line matching:<br></font></td>
            <td><font size="-1"><input type="radio" name="subjectCheckEnabled" value="true"
                                       <% if (subjectCheckEnabled) { %>checked<% } %>>Enabled <input type="radio"
                                                                                                     name="subjectCheckEnabled"
                                                                                                     value="false"
                                                                                                     <% if (!subjectCheckEnabled) { %>checked<% } %>>Disabled</font>
            </td>
            <td><a href="#" onclick="helpwin('emailGateway','subjectcheck');return false;" title="Click for help"><img
               src="images/help-16x16.gif" width="16" height="16" border="0" hspace="8"></a></td>
         </tr>
         <tr>
            <td><font size="-1">Temporary Parent Body: </font></td>
            <td><textarea name="tempParentBody" cols="30" rows="5"
                          wrap="virtual"><% if (tempParentBody != null) { %><%= tempParentBody %><% } %></textarea></td>
            <td><a href="#" onclick="helpwin('emailGateway','tempparent');return false;" title="Click for help"><img
               src="images/help-16x16.gif" width="16" height="16" border="0" hspace="8"></a></td>
         </tr>
         <% if (importOnce) { %>
         <tr>
            <td><font size="-1">Import messages after: </font></td>
            <td><font size="-1"><input type="text" name="importAfter"
                                       value="<% if (importAfter != null) { %><%= importAfter%><% } %>" size="30"
                                       maxlength="75"> (in format dd/mm/yyyy)</font></td>
            <td><a href="#" onclick="helpwin('emailGateway','importafter');return false;" title="Click for help"><img
               src="images/help-16x16.gif" width="16" height="16" border="0" hspace="8"></a></td>
         </tr>
         <% } %>
         <tr>
            <td><font size="-1">Debug:</font></td>
            <td><font size="-1"><input type="radio" name="debug" value="true" <% if (debug) { %>checked<% } %>>Enabled
               <input type="radio" name="debug" value="false" <% if (!debug) { %>checked<% } %>>Disabled</font></td>
            <td><a href="#" onclick="helpwin('emailGateway','debug');return false;" title="Click for help"><img
               src="images/help-16x16.gif" width="16" height="16" border="0" hspace="8"></a></td>
         </tr>

      </table>
   </ul>
   <% }
      if (!importOnce) { %>
   <font size="-1"><b>Advanced Outgoing Mail Settings</b></font>
   <% if (exportOnce) { %>
   <font size="-1"><a href="javascript:reloadForm(true);">Hide</a></font>
   <img src="images/button_hide_advanced.gif" width="10" height="9" alt="" border="0">
   <% } %>
   <ul>
      <table cellpadding="3" cellspacing="0" border="0">
         <tr>
            <td><font size="-1">SMTP server port: </font></td>
            <td><input type="text" name="outboundPort" value="<%= outboundPort %>" size="3" maxlength="5"></td>
            <td><a href="#" onclick="helpwin('emailGateway','outboundport');return false;" title="Click for help"><img
               src="images/help-16x16.gif" width="16" height="16" border="0" hspace="8"></a></td>
         </tr>
         <tr>
            <td><font size="-1">Username: </font></td>
            <td><input type="text" name="outboundUsername"
                       value="<% if (outboundUsername != null) { %><%= outboundUsername %><% } %>" size="15"
                       maxlength="100"></td>
            <td><a href="#" onclick="helpwin('emailGateway','outboundusername');return false;"
                   title="Click for help"><img src="images/help-16x16.gif" width="16" height="16" border="0" hspace="8"></a>
            </td>
         </tr>
         <tr>
            <td><font size="-1">Password: </font></td>
            <td><input type="text" name="outboundPassword"
                       value="<% if (outboundPassword != null) { %><%= outboundPassword %><% } %>" size="15"
                       maxlength="100"></td>
            <td><a href="#" onclick="helpwin('emailGateway','outboundpassword');return false;"
                   title="Click for help"><img src="images/help-16x16.gif" width="16" height="16" border="0" hspace="8"></a>
            </td>
         </tr>
         <tr>
            <td><font size="-1">Organization: </font></td>
            <td><input type="text" name="organization"
                       value="<% if (organization != null) { %><%= organization%><% } %>" size="30" maxlength="75"></td>
            <td><a href="#" onclick="helpwin('emailGateway','organization');return false;" title="Click for help"><img
               src="images/help-16x16.gif" width="16" height="16" border="0" hspace="8"></a></td>
         </tr>
         <tr>
            <td><font size="-1">Only use Default <br>"From" address: </font></td>
            <td><font size="-1"><input type="radio" name="fromPref" value="true" <% if (fromPref) { %>checked<% } %>>Yes
               <input type="radio" name="fromPref" value="false" <% if (!fromPref) { %>checked<% } %>>No</font></td>
            <td><a href="#" onclick="helpwin('emailGateway','onlyusedefaultfromaddress');return false;"
                   title="Click for help"><img src="images/help-16x16.gif" width="16" height="16" border="0" hspace="8"></a>
            </td>
         </tr>
         <tr>
            <td><font size="-1">Honor user email <br>preference: </font></td>
            <td><font size="-1"><input type="radio" name="emailPref" value="true" <% if (emailPref) { %>checked<% } %>>Yes
               <input type="radio" name="emailPref" value="false" <% if (!emailPref) { %>checked<% } %>>No</font></td>
            <td><a href="#" onclick="helpwin('emailGateway','honouremail');return false;" title="Click for help"><img
               src="images/help-16x16.gif" width="16" height="16" border="0" hspace="8"></a></td>
         </tr>
         <tr>
            <td><font size="-1">Allow export of<br>attachments:<br></font></td>
            <td><font size="-1"><input type="radio" name="outboundAttach" value="true"
                                       <% if (outboundAttach) { %>checked<% } %>>Enabled <input type="radio"
                                                                                                name="outboundAttach"
                                                                                                value="false"
                                                                                                <% if (!outboundAttach) { %>checked<% } %>>Disabled</font>
            </td>
            <td><a href="#" onclick="helpwin('emailGateway','attachments');return false;" title="Click for help"><img
               src="images/help-16x16.gif" width="16" height="16" border="0" hspace="8"></a></td>
         </tr>
         <% if (exportOnce) { %>
         <tr>
            <td><font size="-1">Export messages after: </font></td>
            <td><font size="-1"><input type="text" name="exportAfter"
                                       value="<% if (exportAfter != null) { %><%= exportAfter%><% } %>" size="30"
                                       maxlength="75"> (in format dd/mm/yyyy)</font></td>
            <td><a href="#" onclick="helpwin('emailGateway','exportafter');return false;" title="Click for help"><img
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
            <td><a href="#" onclick="helpwin('emailGateway','allowexportagain');return false;"
                   title="Click for help"><img src="images/help-16x16.gif" width="16" height="16" border="0" hspace="8"></a>
            </td>
         </tr>
         <tr>
            <td><font size="-1">Update email message ID <br>stored in Jive: </font></td>
            <td><font size="-1"><input type="radio" name="updateMessageID" value="true"
                                       <% if (updateMessageID) { %>checked<% } %>>Yes <input type="radio"
                                                                                             name="updateMessageID"
                                                                                             value="false"
                                                                                             <% if (!updateMessageID) { %>checked<% } %>>No</font>
            </td>
            <td><a href="#" onclick="helpwin('emailGateway','updatemessageid');return false;"
                   title="Click for help"><img src="images/help-16x16.gif" width="16" height="16" border="0" hspace="8"></a>
            </td>
         </tr>
         <tr>
            <td><font size="-1">Debug:</font></td>
            <td><font size="-1"><input type="radio" name="debug" value="true" <% if (debug) { %>checked<% } %>>Enabled
               <input type="radio" name="debug" value="false" <% if (!debug) { %>checked<% } %>>Disabled</font></td>
            <td><a href="#" onclick="helpwin('emailGateway','debug');return false;" title="Click for help"><img
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
