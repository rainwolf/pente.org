<%
   /**
    *	$RCSfile: attachSettings.jsp,v $
    *	$Revision: 1.4 $
    *	$Date: 2002/11/13 23:57:24 $
    */
%>

<%@ page import="java.io.*,
                 java.util.*,
                 java.text.*,
                 com.jivesoftware.forum.*,
                 com.jivesoftware.forum.util.*,
                 com.jivesoftware.util.ParamUtils"
         errorPage="error.jsp"
%>

<%@ include file="global.jsp" %>

<%! // Global methods, vars, etc

   static final String[] CONTENT_TYPES = {
      "application/msword",
      "application/octet-stream",
      "application/pdf",
      "application/postscript",
      "application/x-gzip-compressed",
      "application/x-zip-compressed",
      "application/zip",
      "audio/basic",
      "audio/mpeg",
      "image/bmp",
      "image/gif",
      "image/jpeg",
      "image/pjpeg",
      "image/psd",
      "image/tiff",
      "image/x-photoshop",
      "image/x-png",
      "message/news",
      "message/rfc822",
      "text/html",
      "text/plain",
      "text/richtext",
      "text/xml",
      "video/mpeg",
      "video/quicktime"
   };
%>

<% // Permission check
   if (!isSystemAdmin) {
      throw new UnauthorizedException("You don't have admin privileges to perform this operation.");
   }

   // get parameters
   boolean saveSettings = ParamUtils.getBooleanParameter(request, "saveSettings");
   int maxAttach = ParamUtils.getIntParameter(request, "maxAttach", -1);
   int maxAttachSize = ParamUtils.getIntParameter(request, "maxAttachSize", -1);
   boolean allow = ParamUtils.getBooleanParameter(request, "allow");
   String removeableContentType = ParamUtils.getParameter(request, "contentTypeList");
   String contentTypeFromList = ParamUtils.getParameter(request, "contentTypeFromList");
   String submitButton = ParamUtils.getParameter(request, "submitButton");
   boolean removeContentType = "Remove".equals(submitButton);
   boolean addContentTypeFromList = " << ".equals(submitButton);
   boolean addContentType = "Add".equals(submitButton);
   boolean saveAllowSetting = ParamUtils.getBooleanParameter(request, "saveAllowSetting");
   boolean allowAllByDefault = ParamUtils.getBooleanParameter(request, "allowAllByDefault");
   boolean enableThumbs = ParamUtils.getBooleanParameter(request, "enableThumbs");
   boolean preserveAspect = ParamUtils.getBooleanParameter(request, "preserveAspect");
   boolean clearImageCache = request.getParameter("clearImageCache") != null;
   int maxThumbDimension = ParamUtils.getIntParameter(request, "maxThumbDimension", -1);
   String contentType = ParamUtils.getParameter(request, "contentType");

   // Get the attachment manager
   AttachmentManager attachmentManager = forumFactory.getAttachmentManager();

   if (clearImageCache) {
      // Delete all files in the attachments/cache/*.png
      File cacheDir = new File(JiveGlobals.getJiveHome() + File.separator
         + "attachments" + File.separator + "cache");
      String[] cacheFiles = cacheDir.list();
      for (int i = 0; i < cacheFiles.length; i++) {
         if (cacheFiles[i].endsWith(".png")) {
            (new File(cacheDir, cacheFiles[i])).delete();
         }
      }
      // Done, so redirect
      response.sendRedirect("attachSettings.jsp");
      return;
   }

   if (saveAllowSetting) {
      attachmentManager.setAllowAllByDefault(allowAllByDefault);
      // Done so redirect
      response.sendRedirect("attachSettings.jsp");
      return;
   }

   if (addContentType && contentType != null) {
      contentType = contentType.trim();
      if (attachmentManager.getAllowAllByDefault()) {
         attachmentManager.addDisallowedType(contentType);
      } else {
         attachmentManager.addAllowedType(contentType);
      }
      // Done so redirect
      response.sendRedirect("attachSettings.jsp");
      return;
   }

   if (saveSettings) {
      if (maxAttach != -1) {
         attachmentManager.setMaxAttachmentsPerMessage(maxAttach);
      }
      if (maxAttachSize != -1) {
         attachmentManager.setMaxAttachmentSize(maxAttachSize);
      }
      attachmentManager.setImagePreviewEnabled(enableThumbs);
      attachmentManager.setImagePreviewRatioEnabled(preserveAspect);
      if (maxThumbDimension != -1) {
         attachmentManager.setImagePreviewMaxSize(maxThumbDimension);
      }
      // Done so redirect
      response.sendRedirect("attachSettings.jsp");
      return;
   }

   if (removeContentType) {
      if (attachmentManager.getAllowAllByDefault()) {
         attachmentManager.removeDisallowedType(removeableContentType);
      } else {
         attachmentManager.removeAllowedType(removeableContentType);
      }
      // Done so redirect
      response.sendRedirect("attachSettings.jsp");
      return;
   }

   if (addContentTypeFromList) {
      if (attachmentManager.getAllowAllByDefault()) {
         attachmentManager.addDisallowedType(contentTypeFromList);
      } else {
         attachmentManager.addAllowedType(contentTypeFromList);
      }
      // Done so redirect
      response.sendRedirect("attachSettings.jsp");
      return;
   }

   // Load attach setting values from the API
   allow = attachmentManager.getAllowAllByDefault();
   Iterator contentTypes = null;
   if (allow) {
      contentTypes = attachmentManager.disallowedTypes();
   } else {
      contentTypes = attachmentManager.allowedTypes();
   }
   maxAttachSize = attachmentManager.getMaxAttachmentSize();
   maxAttach = attachmentManager.getMaxAttachmentsPerMessage();
   enableThumbs = attachmentManager.isImagePreviewEnabled();
   preserveAspect = attachmentManager.isImagePreviewRatioEnabled();
   maxThumbDimension = attachmentManager.getImagePreviewMaxSize();
