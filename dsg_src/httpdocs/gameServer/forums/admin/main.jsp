<%
   /**
    *	$RCSfile: main.jsp,v $
    *	$Revision: 1.4 $
    *	$Date: 2002/10/31 04:23:28 $
    */
%>

<%@ page import="java.util.*,
                 com.jivesoftware.forum.*,
                 com.jivesoftware.forum.util.*" %>

<%@ include file="global.jsp" %>

<%@ include file="header.jsp" %>

<% // Title of this page and breadcrumbs
   String title = "Jive Forums 3 Admin";
   String[][] breadcrumbs = null;
%>
<%@ include file="title.jsp" %>

<% String jiveEdition = null;
   if (Version.getEdition() == Version.Edition.LITE) {
      jiveEdition = "Jive Forums Lite";
   } else if (Version.getEdition() == Version.Edition.PROFESSIONAL) {
      jiveEdition = "Jive Forums Professional";
   } else if (Version.getEdition() == Version.Edition.ENTERPRISE) {
      jiveEdition = "Jive Forums Enterprise";
   }

   // Load license details:
   boolean isCommercial = false;
   boolean isNonCommercial = false;
   boolean isEvaluation = false;
   int exprDaysFromNow = 1;
   boolean validLicense = false;
   try {
      LicenseManager.validateLicense(jiveEdition, "2.0");
      validLicense = true;
   } catch (Exception e) {
      e.printStackTrace();
   }
   if (validLicense) {
      License.LicenseType licenseType = LicenseManager.getLicenseType();
      isCommercial = (licenseType == License.LicenseType.COMMERCIAL);
      isNonCommercial = (licenseType == License.LicenseType.NON_COMMERCIAL);
      isEvaluation = (!isCommercial && !isNonCommercial);
      if (isEvaluation) {
         Date exprDate = LicenseManager.getExpiresDate();
         if (exprDate != null) {
            long expires = exprDate.getTime() - System.currentTimeMillis();
            exprDaysFromNow = (int) Math.ceil((expires / JiveConstants.DAY)) + 1;
         } else {
            exprDaysFromNow = -1;
         }
      }
   }
%>


<b>Welcome to the Jive Forums Administration tool.</b>

<br><br>

<ul>
   <table bgcolor="#cccccc" cellpadding="1" cellspacing="0" border="0">
      <tr>
         <td>
            <table bgcolor="#ffffff" cellpadding="3" cellspacing="0" border="0">
               <tr bgcolor="#eeeeee">
                  <td>
                     <b>Version:</b>
                  </td>
                  <td>&nbsp;</td>
                  <td>
                     <b><%= jiveEdition %> <%= Version.getVersionNumber() %>
                     </b>
                  </td>
               </tr>
               <tr>
                  <td>
                     <font color="#444444">
                        &nbsp;
                        Appserver:
                     </font>
                  </td>
                  <td>&nbsp;</td>
                  <td>
                     <font color="#444444">
                        <%= application.getServerInfo() %>
                     </font>
                  </td>
               </tr>
               <% if (isSystemAdmin) { %>
               <tr>
                  <td>
                     <font color="#444444">
                        &nbsp;
                        jiveHome Directory:
                     </font>
                  </td>
                  <td>&nbsp;</td>
                  <td>
                     <font color="#444444">
                        <%= JiveGlobals.getJiveHome() %>
                     </font>
                  </td>
               </tr>
               <% } %>
               <tr>
                  <td valign="top">
                     <font color="#444444">
                        &nbsp;
                        License:
                     </font>
                  </td>
                  <td>&nbsp;</td>
                  <td>
                     <table cellpadding="0" cellspacing="0" border="0" width="100%">
                        <tr>
                           <td width="1%" valign="top">
                              <% if (isCommercial || isNonCommercial) { %>
                              <img src="images/check.gif" width="13" height="13" border="0" hspace="4">
                              <% } else { %>
                              <img src="images/x.gif" width="13" height="13" border="0" hspace="4">
                              <% } %>
                           </td>
                           <td width="99%">
                              <font color="#444444">
                                 <% if (isCommercial) { %>
                                 You are licensed for commercial deployment.

                                 <% } else if (isNonCommercial) { %>
                                 You are licensed for <b>non-commercial</b> deployment.

                                 <% } else if (isEvaluation) { %>

                                 <% if (exprDaysFromNow == -1) { %>
                                 You are licensed for <b>evaluation</b> purposes only.
                                 <% } else { %>
                                 You are licensed for <b>evaluation</b> purposes. Your
                                 evalution period will end <%= exprDaysFromNow %>
                                 day<%= (exprDaysFromNow == 1 ? "" : "s") %> from now.
                                 <% } %>
                                 <% } else { %>
                                 Your license is invalid.

                                 <% } %>
                                 <br>
                                 <a href="license.jsp">More Details</a>
                              </font>
                           </td>
                        </tr>
                     </table>
                  </td>
               </tr>
            </table>
         </td>
      </tr>
   </table>
