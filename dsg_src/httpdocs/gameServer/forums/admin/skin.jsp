<%
   /**
    *	$RCSfile: skin.jsp,v $
    *	$Revision: 1.15.2.2 $
    *	$Date: 2003/03/28 22:35:11 $
    */
%>

<%@ page import="java.util.*,
                 com.jivesoftware.forum.*,
                 com.jivesoftware.util.*,
                 com.jivesoftware.forum.util.*" %>

<%! // global methods, vars, etc

   // Default properties values. (Maintenance note: this list is duplicated in upgrade.jsp)
   static Map properties = new HashMap();

   static {
      properties.put("fontFace", "tahoma,arial,helvetica,sans-serif");
      properties.put("descrFontFace", "verdana,arial,sans-serif");
      properties.put("fontSize", "0.8em");
      properties.put("descrFontSize", "0.8em");

      properties.put("homeURL", "");
      properties.put("bgColor", "#ffffff");
      properties.put("textColor", "#000000");
      properties.put("linkColor", "#003399");
      properties.put("vLinkColor", "#003399");
      properties.put("aLinkColor", "#99ccff");

      properties.put("borderColor", "#cccccc");
      properties.put("evenColor", "#ffffff");
      properties.put("oddColor", "#eeeeee");
      properties.put("activeColor", "#ffffcc");
      properties.put("tableHeaderColor", "#ffffff");
      properties.put("tableHeaderBgColor", "#336699");
      properties.put("breadcrumbColor", "#660000");
      properties.put("breadcrumbColorHover", "#660000");

      properties.put("communityDescription", "Welcome to our online community. Please choose from one of the forums below or log-in to your user account to start using this service.");
      properties.put("headerLogo", "<img src=\"images/logo.gif\" width=\"300\" height=\"45\" alt=\"Jive Community Forums\" border=\"0\">");
      properties.put("headerBorderColor", "#003366");
      properties.put("headerBgColor", "#336699");

      properties.put("threadMode", "flat"); // other values are "threaded" or "tree"
      properties.put("trackIP", "true");
      properties.put("newAccountCreationEnabled", "true");
      properties.put("showLastPostLink", "true");
      properties.put("usersChooseLocale", "false");
      properties.put("useDefaultWelcomeText", "true");
      properties.put("useDefaultHeaderImage", "true");
      properties.put("readTracker.enabled", "true");

      properties.put("usersChooseThreadMode", "false");
   }
%>

<%@ include file="global.jsp" %>