%>

<%@ include file="header.jsp" %>

<% // Title of this page and breadcrumbs
   String title = "Attachment Settings";
   String[][] breadcrumbs = {
      {"Main", "main.jsp"},
      {title, "attachSettings.jsp"}
   };
%>
<%@ include file="title.jsp" %>

<font size="-1">
   Edit attachment policies using the forms below.
</font>
<p>

<form action="attachSettings.jsp">
   <input type="hidden" name="saveSettings" value="true">

   <font size="-1"><b>General Attachment Settings</b></font>
   <p>
   <ul>
      <table cellpadding="3" cellspacing="1" border="0">
         <tr>
            <td><font size="-1">Max number of attachments per message:</font></td>
            <td><input type="text" size="3" maxlength="6" name="maxAttach" value="<%= maxAttach %>"></td>
            <td><a href="#" onclick="helpwin('attach_settings','max_attach');return false;"
                   title="Click for help"
            ><img src="images/help-16x16.gif" width="16" height="16" border="0" hspace="8"></a>
            </td>
         </tr>
         <tr>
            <td><font size="-1">Max attachment size (kilobytes):</font></td>
            <td><input type="text" size="10" maxlength="15" name="maxAttachSize" value="<%= maxAttachSize %>"></td>
            <td><a href="#" onclick="helpwin('attach_settings','max_size');return false;"
                   title="Click for help"
            ><img src="images/help-16x16.gif" width="16" height="16" border="0" hspace="8"></a>
            </td>
         </tr>
         <tr>
            <td><font size="-1">Enable image previews:</font></td>
            <td>
               <font size="-1">
                  <input type="radio" name="enableThumbs" value="true" id="rb01"<%= enableThumbs?" checked":"" %>>
                  <label for="rb01">Yes</label>

                  <input type="radio" name="enableThumbs" value="false" id="rb02"<%= (!enableThumbs)?" checked":"" %>>
                  <label for="rb02">No</label>
               </font>
            </td>
            <td><a href="#" onclick="helpwin('attach_settings','enable_img_preview');return false;"
                   title="Click for help"
            ><img src="images/help-16x16.gif" width="16" height="16" border="0" hspace="8"></a>
            </td>
         </tr>
         <% if (enableThumbs) { %>
         <tr>
            <td><font size="-1">&nbsp;</font></td>
            <td>
               <input type="submit" name="clearImageCache" value="Clear Image Cache">
            </td>
            <td><a href="#" onclick="helpwin('attach_settings','clear_img_cache');return false;"
                   title="Click for help"
            ><img src="images/help-16x16.gif" width="16" height="16" border="0" hspace="8"></a>
            </td>
         </tr>
         <tr>
            <td><font size="-1">Preserve image preview aspect ratio:</font></td>
            <td>
               <font size="-1">
                  <input type="radio" name="preserveAspect" value="true" id="rb03"<%= preserveAspect?" checked":"" %>>
                  <label for="rb03">Yes</label>

                  <input type="radio" name="preserveAspect" value="false" id="rb04"<%= !preserveAspect?" checked":"" %>>
                  <label for="rb04">No</label>
               </font>
            </td>
            <td><a href="#" onclick="helpwin('attach_settings','preserve_ratio');return false;"
                   title="Click for help"
            ><img src="images/help-16x16.gif" width="16" height="16" border="0" hspace="8"></a>
            </td>
         </tr>
         <tr>
            <td><font size="-1">Max image preview demension (pixels):</font></td>
            <td><input type="text" size="10" maxlength="15" name="maxThumbDimension"
                       value="<%= maxThumbDimension %>"></font></td>
            <td><a href="#" onclick="helpwin('attach_settings','max_dimension');return false;"
                   title="Click for help"
            ><img src="images/help-16x16.gif" width="16" height="16" border="0" hspace="8"></a>
            </td>
         </tr>
         <% } %>
      </table>
      <p>
         <input type="submit" value="Save Settings">
