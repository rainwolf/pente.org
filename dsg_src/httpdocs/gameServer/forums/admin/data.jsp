<%
   /**
    *	$RCSfile: data.jsp,v $
    *	$Revision: 1.4 $
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

<% // Permission check
   if (!isSystemAdmin) {
      throw new UnauthorizedException("You don't have admin privileges to perform this operation.");
   }

   // Get a handle on a DbForumFactory object (we can do that because we're
   // the system admin:
   DbForumFactory dbForumFactory = DbForumFactory.getInstance();

   // Get a DbDataExport instance:
   DbDataExport exporter = DbDataExport.getInstance(dbForumFactory);

   // If an export is currently running, redirect to the status page:
   if (exporter.isRunning()) {
      response.sendRedirect("dataStatus.jsp?type=export");
      return;
   }

   // Get parameters
   boolean cancel = request.getParameter("cancel") != null;
   boolean doExport = "export".equals(request.getParameter("mode"));
   boolean doImport = "import".equals(request.getParameter("mode"));
   boolean startExport = request.getParameter("startExport") != null;
   boolean startImport = request.getParameter("startImport") != null;
   String filename = request.getParameter("file");
   boolean standard = "standard".equals(request.getParameter("type"));
   boolean custom = "custom".equals(request.getParameter("type"));
   boolean exportIDs = ParamUtils.getBooleanParameter(request, "exportIDs");
   boolean exportUsers = ParamUtils.getBooleanParameter(request, "exportUsers");
   boolean exportGroups = ParamUtils.getBooleanParameter(request, "exportGroups");
   boolean exportPerms = ParamUtils.getBooleanParameter(request, "exportPerms");
   boolean exportForums = ParamUtils.getBooleanParameter(request, "exportForums");
   String customFilename = ParamUtils.getParameter(request, "customFilename");
   // Set default values for the booleans if no parameter was passed in
   if (request.getParameter("exportIDs") == null) {
      exportIDs = false;
   }
   if (request.getParameter("exportUsers") == null) {
      if (custom) {
         exportUsers = false;
      } else {
         exportUsers = true;
      }
   }
   if (request.getParameter("exportGroups") == null) {
      if (custom) {
         exportGroups = false;
      } else {
         exportGroups = true;
      }
   }
   if (request.getParameter("exportPerms") == null) {
      if (custom) {
         exportPerms = false;
      } else {
         exportPerms = true;
      }
   }
   if (request.getParameter("exportForums") == null) {
      if (custom) {
         exportForums = false;
      } else {
         exportForums = true;
      }
   }

   // Cancel if requested
   if (cancel) {
      response.sendRedirect("data.jsp");
      return;
   }

   // Setup the correct output filename:
   if (doExport) {
      if (customFilename == null) {
         customFilename = exporter.getFilename();
      }
   }

   // Set properties on the exporter object, run an export if requested
   boolean exportFilenameError = false;
   boolean exportIOError = false;
   if (doExport) {
      exporter.setExportIDs(exportIDs);
      exporter.setExportUsers(exportUsers);
      exporter.setExportGroups(exportGroups);
      exporter.setExportPerms(exportPerms);
      exporter.setExportForums(exportForums);
      if (customFilename != null) {
         try {
            exporter.setFilename(customFilename);
         } catch (IllegalArgumentException iae) { // bad filename
            exportFilenameError = true;
         }
      }
      // Run the export
      if (startExport && !exportFilenameError) {
         try {
            exporter.export();
            response.sendRedirect("data.jsp");
            return;
         } catch (IOException ioe) {
            exportIOError = true;
         }
      }
   }

   // Do an import
   boolean genImportError = false;
   if (doImport && startImport) {
      // Convert the filename from hex:
      filename = new String(StringUtils.decodeHex(filename));
      Reader in = new BufferedReader(new InputStreamReader(new FileInputStream(
         new File(JiveGlobals.getJiveHome() + System.getProperty("file.separator")
            + "data" + System.getProperty("file.separator") + filename)), "UTF-8"));
      DbDataImport importer = new DbDataImport(dbForumFactory);
      try {
         importer.doImport(in);
      } catch (Exception e) {
         genImportError = true;
         e.printStackTrace();
         response.sendRedirect("data.jsp?mode=import&error=true");
         return;
      }
      response.sendRedirect("data.jsp");
      return;
   }
%>

<%@ include file="header.jsp" %>

<p>

      <%  // Title of this page and breadcrumbs
        String title = "Data Import &amp; Export";
        String[][] breadcrumbs = {
            {"Main", "main.jsp"},
            {title, "data.jsp"}
        };
%>
   <%@ include file="title.jsp" %>

   <script language="JavaScript" type="text/javascript">
      <!--
      function disable(el, val) {
         el.exportIDs.disabled = val;
         el.exportUsers.disabled = val;
         el.exportGroups.disabled = val;
         el.exportPerms.disabled = val;
         el.exportForums.disabled = val;
         el.customFilename.disabled = val;
      }

      //-->
   </script>

   <font size="-1">
      The import and export functions allow you to read data into and write
      data from your Jive Forums installation. All data uses the Jive Forums XML format.
   </font>

<p>

      <%  // Show the import/export choice if no choice has been selected yet:
        if (!doImport && !doExport) {
%>

   <font size="-1">
      <b>Please choose what you would like to do:</b> -- Note, you will be able
      to customize your import or export.
   </font>
<p>

<form action="data.jsp">
   <table cellpadding="2" cellspacing="0" border="0">
      <tr>
         <td rowspan="99" nowrap>&nbsp;&nbsp;</td>
         <td><input type="radio" name="mode" value="import" id="rb01"></td>
         <td>
            <font size="-1">
               <label for="rb01">
                  Import data to this Jive Forums installation.
               </label>
            </font>
         </td>
      </tr>
      <tr>
         <td><input type="radio" name="mode" value="export" id="rb02"></td>
         <td>
            <font size="-1">
               <label for="rb02">
                  Export data from this Jive Forums installation.
               </label>
            </font>
         </td>
      </tr>
      <tr>
         <td>&nbsp;</td>
         <td>
            <br>
            <input type="submit" value="Continue...">
            <input type="submit" name="cancel" value="Cancel">
         </td>
      </tr>
   </table>
</form>

<% // Show options for doImport or doExport:
} else {
%>
<% // Options for importing
   if (doImport) {
%>

<font size="-1">
   <p>
      <b>Import Data</b>
   </p>
</font>

<font size="-1">
   Choose a data file and proceed with an import. All files are imported from
   the directory <%= (JiveGlobals.getJiveHome() + File.separator + "data" + File.separator) %>
</font>
<p>

      <%  if ("true".equals(request.getParameter("error"))) { %>

   <font size="-1">
      <i>An error occurred while importing your data. Please see your
         appserver's error logs for more information.</i>
   </font>
<p>

      <%  } %>

<form action="data.jsp">
   <input type="hidden" name="mode" value="import">

   <table bgcolor="<%= tblBorderColor %>" cellpadding="0" cellspacing="0" border="0">
      <tr>
         <td>
            <table bgcolor="<%= tblBorderColor %>" cellpadding="3" cellspacing="1" border="0">
               <tr>
                  <td bgcolor="#eeeeee" align="center"><font size="-2" face="verdana">&nbsp;</font></td>
                  <td bgcolor="#eeeeee" align="center"><font size="-2" face="verdana"><b>&nbsp;FILENAME&nbsp;</b></font>
                  </td>
                  <td bgcolor="#eeeeee" align="center"><font size="-2" face="verdana"><b>&nbsp;SIZE&nbsp;</b></font>
                  </td>
                  <td bgcolor="#eeeeee" align="center"><font size="-2" face="verdana"><b>&nbsp;LAST
                     MODIFIED&nbsp;</b></font></td>
               </tr>
               <% // Get a list of xml files from the data dir:
                  File dataDir = new File(JiveGlobals.getJiveHome(), "data");
                  boolean error = false;
                  boolean foundFile = false;
                  if (!dataDir.exists() || !dataDir.canRead()) {
                     error = true;
               %>
               <tr bgcolor="#ffffff">
                  <td align="center" colspan="4">
                     <font size="-1">
                        &nbsp;<i><%= JiveGlobals.getJiveHome() + File.separator + "data" %>
                        does not exist or your appserver can not read it.&nbsp;</i>
                     </font>
                  </td>
               </tr>
               <% } else {
                  String[] files = dataDir.list();
                  for (int i = 0; i < files.length; i++) {
                     String fname = files[i];
                     if (fname.endsWith(".xml")) {
                        foundFile = true;
                        File file = new File(dataDir, fname);
                        String filesize = "n/a";
                        long bytes = file.length();
                        if (bytes < 1024) {
                           filesize = bytes + " b";
                        } else if (bytes >= 1024 && bytes < (1024 * 1024)) {
                           filesize = (bytes / 1024) + " K";
                        } else {
                           filesize = (bytes / (1024 * 1024)) + " MB";
                        }
               %>
               <tr bgcolor="#ffffff">
                  <td><input type="radio" name="file" value="<%= StringUtils.encodeHex(fname.getBytes()) %>"
                             id="<%= i %>"></td>
                  <td><font size="-1">
                     &nbsp;<label for="<%= i %>"><%= fname %>
                  </label>&nbsp;
                  </font>
                  </td>
                  <td><font size="-1">
                     &nbsp;<%= filesize %>&nbsp;
                  </font>
                  </td>
                  <td><font size="-1">
                     &nbsp;<%= SkinUtils.formatDate(request, pageUser, new Date(file.lastModified())) %>&nbsp;
                  </font>
                  </td>
               </tr>
               <% }
               }
                  if (!foundFile) {
               %>
               <tr bgcolor="#ffffff">
                  <td align="center" colspan="4">
                     <font size="-1">
                        <i>No XML import files were found.</i>
                     </font>
                  </td>
               </tr>
               <% }
               }
               %>
            </table>
         </td>
      </tr>
   </table>
   <p>

         <%  if (!error && foundFile) { %>

      <input type="submit" value="Import File" name="startImport">

         <%  } %>

</form>

<% // Options for exporting
} else if (doExport) {
%>

<font size="-1">
   <b>Export Options</b>
</font>
<p>

   <font size="-1">
      Choose either a standard data export or a custom one. If you choose a custom
      one, please use the form below to pick and choose what data you want exported.
   </font>
<p>

<form action="data.jsp">
   <input type="hidden" name="mode" value="export">
   <table cellpadding="2" cellspacing="0" border="0">
      <tr>
         <td rowspan="99" nowrap>&nbsp;&nbsp;</td>
         <td valign="top">
            <input type="radio" name="type" value="standard" id="rb01"<%= (standard?" checked":"") %>
                   onclick="disable(this.form,true);">
         </td>
         <td>
            <font size="-1">
               <label for="rb01">
                  Standard Export
               </label>
               -- All users, groups, permissions and forums are exported to
               <tt><%= JiveGlobals.getJiveHome() + File.separator %>data<%= File.separator + exporter.getFilename() %>
               </tt>
            </font>
         </td>
      </tr>
      <tr>
         <td valign="top">
            <input type="radio" name="type" value="custom" id="rb02"<%= (custom?" checked":"") %>
                   onclick="disable(this.form,false);">
         </td>
         <td>
            <font size="-1">
               <label for="rb02">
                  Custom Export
               </label>
               -- Please customize your export by selecting the following options:
            </font>
         </td>
      </tr>
      <tr>
         <td>&nbsp;</td>
         <td>
            <table cellpadding="2" cellspacing="0" border="0" width="100%">
               <tr>
                  <td width="1%" nowrap>
                     <input type="checkbox" name="exportIDs" id="cb01"
                            onclick="this.form.type[this.form.type.length-1].checked=true;"
                        <%= (exportIDs?" checked":"") %>>
                  </td>
                  <td width="1%" nowrap>
                     <font size="-1">
                        <label for="cb01">
                           Export all object IDs
                        </label>
                     </font>
                  </td>
                  <td width="98%">
                     <a href="#" onclick="helpwin('data','export_all_ids');return false;" title="Click for help"
                     ><img src="images/help-16x16.gif" width="16" height="16" border="0" hspace="8"></a>
                  </td>
               </tr>
               <tr>
                  <td width="1%" nowrap><input type="checkbox" name="exportUsers" id="cb02"
                                               onclick="this.form.type[this.form.type.length-1].checked=true;"
                     <%= (exportUsers?" checked":"") %>>
                  </td>
                  <td width="1%" nowrap>
                     <font size="-1">
                        <label for="cb02">
                           Export all users
                        </label>
                     </font>
                  </td>
                  <td width="98%">
                     <a href="#" onclick="helpwin('data','export_all_users');return false;" title="Click for help"
                     ><img src="images/help-16x16.gif" width="16" height="16" border="0" hspace="8"></a>
                  </td>
               </tr>
               <tr>
                  <td width="1%" nowrap>
                     <input type="checkbox" name="exportGroups" id="cb03"
                            onclick="this.form.type[this.form.type.length-1].checked=true;"
                        <%= (exportGroups?" checked":"") %>>
                  </td>
                  <td width="1%" nowrap>
                     <font size="-1">
                        <label for="cb03">
                           Export all groups
                        </label>
                     </font>
                  </td>
                  <td width="98%">
                     <a href="#" onclick="helpwin('data','export_all_groups');return false;" title="Click for help"
                     ><img src="images/help-16x16.gif" width="16" height="16" border="0" hspace="8"></a>
                  </td>
               </tr>
               <tr>
                  <td width="1%" nowrap>
                     <input type="checkbox" name="exportPerms" id="cb04"
                            onclick="this.form.type[this.form.type.length-1].checked=true;"
                        <%= (exportPerms?" checked":"") %>>
                  </td>
                  <td width="1%" nowrap>
                     <font size="-1">
                        <label for="cb04">
                           Export all permissions
                        </label>
                     </font>
                  </td>
                  <td width="98%">
                     <a href="#" onclick="helpwin('data','export_perms');return false;" title="Click for help"
                     ><img src="images/help-16x16.gif" width="16" height="16" border="0" hspace="8"></a>
                  </td>
               </tr>
               <tr>
                  <td width="1%" nowrap>
                     <input type="checkbox" name="exportForums" id="cb05"
                            onclick="this.form.type[this.form.type.length-1].checked=true;"
                        <%= (exportForums?" checked":"") %>>
                  </td>
                  <td width="1%" nowrap>
                     <font size="-1">
                        <label for="cb05">
                           Export all forums
                        </label>
                     </font>
                  </td>
                  <td width="98%">
                     <a href="#" onclick="helpwin('data','export_forums');return false;" title="Click for help"
                     ><img src="images/help-16x16.gif" width="16" height="16" border="0" hspace="8"></a>
                  </td>
               </tr>
               <% if (startExport && exportFilenameError) { %>
               <tr>
                  <td colspan="3">
                     <font size="-1" color="#ff0000">
                        Error: Invalid filename. Only alphanumeric characters and dashes
                        and one "." are allowed.
                     </font>
                  </td>
               </tr>
               <% } %>
               <tr>
                  <td colspan="3">
                     <font size="-1">
                        <label for="cb06">
                           Custom File Name:
                        </label>
                     </font>
                     <input type="text" name="customFilename" value="<%= exporter.getFilename() %>" size="30"
                            maxlength="50"
                            onclick="this.form.type[this.form.type.length-1].checked=true;">
                     <a href="#" onclick="helpwin('data','custom_filename');return false;" title="Click for help"
                     ><img src="images/help-16x16.gif" width="16" height="16" border="0" hspace="8"></a>
                  </td>
               </tr>
            </table>
         </td>
      </tr>
      <tr>
         <td>&nbsp;</td>
         <td>
            <br>
            <input type="submit" value="Start Data Export" name="startExport">
            <input type="submit" name="cancel" value="Cancel">
         </td>
      </tr>
   </table>
</form>

<% } // end else if doExport %>

<% } // end else to if !doImport && !doExport %>

</body>
</html>