<% // Get parameters
   boolean save = "save".equals(request.getParameter("formAction"));
   boolean cancel = "cancel".equals(request.getParameter("formAction"));
   boolean restoreDefaults = "defaults".equals(request.getParameter("formAction"));
   String mode = ParamUtils.getParameter(request, "mode");

   // Cancel if requested
   if (cancel) {
      response.sendRedirect("skin.jsp");
      return;
   }

   // Get the current theme. The current theme will be "default" if no
   // current theme is detected.
   String theme = "default";

   // Save properties to current theme
   if (save) {
      // Loop through the list of properties, save whatever is not null
      for (Iterator iter = properties.keySet().iterator(); iter.hasNext(); ) {
         String propName = (String) iter.next();
         String propValue = ParamUtils.getParameter(request, propName, true);
         if (propValue != null) {
            if (propName.indexOf(".") > -1) {
               JiveGlobals.setJiveProperty(propName, propValue);
            } else {
               JiveGlobals.setJiveProperty("skin." + theme + "." + propName, propValue);
            }
         }
      }
   }

   // Restore the current theme to use the default
   if (restoreDefaults) {
      // Loop through the defaults, set jive properties accordingly
      for (Iterator iter = properties.keySet().iterator(); iter.hasNext(); ) {
         String key = (String) iter.next();
         String value = (String) properties.get(key);
         if (key.indexOf(".") > -1) {
            JiveGlobals.setJiveProperty(key, value);
         } else {
            JiveGlobals.setJiveProperty("skin.default." + key, value);
         }
      }
   }

   // List o' properties
   String skinTheme = "skin." + theme + ".";
   String fontFace = JiveGlobals.getJiveProperty(skinTheme + "fontFace");
   String descrFontFace = JiveGlobals.getJiveProperty(skinTheme + "descrFontFace");
   String fontSize = JiveGlobals.getJiveProperty(skinTheme + "fontSize");
   String descrFontSize = JiveGlobals.getJiveProperty(skinTheme + "descrFontSize");

   String homeURL = JiveGlobals.getJiveProperty(skinTheme + "homeURL");
   String bgColor = JiveGlobals.getJiveProperty(skinTheme + "bgColor");
   String textColor = JiveGlobals.getJiveProperty(skinTheme + "textColor");
   String linkColor = JiveGlobals.getJiveProperty(skinTheme + "linkColor");
   String vLinkColor = JiveGlobals.getJiveProperty(skinTheme + "vLinkColor");
   String aLinkColor = JiveGlobals.getJiveProperty(skinTheme + "aLinkColor");

   String borderColor = JiveGlobals.getJiveProperty(skinTheme + "borderColor");
   String evenColor = JiveGlobals.getJiveProperty(skinTheme + "evenColor");
   String oddColor = JiveGlobals.getJiveProperty(skinTheme + "oddColor");
   String activeColor = JiveGlobals.getJiveProperty(skinTheme + "activeColor");
   String tableHeaderColor = JiveGlobals.getJiveProperty(skinTheme + "tableHeaderColor");
   String tableHeaderBgColor = JiveGlobals.getJiveProperty(skinTheme + "tableHeaderBgColor");
   String breadcrumbColor = JiveGlobals.getJiveProperty(skinTheme + "breadcrumbColor");
   String breadcrumbColorHover = JiveGlobals.getJiveProperty(skinTheme + "breadcrumbColorHover");

   String communityDescription = JiveGlobals.getJiveProperty(skinTheme + "communityDescription");
   String headerLogo = JiveGlobals.getJiveProperty(skinTheme + "headerLogo");
   String headerBorderColor = JiveGlobals.getJiveProperty(skinTheme + "headerBorderColor");
   String headerBgColor = JiveGlobals.getJiveProperty(skinTheme + "headerBgColor");

   String threadMode = JiveGlobals.getJiveProperty(skinTheme + "threadMode");
   if (threadMode == null || "".equals(threadMode)) {
      // set "flat" mode as the default thread view
      threadMode = "flat";
   }
   String trackIP = JiveGlobals.getJiveProperty(skinTheme + "trackIP");
   if (trackIP == null) {
      // on by default
      trackIP = "true";
   } else if (!"true".equals(trackIP)) {
      trackIP = "false";
   }
   String newAccountCreationEnabled = JiveGlobals.getJiveProperty("skin.default.newAccountCreationEnabled");
   if (newAccountCreationEnabled == null) {
      // enable by default
      newAccountCreationEnabled = "true";
   }
   String showLastPostLink = JiveGlobals.getJiveProperty("skin.default.showLastPostLink");
   if (showLastPostLink == null) {
      // enable by default
      showLastPostLink = "true";
   }
   String usersChooseLocale = JiveGlobals.getJiveProperty("skin.default.usersChooseLocale");
   if (usersChooseLocale == null) {
      // not enabled by default
      usersChooseLocale = "false";
   }
   String useDefaultWelcomeText = JiveGlobals.getJiveProperty("skin.default.useDefaultWelcomeText");
   if (useDefaultWelcomeText == null) {
      // enable by default
      useDefaultWelcomeText = "true";
   }
   String useDefaultHeaderImage = JiveGlobals.getJiveProperty("skin.default.useDefaultHeaderImage");
   if (useDefaultHeaderImage == null) {
      // enable by default
      useDefaultHeaderImage = "true";
   }
   String usersChooseThreadMode = JiveGlobals.getJiveProperty("skin.default.usersChooseThreadMode");
   if (usersChooseThreadMode == null) {
      usersChooseThreadMode = (String) properties.get("usersChooseThreadMode");
      JiveGlobals.setJiveProperty("skin.default.usersChooseThreadMode", usersChooseThreadMode);
   }
   String readTrackerEnabled = JiveGlobals.getJiveProperty("readTracker.enabled");
   if (readTrackerEnabled == null) {
      readTrackerEnabled = (String) properties.get("readTracker.enabled");
      JiveGlobals.setJiveProperty("readTracker.enabled", readTrackerEnabled);
   }

   if (activeColor == null) {
      activeColor = "#ffcccc";
      JiveGlobals.setJiveProperty("skin.default.activeColor", activeColor);
   }

   // escape the < and > and quotes from necessary fields
   communityDescription = StringUtils.escapeHTMLTags(communityDescription);
   communityDescription = StringUtils.replace(communityDescription, "\"", "&quot;");
   headerLogo = StringUtils.escapeHTMLTags(headerLogo);
   headerLogo = StringUtils.replace(headerLogo, "\"", "&quot;");
%>


<%@ include file="header.jsp" %>

<% // Title of this page and breadcrumbs
   String title = "Skin Settings";
   String[][] breadcrumbs = null;
   if ("fonts".equals(mode)) {
      breadcrumbs = new String[][]{
         {"Main", "main.jsp"},
         {title, "skin.jsp"},
         {"Fonts", "skin.jsp?mode=fonts"}
      };
   } else if ("colors".equals(mode)) {
      breadcrumbs = new String[][]{
         {"Main", "main.jsp"},
         {title, "skin.jsp"},
         {"Colors", "skin.jsp?mode=colors"}
      };
   } else if ("forumtext".equals(mode)) {
      breadcrumbs = new String[][]{
         {"Main", "main.jsp"},
         {title, "skin.jsp"},
         {"Forum Text", "skin.jsp?mode=forumtext"}
      };
   } else if ("headerandfooter".equals(mode)) {
      breadcrumbs = new String[][]{
         {"Main", "main.jsp"},
         {title, "skin.jsp"},
         {"Header &amp; Footer", "skin.jsp?mode=headerandfooter"}
      };
   } else if ("features".equals(mode)) {
      breadcrumbs = new String[][]{
         {"Main", "main.jsp"},
         {title, "skin.jsp"},
         {"Features", "skin.jsp?mode=features"}
      };
   } else if ("threadmode".equals(mode)) {
      breadcrumbs = new String[][]{
         {"Main", "main.jsp"},
         {title, "skin.jsp"},
         {"Thread Mode", "skin.jsp?mode=threadmode"}
      };
   } else {
      breadcrumbs = new String[][]{
         {"Main", "main.jsp"},
         {title, "skin.jsp"}
      };
   }