</form>
</ul>

<font size="-1"><b>Content Types</b></font>
<a href="#" onclick="helpwin('attach_settings','content_types');return false;"
   title="Click for help"
><img src="images/help-16x16.gif" width="16" height="16" border="0" hspace="8"></a>
<ul>
   <form action="attachSettings.jsp">
      <input type="hidden" name="saveAllowSetting" value="true">
      <table cellpadding="2" cellspacing="0" border="0">
         <tr>
            <td><input type="radio" name="allowAllByDefault" id="rb10"<%= (allow?" checked":"") %> value="true"></td>
            <td><label for="rb10"><font size="-1">Allow all content types except those listed below.</font></label></td>
            <td><a href="#" onclick="helpwin('attach_settings','allow_all');return false;"
                   title="Click for help"
            ><img src="images/help-16x16.gif" width="16" height="16" border="0" hspace="8"></a>
            </td>
         </tr>
         <tr>
            <td><input type="radio" name="allowAllByDefault" id="rb11"<%= (!allow?" checked":"") %> value="false"></td>
            <td><label for="rb11"><font size="-1">Disallow all content types except those listed below.</font></label>
            </td>
            <td><a href="#" onclick="helpwin('attach_settings','disallow_all');return false;"
                   title="Click for help"
            ><img src="images/help-16x16.gif" width="16" height="16" border="0" hspace="8"></a>
            </td>
            </td>
         </tr>
         <tr>
            <td colspan="3">
               <input type="submit" value="Save">
            </td>
         </tr>
      </table>
   </form>

   <form action="attachSettings.jsp">
      <table bgcolor="<%= tblBorderColor %>" cellpadding="0" cellspacing="0" border="0" width="">
         <tr>
            <td>
               <table bgcolor="<%= tblBorderColor %>" cellpadding="4" cellspacing="1" border="0" width="100%">
                  <tr bgcolor="#eeeeee">
                     <td align="center"><font size="-2" face="verdana"><b><%= (allow) ? "DISALLOWED" : "ALLOWED" %>
                        CONTENT TYPES</b></font></td>
                     <td><font size="-2">&nbsp;</font></td>
                     <td align="center"><font size="-2" face="verdana"><b>COMMON CONTENT TYPES</b></font></td>
                  </tr>
                  <tr bgcolor="#ffffff">
                     <td valign="top">
                        <font size="-1">
                           <select size="10" name="contentTypeList">
                              <% boolean hadNext = contentTypes.hasNext();
                                 while (contentTypes.hasNext()) {
                                    String type = (String) contentTypes.next();
                              %>
                              <option value="<%= type %>"><%= type %>
                                    <%  } %>
                                    <%  if (hadNext) { %>
                           </select><br>
                           <center><input type="submit" name="submitButton" value="Remove"></center>
                           <% } else { %>
                           </select>
                           <% } %>
                        </font>
                     </td>
                     <td nowrap>
                        &nbsp;<input type="submit" name="submitButton" value=" &lt;&lt; ">&nbsp;
                     </td>
                     <td valign="top"><font size="-1">
                        <select size="10" name="contentTypeFromList">
                           <% for (int i = 0; i < CONTENT_TYPES.length; i++) { %>
                           <option value="<%= CONTENT_TYPES[i] %>"><%= CONTENT_TYPES[i] %>
                                 <%  } %>
                        </select>
                     </font>
                     </td>
                  </tr>
                  <tr bgcolor="#ffffff">
                     <td colspan="3">
                        <font size="-1">
                           Add Content Type:
                        </font>
                        <input type="text" name="contentType" size="20" maxlength="50">
                        <input type="submit" value="Add" name="submitButton">
                     </td>
                  </tr>
               </table>
            </td>
         </tr>
      </table>
   </form>
</ul>

<p>

   <%@ include file="footer.jsp" %>