</ul>

<br>

<% // Only show these links if we're a system admin:
   if (isSystemAdmin) {
%>

<font size="-1">
   <b>Common Administrative Tasks</b>
</font>
<ul>
   <table cellpadding="5" cellspacing="0" border="0">
      <tr>
         <td valign="top">
            <a href="forums.jsp"
            ><img src="images/go_to.gif" width="13" height="10" border="0" vspace="3"></a>
         </td>
         <td onmouseover="this.bgColor='#f1faff';this.style.cursor='hand';" onmouseout="this.bgColor='#ffffff';"
             onclick="location.href='forums.jsp';">
            <a href="forums.jsp"
            ><b>Categories &amp; Forums Summary</b></a>
            <br>
            <font color="#444444">
               Create, manage or delete categories and forums.
            </font>
         </td>
         <td valign="top">
            <a href="users.jsp"
            ><img src="images/go_to.gif" width="13" height="10" border="0" vspace="3"></a>
         </td>
         <td onmouseover="this.bgColor='#f1faff';this.style.cursor='hand';" onmouseout="this.bgColor='#ffffff';"
             onclick="location.href='users.jsp';">
            <a href="users.jsp"
            ><b>User Summary</b></a>
            <br>
            <font color="#444444">
               Create, manage or delete users.
            </font>
         </td>
      </tr>
      <tr>
         <td valign="top">
            <a href="filters.jsp"
            ><img src="images/go_to.gif" width="13" height="10" border="0" vspace="3"></a>
         </td>
         <td onmouseover="this.bgColor='#f1faff';this.style.cursor='hand';" onmouseout="this.bgColor='#ffffff';"
             onclick="location.href='filters.jsp';">
            <a href="filters.jsp"
            ><b>Global Message Filters</b></a>
            <br>
            <font color="#444444">
               Control how message content is formatted.
            </font>
         </td>
         <td valign="top">
            <a href="perms.jsp?mode=<%= FORUM_MODE %>&permGroup=<%= CONTENT_GROUP %>"
            ><img src="images/go_to.gif" width="13" height="10" border="0" vspace="3"></a>
         </td>
         <td onmouseover="this.bgColor='#f1faff';this.style.cursor='hand';" onmouseout="this.bgColor='#ffffff';"
             onclick="location.href='perms.jsp?mode=<%= FORUM_MODE %>&permGroup=<%= CONTENT_GROUP %>';">
            <a href="perms.jsp?mode=<%= FORUM_MODE %>&permGroup=<%= CONTENT_GROUP %>"
            ><b>Global Permissions</b></a>
            <br>
            <font color="#444444">
               Set global access policies.
            </font>
         </td>
      </tr>
      <tr>
         <td valign="top">
            <a href="cache.jsp"
            ><img src="images/go_to.gif" width="13" height="10" border="0" vspace="3"></a>
         </td>
         <td onmouseover="this.bgColor='#f1faff';this.style.cursor='hand';" onmouseout="this.bgColor='#ffffff';"
             onclick="location.href='cache.jsp';">
            <a href="cache.jsp"
            ><b>Cache Settings</b></a>
            <br>
            <font color="#444444">
               Tune and monitor performance.
            </font>
         </td>
         <td valign="top">
            <a href="locale.jsp"
            ><img src="images/go_to.gif" width="13" height="10" border="0" vspace="3"></a>
         </td>
         <td onmouseover="this.bgColor='#f1faff';this.style.cursor='hand';" onmouseout="this.bgColor='#ffffff';"
             onclick="location.href='locale.jsp';">
            <a href="locale.jsp"
            ><b>Locale Settings</b></a>
            <br>
            <font color="#444444">
               Modify international preferences.
            </font>
         </td>
      </tr>
   </table>
</ul>

<% } %>

<b>Help &amp; Documentation</b>
<ul>
   Inline documentation is located throughout the admin tool. Click on the
   help icon <img src="images/help-16x16.gif" width="16" height="16" border="0">
   to launch context-specific help.
</ul>

<p>

   <%@ include file="footer.jsp" %>