%>
<%@ include file="title.jsp" %>

<script language="JavaScript" type="text/javascript">
   <!--
   var theEl;

   function colorPicker(el) {
      var val = el.value.substr(1, 6);
      var win = window.open("colorPicker.jsp?element=" + el.name + "&defaultColor=" + val, "", "menubar=yes,location=no,personalbar=no,scrollbar=yes,width=580,height=300,resizable");
   }

   //-->
</script>
<style type="text/css">
    .demo {
        text-decoration: underline;
        color: <%= linkColor %>;
    }

    .demo:hover {
        text-decoration: underline;
        color: <%= aLinkColor %>;
    }
</style>

<form action="skin.jsp" name="skinForm" method="post">
   <input type="hidden" name="formAction" value="">
   <% if (mode != null) { %>
   <input type="hidden" name="mode" value="<%= mode %>">
   <% } %>

   <% // Depending on the "mode", we'll print out a different page. The default
      // mode (when mode == null) is to print out the list of editable skin
      // categories.
      if (mode == null) {
   %>
   <font size="-1">
      You can edit the attributes of the default Jive Forums skin by choosing one
      of the options below:
   </font>
   <ul>
      <li><font size="-1"><a href="skin.jsp?mode=fonts">Fonts</a>
         - Adjust the list of fonts and their sizes.
      </font>

      <li><font size="-1"><a href="skin.jsp?mode=colors">Colors</a>
         - Adjust colors of backgrounds, fonts, links, and borders.
      </font>

      <li><font size="-1"><a href="skin.jsp?mode=forumtext">Forum Text</a>
         - Edit the welcome text for your community.
      </font>

      <li><font size="-1"><a href="skin.jsp?mode=headerandfooter">Header &amp; Footer</a>
         - Edit the image and colors of the header and footer as well as
         the text and links for the breadcrumbs.
      </font>

      <li><font size="-1"><a href="skin.jsp?mode=features">Features</a>
         - Turn on or off various features in the default skin.
      </font>

   </ul>

   <% } else if ("fonts".equals(mode)) { %>

   <font size="-1">
      <b>Font Settings</b>
   </font>
   <ul>
      <table bgcolor="<%= tblBorderColor %>" cellpadding="0" cellspacing="0" border="0">
         <tr>
            <td>
               <table bgcolor="<%= tblBorderColor %>" cellpadding="3" cellspacing="1" border="0" width="100%">
                  <tr bgcolor="#ffffff">
                     <td rowspan="2"><font size="-1">Global Font:</font></td>
                     <td nowrap><font size="-1">Font List:</font></td>
                     <td><input type="text" size="25" name="fontFace" maxlength="100"
                                value="<%= (fontFace!=null)?fontFace:"" %>"></td>
                     <td rowspan="2" style="font-size:<%= fontSize %>;font-family:<%= fontFace %>;">
                        This is an example of this font
                     </td>
                  </tr>
                  <tr bgcolor="#ffffff">
                     <td nowrap><font size="-1">Font Size:</font></td>
                     <td><input type="text" size="25" name="fontSize" maxlength="100"
                                value="<%= (fontSize!=null)?fontSize:"" %>"></td>
                  </tr>
                  <tr bgcolor="#ffffff">
                     <td rowspan="2">
                        <font size="-1">Description Font:</font>
                        <font size="-2">
                           <br>(Size is relative to the global font.)
                        </font>
                     </td>
                     <td><font size="-1">Font List:</font></td>
                     <td><input type="text" size="25" name="descrFontFace" maxlength="100"
                                value="<%= (descrFontFace!=null)?descrFontFace:"" %>"></td>
                     <td rowspan="2" style="font-size:<%= fontSize %>">
            <span style="font-size:<%= descrFontSize %>;font-family:<%= descrFontFace %>;">
            This would be a forum or category description.
            </span>
                     </td>
                  </tr>
                  <tr bgcolor="#ffffff">
                     <td nowrap><font size="-1">Font Size:</font></td>
                     <td><input type="text" size="25" name="descrFontSize" maxlength="100"
                                value="<%= (descrFontSize!=null)?descrFontSize:"" %>"></td>
                  </tr>
               </table>
            </td>
         </tr>
      </table>
   </ul>

   <% } else if ("colors".equals(mode)) { %>

   <font size="-1">
      <b>Global Color Settings</b>
   </font>
   <ul>
      <table bgcolor="<%= tblBorderColor %>" cellpadding="0" cellspacing="0" border="0">
         <tr>
            <td>
               <table bgcolor="<%= tblBorderColor %>" cellpadding="3" cellspacing="1" border="0" width="100%">
                  <tr bgcolor="#ffffff">
                     <td><font size="-1">Background Color:</font></td>
                     <td>
                        <table border="0" cellpadding="2" cellspacing="0">
                           <tr>
                              <td>
                                 <table cellpadding="0" cellspacing="1" border="1"
                                 >
                                    <td bgcolor="<%= (bgColor!=null)?bgColor:"" %>"
                                    ><a href="#" onclick="colorPicker(document.skinForm.bgColor);"
                                    ><img src="images/blank.gif" width="15" height="15" border="0"></a></td>
                                 </table>
                              </td>
                              <td><input type="text" name="bgColor" size="10" maxlength="20"
                                         value="<%= (bgColor!=null)?bgColor:"" %>"></td>
                           </tr>
                        </table>
                     </td>
                     <td rowspan="5" align="center" bgcolor="<%= bgColor %>">

                        <table cellpadding="5" cellspacing="0" border="0" width="200">
                           <tr>
                              <td>
                                 <font size="-1" color="<%= textColor %>"
                                       face="<%= JiveGlobals.getJiveProperty(skinTheme + "fontFace") %>">
                                    This is how the text looks on your page.
                                    <br>
                                    <a href="#" onclick="return false;" class="demo">
                                       This is what a link
                                       looks like on your page.</a>
                                 </font>
                              </td>
                           </tr>
                        </table>

                     </td>
                  </tr>
                  <tr bgcolor="#ffffff">
                     <td><font size="-1">Text Color:</font></td>
                     <td>
                        <table border="0" cellpadding="2" cellspacing="0">
                           <tr>
                              <td>
                                 <table cellpadding="0" cellspacing="1" border="1"
                                 >
                                    <td bgcolor="<%= (textColor!=null)?textColor:"" %>"
                                    ><a href="#" onclick="colorPicker(document.skinForm.textColor);"
                                    ><img src="images/blank.gif" width="15" height="15" border="0"></a></td>
                                 </table>
                              </td>
                              <td><input type="text" name="textColor" size="10" maxlength="20"
                                         value="<%= (textColor!=null)?textColor:"" %>"></td>
                           </tr>
                        </table>
                     </td>
                  </tr>
                  <tr bgcolor="#ffffff">
                     <td><font size="-1">Link Color:</font></td>
                     <td>
                        <table border="0" cellpadding="2" cellspacing="0">
                           <tr>
                              <td>
                                 <table cellpadding="0" cellspacing="1" border="1"
                                 >
                                    <td bgcolor="<%= (linkColor!=null)?linkColor:"" %>"
                                    ><a href="#" onclick="colorPicker(document.skinForm.linkColor);"
                                    ><img src="images/blank.gif" width="15" height="15" border="0"></a></td>
                                 </table>
                              </td>
                              <td><input type="text" name="linkColor" size="10" maxlength="20"
                                         value="<%= (linkColor!=null)?linkColor:"" %>"></td>
                           </tr>
                        </table>
                     </td>
                  </tr>
                  <tr bgcolor="#ffffff">
                     <td><font size="-1">Visited Link Color:</font></td>
                     <td>
                        <table border="0" cellpadding="2" cellspacing="0">
                           <tr>
                              <td>
                                 <table cellpadding="0" cellspacing="1" border="1"
                                 >
                                    <td bgcolor="<%= (vLinkColor!=null)?vLinkColor:"" %>"
                                    ><a href="#" onclick="colorPicker(document.skinForm.vLinkColor);"
                                    ><img src="images/blank.gif" width="15" height="15" border="0"></a></td>
                                 </table>
                              </td>
                              <td><input type="text" name="vLinkColor" size="10" maxlength="20"
                                         value="<%= (vLinkColor!=null)?vLinkColor:"" %>"></td>
                           </tr>
                        </table>
                     </td>
                  </tr>
                  <tr bgcolor="#ffffff">
                     <td><font size="-1">Active Link Color:</font>
                        <font size="-2">
                           <br>
                           (Also hover color)
                        </font>
                     </td>
                     <td>
                        <table border="0" cellpadding="2" cellspacing="0">
                           <tr>
                              <td>
                                 <table cellpadding="0" cellspacing="1" border="1"
                                 >
                                    <td bgcolor="<%= (aLinkColor!=null)?aLinkColor:"" %>"
                                    ><a href="#" onclick="colorPicker(document.skinForm.aLinkColor);"
                                    ><img src="images/blank.gif" width="15" height="15" border="0"></a></td>
                                 </table>
                              </td>
                              <td><input type="text" name="aLinkColor" size="10" maxlength="20"
                                         value="<%= (aLinkColor!=null)?aLinkColor:"" %>"></td>
                           </tr>
                        </table>
                     </td>
                  </tr>

                  <tr bgcolor="#ffffff">
                     <td><font size="-1">Breadcrumb Color:</font></td>
                     <td>
                        <table border="0" cellpadding="2" cellspacing="0">
                           <tr>
                              <td>
                                 <table cellpadding="0" cellspacing="1" border="1"
                                 >
                                    <td bgcolor="<%= (breadcrumbColor!=null)?breadcrumbColor:"" %>"
                                    ><a href="#" onclick="colorPicker(document.skinForm.breadcrumbColor);"
                                    ><img src="images/blank.gif" width="15" height="15" border="0"></a></td>
                                 </table>
                              </td>
                              <td><input type="text" name="breadcrumbColor" size="10" maxlength="20"
                                         value="<%= (breadcrumbColor!=null)?breadcrumbColor:"" %>"></td>
                     </td>
                  </tr>
               </table>
            </td>
            <td rowspan="2" style="font-size:<%= fontSize %>;font-family:<%= fontFace %>;">
            <span style="color:<%= breadcrumbColor %>;">
            <a href="" style="color:<%= breadcrumbColor %> !important;" onclick="return false;"
            ><b>Home</b></a>
            &raquo;
            <a href="" style="color:<%= breadcrumbColor %> !important;" onclick="return false;"
            ><b>Forums</b></a>
            </span>
            </td>
         </tr>
         <tr bgcolor="#ffffff">
            <td><font size="-1">Breadcrumb Hover Color:</font></td>
            <td>
               <table border="0" cellpadding="2" cellspacing="0">
                  <tr>
                     <td>
                        <table cellpadding="0" cellspacing="1" border="1"
                        >
                           <td bgcolor="<%= (breadcrumbColorHover!=null)?breadcrumbColorHover:"" %>"
                           ><a href="#" onclick="colorPicker(document.skinForm.breadcrumbColorHover);"
                           ><img src="images/blank.gif" width="15" height="15" border="0"></a></td>
                        </table>
                     </td>
                     <td><input type="text" name="breadcrumbColorHover" size="10" maxlength="20"
                                value="<%= (breadcrumbColorHover!=null)?breadcrumbColorHover:"" %>"></td>
            </td>
         </tr>
      </table>
      </td>
      </tr>

      <tr bgcolor="#ffffff">
         <td><font size="-1">Global Border Color:</font></td>
         <td>
            <table border="0" cellpadding="2" cellspacing="0">
               <tr>
                  <td>
                     <table cellpadding="0" cellspacing="1" border="1"
                     >
                        <td bgcolor="<%= (borderColor!=null)?borderColor:"" %>"
                        ><a href="#" onclick="colorPicker(document.skinForm.borderColor);"
                        ><img src="images/blank.gif" width="15" height="15" border="0"></a></td>
                     </table>
                  </td>
                  <td><input type="text" name="borderColor" size="10" maxlength="20"
                             value="<%= (borderColor!=null)?borderColor:"" %>"></td>
         </td>
      </tr>
      </table>
      </td>
      <td rowspan="99">&nbsp;</td>
      </tr>
      <tr bgcolor="#ffffff">
         <td><font size="-1">Even Row Color:</font></td>
         <td>
            <table border="0" cellpadding="2" cellspacing="0">
               <tr>
                  <td>
                     <table cellpadding="0" cellspacing="1" border="1"
                     >
                        <td bgcolor="<%= (evenColor!=null)?evenColor:"" %>"
                        ><a href="#" onclick="colorPicker(document.skinForm.evenColor);"
                        ><img src="images/blank.gif" width="15" height="15" border="0"></a></td>
                     </table>
                  </td>
                  <td><input type="text" name="evenColor" size="10" maxlength="20"
                             value="<%= (evenColor!=null)?evenColor:"" %>"></td>
         </td>
      </tr>
      </table>
      </td>
      </tr>
      <tr bgcolor="#ffffff">
         <td><font size="-1">Odd Row Color:</font></td>
         <td>
            <table border="0" cellpadding="2" cellspacing="0">
               <tr>
                  <td>
                     <table cellpadding="0" cellspacing="1" border="1"
                     >
                        <td bgcolor="<%= (oddColor!=null)?oddColor:"" %>"
                        ><a href="#" onclick="colorPicker(document.skinForm.oddColor);"
                        ><img src="images/blank.gif" width="15" height="15" border="0"></a></td>
                     </table>
                  </td>
                  <td><input type="text" name="oddColor" size="10" maxlength="20"
                             value="<%= (oddColor!=null)?oddColor:"" %>"></td>
         </td>
      </tr>
      </table>
      </td>
      </tr>
      <tr bgcolor="#ffffff">
         <td><font size="-1">Active Row Color:</font></td>
         <td>
            <table border="0" cellpadding="2" cellspacing="0">
               <tr>
                  <td>
                     <table cellpadding="0" cellspacing="1" border="1"
                     >
                        <td bgcolor="<%= (activeColor!=null)?activeColor:"" %>"
                        ><a href="#" onclick="colorPicker(document.skinForm.activeColor);"
                        ><img src="images/blank.gif" width="15" height="15" border="0"></a></td>
                     </table>
                  </td>
                  <td><input type="text" name="activeColor" size="10" maxlength="20"
                             value="<%= (activeColor!=null)?activeColor:"" %>"></td>
         </td>
      </tr>
      </table>
      </td>
      </tr>
      <tr bgcolor="#ffffff">
         <td><font size="-1">Table Header Font Color:</font></td>
         <td>
            <table border="0" cellpadding="2" cellspacing="0">
               <tr>
                  <td>
                     <table cellpadding="0" cellspacing="1" border="1"
                     >
                        <td bgcolor="<%= (tableHeaderColor!=null)?tableHeaderColor:"" %>"
                        ><a href="#" onclick="colorPicker(document.skinForm.tableHeaderColor);"
                        ><img src="images/blank.gif" width="15" height="15" border="0"></a></td>
                     </table>
                  </td>
                  <td><input type="text" name="tableHeaderColor" size="10" maxlength="20"
                             value="<%= (tableHeaderColor!=null)?tableHeaderColor:"" %>"></td>
         </td>
      </tr>
      </table>
      </td>
      </tr>
      <tr bgcolor="#ffffff">
         <td><font size="-1">Table Header Background Color:</font></td>
         <td>
            <table border="0" cellpadding="2" cellspacing="0">
               <tr>
                  <td>
                     <table cellpadding="0" cellspacing="1" border="1"
                     >
                        <td bgcolor="<%= (tableHeaderBgColor!=null)?tableHeaderBgColor:"" %>"
                        ><a href="#" onclick="colorPicker(document.skinForm.tableHeaderBgColor);"
                        ><img src="images/blank.gif" width="15" height="15" border="0"></a></td>
                     </table>
                  </td>
                  <td><input type="text" name="tableHeaderBgColor" size="10" maxlength="20"
                             value="<%= (tableHeaderBgColor!=null)?tableHeaderBgColor:"" %>"></td>
         </td>
      </tr>
      </table>
      </td>
      </tr>
      </table>
      </td></tr>
      </table>
   </ul>

   <% } else if ("forumtext".equals(mode)) { %>

   <font size="-1">
      <b>Community Description</b>
   </font>
   <ul>
      <table cellpadding="3" cellspacing="0" border="0">
         <tr>
            <td>
               <input type="radio" name="useDefaultWelcomeText"
                      value="true"<%= (("true".equals(useDefaultWelcomeText))?" checked":"") %> id="rb01">
            </td>
            <td>
               <font size="-1">
                  <label for="rb01">Use default welcome message (internationalized):</label>
               </font>
            </td>
         </tr>
         <tr>
            <td><font size="-1">&nbsp;</font></td>
            <td>
               <table bgcolor="<%= tblBorderColor %>" cellpadding="1" cellspacing="0" border="0" width="400">
                  <tr>
                     <td>
                        <table bgcolor="#ffffff" cellpadding="3" cellspacing="0" border="0" width="100%">
                           <tr>
                              <td>
                                 <font size="-1">
                                    <%= LocaleUtils.getLocalizedString("global.community_text", JiveGlobals.getLocale()) %>
                                 </font>
                              </td>
                           </tr>
                        </table>
                     </td>
                  </tr>
               </table>
            </td>
         </tr>
         <tr>
            <td>
               <input type="radio" name="useDefaultWelcomeText"
                      value="false"<%= (("true".equals(useDefaultWelcomeText))?"":" checked") %> id="rb02">
            </td>
            <td>
               <font size="-1">
                  <label for="rb02">Enter a custom welcome message:</label>
               </font>
            </td>
         </tr>
         <tr>
            <td><font size="-1">&nbsp;</font></td>
            <td>
            <textarea rows="5" cols="35" name="communityDescription" wrap="virtual"
                      onfocus="this.form.useDefaultWelcomeText[1].checked=true;"
            ><%= (communityDescription != null) ? communityDescription : "" %></textarea>
            </td>
         </tr>
      </table>
   </ul>

   <% } else if ("headerandfooter".equals(mode)) { %>

   <font size="-1">
      <b>Global header</b>
   </font>
   <ul>
      <table bgcolor="<%= tblBorderColor %>" cellpadding="0" cellspacing="0" border="0">
         <tr>
            <td>
               <table bgcolor="<%= tblBorderColor %>" cellpadding="3" cellspacing="1" border="0" width="100%">
                  <tr bgcolor="#ffffff">
                     <td><font size="-1">Header Border Color:</font></td>
                     <td>
                        <table border="0" cellpadding="2" cellspacing="0">
                           <tr>
                              <td>
                                 <table cellpadding="0" cellspacing="1" border="1"
                                 >
                                    <td bgcolor="<%= (headerBorderColor!=null)?headerBorderColor:"" %>"
                                    ><a href="#" onclick="colorPicker(document.skinForm.headerBorderColor);"
                                    ><img src="images/blank.gif" width="15" height="15" border="0"></a></td>
                                 </table>
                              </td>
                              <td><input type="text" name="headerBorderColor" size="10" maxlength="20"
                                         value="<%= (headerBorderColor!=null)?headerBorderColor:"" %>"></td>
                           </tr>
                        </table>
                     </td>
                  </tr>
                  <tr bgcolor="#ffffff">
                     <td><font size="-1">Header Background Color:</font></td>
                     <td>
                        <table border="0" cellpadding="2" cellspacing="0">
                           <tr>
                              <td>
                                 <table cellpadding="0" cellspacing="1" border="1"
                                 >
                                    <td bgcolor="<%= (headerBgColor!=null)?headerBgColor:"" %>"
                                    ><a href="#" onclick="colorPicker(document.skinForm.headerBgColor);"
                                    ><img src="images/blank.gif" width="15" height="15" border="0"></a></td>
                                 </table>
                              </td>
                              <td><input type="text" name="headerBgColor" size="10" maxlength="20"
                                         value="<%= (headerBgColor!=null)?headerBgColor:"" %>"></td>
                           </tr>
                        </table>
                     </td>
                  </tr>
                  <tr bgcolor="#ffffff">
                     <td valign="top"><font size="-1">Header Logo:</font></td>
                     <td valign="top">
                        <table cellpadding="3" cellspacing="0" border="0" width="400">
                           <tr>
                              <td>
                                 <input type="radio" name="useDefaultHeaderImage"
                                        value="true"<%= ("true".equals(useDefaultHeaderImage)?" checked":"") %>
                                        id="rb01">
                              </td>
                              <td>
                                 <font size="-1">
                                    <label for="rb01">Use default header image (internationalized):</label>
                                 </font>
                              </td>
                           </tr>
                           <tr>
                              <td><font size="-1">&nbsp;</font></td>
                              <td>
                                 <font size="-1">
                                    <% Locale locale = JiveGlobals.getLocale();
                                       if ("en".equals(locale.getLanguage())) {
                                    %>
                                    <b>&lt;img src="images/logo.gif" width="242" height="38" border="0"&gt;</b>
                                    <% } else {
                                       // Display the locale-specific image URL:
                                       String localeCode = locale.toString();
                                    %>
                                    <b>&lt;img src="images/logo_<%= localeCode %>.gif" width="242" height="38"
                                       border="0"&gt;</b>
                                    <%
                                       }
                                    %>
                                 </font>
                              </td>
                           </tr>
                           <tr>
                              <td>
                                 <input type="radio" name="useDefaultHeaderImage"
                                        value="false"<%= ("true".equals(useDefaultHeaderImage)?"":" checked") %>
                                        id="rb02">
                              </td>
                              <td>
                                 <font size="-1">
                                    <label for="rb02">Use custom header image:</label>
                                 </font>
                              </td>
                           </tr>
                           <tr>
                              <td><font size="-1">&nbsp;</font></td>
                              <td>
                                 <input type="text" size="50" name="headerLogo" maxlength="150"
                                        value="<%= (headerLogo!=null)?headerLogo:"" %>"
                                        onfocus="this.form.useDefaultHeaderImage[1].checked=true;">
                              </td>
                           </tr>
                        </table>
                     </td>
                  </tr>
                  <tr bgcolor="#ffffff">
                     <td><font size="-1">Home URL:</font></td>
                     <td><input type="text" size="35" name="homeURL" maxlength="150"
                                value="<%= (homeURL!=null)?homeURL:"" %>">
                        <font size="-2">
                           <br>
                           Note, if this is left blank, the "Home" part of the breadcrumbs will not appear.
                           This URL can be relative or absolute.
                        </font>
                     </td>
                  </tr>
               </table>
            </td>
         </tr>
      </table>
   </ul>

   <% } else if ("features".equals(mode)) { %>

   <font size="-1">
      <b>Features</b>
   </font>

   <font size="-1">

      <p><b>Thread Mode:</b>
         <a href="#" onclick="helpwin('skin','thread_mode');return false;"
            title="Click for help"
         ><img src="images/help-16x16.gif" width="16" height="16" border="0" hspace="8"></a>

      <ul>
         <table cellpadding="3" cellspacing="0" border="0">
            <tr valign="top">
               <td>
                  <input type="radio" name="threadMode" value="flat"<%= ("flat".equals(threadMode))?" checked":"" %>
                         id="rb1">
               </td>
               <td>
                  <label for="rb1">Flat - Messages appear in a list.</label>
               </td>
            </tr>
            <tr valign="top">
               <td>
                  <input type="radio" name="threadMode"
                         value="threaded"<%= ("threaded".equals(threadMode))?" checked":"" %> id="rb2">
               </td>
               <td>
                  <label for="rb2">Threaded - Messages are indented.</label>
               </td>
            </tr>
            <tr>
               <td>
                  <input type="radio" name="threadMode" value="tree"<%= ("tree".equals(threadMode))?" checked":"" %>
                         id="rb22">
               </td>
               <td>
                  <label for="rb22">Tree - One message per page with the thread tree below it.</label>
               </td>
            </tr>
         </table>
      </ul>


      <p><b>Show "Last Post" Link:</b>
         <a href="#" onclick="helpwin('skin','last_post');return false;"
            title="Click for help"
         ><img src="images/help-16x16.gif" width="16" height="16" border="0" hspace="8"></a>

      <ul>
         <table cellpadding="3" cellspacing="0" border="0">
            <tr valign="top">
               <td>
                  <input type="radio" name="showLastPostLink"
                         value="true"<%= ("true".equals(showLastPostLink))?" checked":"" %> id="rb10">
               </td>
               <td>
                  <label for="rb10">Enabled - The last posted message will appear as a link in the index and forum
                     views.</label>
               </td>
            </tr>
            <tr valign="top">
               <td>
                  <input type="radio" name="showLastPostLink"
                         value="false"<%= ("false".equals(showLastPostLink))?" checked":"" %> id="rb20">
               </td>
               <td>
                  <label for="rb20">Disabled - The last posted message will <b>not</b> appear as a link in the forum and
                     thread views. This
                     might be better for smaller UIs.</label>
               </td>
            </tr>
         </table>
      </ul>


      <p><b>New Account Creation Enabled:</b>
         <a href="#" onclick="helpwin('skin','new_account');return false;"
            title="Click for help"
         ><img src="images/help-16x16.gif" width="16" height="16" border="0" hspace="8"></a>

      <ul>
         <table cellpadding="3" cellspacing="0" border="0">
            <tr valign="top">
               <td>
                  <input type="radio" name="newAccountCreationEnabled"
                         value="true"<%= ("true".equals(newAccountCreationEnabled))?" checked":"" %> id="rb7">
               </td>
               <td>
                  <label for="rb7">Enabled - Users can create new accounts through the default skin
                     (recommended).</label>
               </td>
            </tr>
            <tr valign="top">
               <td>
                  <input type="radio" name="newAccountCreationEnabled"
                         value="false"<%= ("false".equals(newAccountCreationEnabled))?" checked":"" %> id="rb8">
               </td>
               <td>
                  <label for="rb8">Disabled - Users can not create new accounts through the default skin. This might be
                     useful when using custom user systems.</label>
               </td>
            </tr>
         </table>
      </ul>


      <p><b>Track IPs:</b>
         <a href="#" onclick="helpwin('skin','track_ip');return false;"
            title="Click for help"
         ><img src="images/help-16x16.gif" width="16" height="16" border="0" hspace="8"></a>

      <ul>
         <table cellpadding="3" cellspacing="0" border="0">
            <tr valign="top">
               <td>
                  <input type="radio" name="trackIP" value="true"<%= ("true".equals(trackIP))?" checked":"" %> id="rb3">
               </td>
               <td>
                  <label for="rb3">On - User's IP is saved when they post a message.</label>
               </td>
            </tr>
            <tr valign="top">
               <td>
                  <input type="radio" name="trackIP" value="false"<%= ("false".equals(trackIP))?" checked":"" %>
                         id="rb4">
               </td>
               <td>
                  <label for="rb4">Off - User's IP is not saved.</label>
               </td>
            </tr>
         </table>
      </ul>


      <p><b>Allow Users To Choose Locale:</b>
         <a href="#" onclick="helpwin('skin','choose_locale');return false;"
            title="Click for help"
         ><img src="images/help-16x16.gif" width="16" height="16" border="0" hspace="8"></a>

      <ul>
         <table cellpadding="3" cellspacing="0" border="0">
            <tr valign="top">
               <td>
                  <input type="radio" name="usersChooseLocale"
                         value="true"<%= ("true".equals(usersChooseLocale))?" checked":"" %> id="rb30">
               </td>
               <td>
                  <label for="rb30">On - Users can change their locale via their settings page. This will
                     override the default <a href="locale.jsp">Jive locale</a>.</label>
               </td>
            </tr>
            <tr valign="top">
               <td>
                  <input type="radio" name="usersChooseLocale"
                         value="false"<%= ("false".equals(usersChooseLocale))?" checked":"" %> id="rb40">
               </td>
               <td>
                  <label for="rb40">Off - The user locale will be the default
                     <a href="locale.jsp">Jive locale</a>.</label>
               </td>
            </tr>
         </table>
      </ul>


      <%--

  <p><b>Allow Users To Choose their Thread Mode:</b>
      <a href="#" onclick="helpwin('skin','choose_threading');return false;"
      title="Click for help"
      ><img src="images/help-16x16.gif" width="16" height="16" border="0" hspace="8"></a>

      <ul>
      <table cellpadding="3" cellspacing="0" border="0">
      <tr valign="top">
           <td>
               <input type="radio" name="usersChooseThreadMode" value="true"<%= ("true".equals(usersChooseThreadMode))?" checked":"" %> id="rb303">
           </td>
           <td>
                <label for="rb303">On - Users are allowed to specify what thread interface they'd like to use.</label>
           </td>
      </tr>
      <tr valign="top">
          <td>
              <input type="radio" name="usersChooseThreadMode" value="false"<%= ("false".equals(usersChooseThreadMode))?" checked":"" %> id="rb404">
          </td>
          <td>
              <label for="rb404">Off - Users are not allowed to pick a thread interface - the system
      default is used (set above).</label>
          </td>
      </tr>
      </table>
      </ul>

      --%>

      <% if (Version.getEdition() != Version.Edition.LITE) { %>

      <p><b>Read Tracking:</b>
         <a href="#" onclick="helpwin('skin','read_tracking');return false;"
            title="Click for help"
         ><img src="images/help-16x16.gif" width="16" height="16" border="0" hspace="8"></a>

      <ul>
         <table cellpadding="3" cellspacing="0" border="0">
            <tr valign="top">
               <td>
                  <input type="radio" name="readTracker.enabled"
                         value="true"<%= ("true".equals(readTrackerEnabled))?" checked":"" %> id="rb511">
               </td>
               <td>
                  <label for="rb511">Enabled - Registered users can track unread messages.</label>
               </td>
            </tr>
            <tr valign="top">
               <td>
                  <input type="radio" name="readTracker.enabled"
                         value="false"<%= (!"true".equals(readTrackerEnabled))?" checked":"" %> id="rb611">
               </td>
               <td>
                  <label for="rb611">Disabled - New messages are determined by the time the user last visited the
                     site.</label>
               </td>
            </tr>
         </table>
      </ul>

      <% } %>

   </font>

   <% } %>

   <center>
      <% if (mode != null) { %>
      <input type="submit" name="save" value="Save Settings" onclick="this.form.formAction.value='save';">
      <input type="submit" name="cancel" value="Cancel" onclick="this.form.formAction.value='cancel';">
      <% } %>

      <input type="submit" value="Restore All Defaults"
             onclick="if (confirm('Warning, this restores ALL properties. Are you sure you want to proceed?')){this.form.formAction.value='defaults';}">

   </center>

</form>

<%@ include file="footer.jsp" %>



