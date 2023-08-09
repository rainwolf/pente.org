<%
   /**
    *	$RCSfile: license.jsp,v $
    *	$Revision: 1.4.4.1 $
    *	$Date: 2003/02/17 16:16:19 $
    */
%>

<%@ page import="java.io.*,
                 java.util.*,
                 com.jivesoftware.forum.*,
                 com.jivesoftware.forum.util.*"
%>

<%@ include file="global.jsp" %>

<% // Additional security check - only sys admins should be able to see this page:
   if (!isSystemAdmin) {
      throw new UnauthorizedException("You don't have admin privileges to perform this operation.");
   }
%>

<%@ include file="header.jsp" %>

<% String jiveEdition = null;
   if (Version.getEdition() == Version.Edition.LITE) {
      jiveEdition = "Jive Forums Lite";
   } else if (Version.getEdition() == Version.Edition.PROFESSIONAL) {
      jiveEdition = "Jive Forums Professional";
   } else if (Version.getEdition() == Version.Edition.ENTERPRISE) {
      jiveEdition = "Jive Forums Enterprise";
   }
%>

<table cellpadding="0" cellspacing="0" border="0" width="100%">
   <tr>
      <td>
         <b><%= jiveEdition %> - Admin</b>
      </td>
   </tr>
   <tr>
      <td>
         <hr size="0" width="100%">
      </td>
   </tr>
</table>

<font size="-1">
   Below are the details of your Jive Forums license.
   <% if (isSystemAdmin) { %>
   It is installed at:
   <tt><%= JiveGlobals.getJiveHome() %><%= File.separator %>jive.license</tt>
   <% } %>
</font>
<p>

   <font size="-1"><b>License Details</b>
      <ul>
         <font size="-1">
            <% boolean validLicense = false;
               Exception le = null;
               try {
                  LicenseManager.validateLicense(jiveEdition, "2.0");
                  validLicense = true;
               } catch (LicenseException e) {
                  le = e;
               }

               if (validLicense) {
                  License.LicenseType licenseType = LicenseManager.getLicenseType();
                  boolean isCommercial = (licenseType == License.LicenseType.COMMERCIAL);
                  boolean isNonCommercial = (licenseType == License.LicenseType.NON_COMMERCIAL);
                  boolean isEvaluation = (!isCommercial && !isNonCommercial);
            %>
            <% if (isCommercial) { %>

            This copy of <%= jiveEdition %> is licensed for commercial deployment.

            <% } else if (isNonCommercial) { %>

            This copy of <%= jiveEdition %> is licensed for non-commercial deployment. To
            purchase a commercial license, please visit
            <a href="http://www.jivesoftware.com/store/" target="_blank">http://www.jivesoftware.com/store/</a>.

            <% }
            // is evaluation
            else { %>

            This is an evaluation copy of <%= jiveEdition %> and is not licensed for deployment.
            Before deploying for commercial or non-commercial use,
            you must obtain a license at
            <a href="http://www.jivesoftware.com/store/" target="_blank">http://www.jivesoftware.com/store/</a>.
            <p>
                  <%      Date exprDate = LicenseManager.getExpiresDate();
            if (exprDate != null) {
                long expires = exprDate.getTime() - System.currentTimeMillis();
                int daysFromNow = (int)Math.ceil((double)(expires/JiveConstants.DAY))+1;
    %>
               <font color="#ff0000">
                  This evaulation license expires on
                  <%= JiveGlobals.formatDate(exprDate) %>, <%= daysFromNow %> day<%= (daysFromNow == 1) ? "" : "s" %>
                  from now.
               </font>
                  <%      } %>

                  <%  } %>
         </font>

         <p>
         <table bgcolor="<%= tblBorderColor %>" cellpadding="0" cellspacing="0" border="0">
            <td>
               <table cellpadding="4" cellspacing="1" border="0" width="100%">
                  <% if (!isEvaluation) { %>
                  <tr bgcolor="#ffffff">
                     <td><font size="-1">License ID:</font></td>
                     <td><font size="-1"><%= LicenseManager.getLicenseID() %>
                     </font></td>
                  </tr>
                  <% } %>
                  <tr bgcolor="#ffffff">
                     <td><font size="-1">Product:</font></td>
                     <td><font size="-1"><%= LicenseManager.getProduct() %>
                     </font></td>
                  </tr>
                  <tr bgcolor="#ffffff">
                     <td><font size="-1">Version:</font></td>
                     <td><font size="-1"><%= LicenseManager.getVersion() %>
                     </font></td>
                  </tr>
                  <tr bgcolor="#ffffff">
                     <td><font size="-1">License Type:</font></td>
                     <td><font size="-1"><%= LicenseManager.getLicenseType() %>
                     </font></td>
                  </tr>
                  <% if (!isEvaluation) { %>
                  <tr bgcolor="#ffffff">
                     <td><font size="-1">Copies:</font></td>
                     <td><font size="-1"><%= LicenseManager.getNumCopies() %>
                     </font></td>
                  </tr>
                  <% } %>
                  <tr bgcolor="#ffffff">
                     <td><font size="-1">License Created:</font></td>
                     <td><font size="-1"><%= JiveGlobals.formatDate(LicenseManager.getCreationDate()) %>
                     </font></td>
                  </tr>
                  <% if (!isEvaluation) {
                     Date exprDate = LicenseManager.getExpiresDate();
                     if (exprDate != null) { %>
                  <tr bgcolor="#ffffff">
                     <td><font size="-1">License Expires:</font></td>
                     <td><font size="-1" color="#ff0000"><%= JiveGlobals.formatDate(exprDate) %>
                     </font></td>
                  </tr>
                  <% } %>
                  <tr bgcolor="#ffffff">
                     <td><font size="-1">Licensed To:</font></td>
                     <% String name = LicenseManager.getName();
                        if (name == null) {
                           name = "&nbsp;";
                        }
                     %>
                     <td><font size="-1"><%= name %>
                     </font></td>
                  </tr>
                  <% String company = LicenseManager.getCompany();
                     if (company != null && "".equals(company)) {
                  %>
                  <tr bgcolor="#ffffff">
                     <td><font size="-1">Company/Organization:</font></td>
                     <td><font size="-1"><%= company %>
                     </font></td>
                  </tr>
                  <% } %>
                  <% int clusterMembers = LicenseManager.getNumClusterMembers();
                     if (clusterMembers > 0) {
                  %>
                  <tr bgcolor="#ffffff">
                     <td><font size="-1">Cluster Members Allowed:</font></td>
                     <td><font size="-1"><%= clusterMembers %>
                     </font></td>
                  </tr>
                  <% } %>
                  <tr bgcolor="#ffffff">
                     <td><font size="-1">URL:</font></td>
                     <% String url = LicenseManager.getURL();
                        if (url == null || "".equals(url)) {
                           url = "<i>Unspecified or Internal Use</i>";
                        }
                     %>
                     <td><font size="-1"><%= url %>
                     </font></td>
                  </tr>
                  <% } // end !isEvaulation %>
               </table>
            </td>
            </tr>
         </table>

         <% } else { %>

         <font color="#ff0000">
            License not valid: <%= le.getMessage() %>
         </font>

         <% } %>

      </ul>
   </font>

<center>
   <form action="main.jsp">
      <input type="submit" value="Go Back">
   </form>
</center>

<%@ include file="footer.jsp" %>
