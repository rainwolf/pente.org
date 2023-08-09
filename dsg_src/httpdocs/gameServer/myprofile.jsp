<%@ page import="java.util.*,
                 org.pente.gameServer.core.*,
                 com.jivesoftware.forum.*,
                 com.jivesoftware.util.*" %>

<%@ page import="com.jivesoftware.forum.action.SettingsAction,
                 java.util.Locale" %>

<%
   DSGPlayerData dsgPlayerData = (DSGPlayerData) request.getAttribute("dsgPlayerData");
   if (dsgPlayerData == null) {
      throw new Exception("Illegal access attempted");
   }

   String changeProfileError = (String) request.getAttribute("changeProfileError");
   String changeProfileSuccess = (String) request.getAttribute("changeProfileSuccess");
%>

<% pageContext.setAttribute("title", "My Profile"); %>
<% pageContext.setAttribute("current", "My Profile"); %>
<%@ include file="begin.jsp" %>
<link rel="stylesheet" type="text/css" href="<%= request.getContextPath() %>/gameServer/forums/style.jsp"/>


<% String selectedTab = "My Info"; %>
<%@ include file="tabs.jsp" %>


<form enctype="multipart/form-data"
      name="change_profile_form"
      method="post"
      action="/gameServer/myprofile/myInfo">

   <table width="100%" border="0" colspacing="0" colpadding="0">


      <tr>
         <td>
            <a name="myInfo"><h3>My Info</h3></a>
         </td>
      </tr>
      <tr>
         <td>
            <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
               <a href="/help/helpWindow.jsp?file=privacyPolicy">Privacy Policy</a>
               concerning your email address.<br>
               <br>
               Your password must contain only letters, digits and the underscore character
               and must be 5-16 characters. Email addresses must be in the format user@host.com.<br>
               <br>
               * Required field<br>
            </font>
         </td>
      </tr>

      <tr>
         <td>&nbsp;</td>
      </tr>

      <% if (changeProfileError != null) { %>

      <tr>
         <td>
            <b><font face="Verdana, Arial, Helvetica, sans-serif" size="2" color="<%= textColor2 %>">
               Changing profile failed: <%= changeProfileError %>
            </b></font>
         </td>
      </tr>

      <% } else if (changeProfileSuccess != null) { %>

      <tr>
         <td>
            <font face="Verdana, Arial, Helvetica, sans-serif" size="2" color="<%= textColor2 %>">
               <b><%= changeProfileSuccess %>
               </b>
            </font>
         </td>
      </tr>

      <%
         }
      %>

      <tr>
         <td>

            <table border="0" colspacing="1" colpadding="1">
               <tr>
                  <td width="160">
                     <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
                        Name*
                     </font>
                  </td>
                  <td>
                     <font face="Verdana, Arial, Helvetica, sans-serif" size="2" color="<%= textColor2 %>"><b>
                        <%= dsgPlayerData.getName() %>
                     </b></font>
                  </td>
               </tr>
               <tr>
                  <td>
                     <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
                        New Password
                     </font>
                  </td>
                  <td>
                     <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
                        <input type="password" name="changePassword" size="16" maxlength="16">
                     </font>
                  </td>
               </tr>
               <tr>
                  <td>
                     <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
                        Re-enter New Password
                     </font>
                  </td>
                  <td>
                     <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
                        <input type="password" name="changePasswordConfirm" size="16" maxlength="16">
                     </font>
                  </td>
               </tr>
               <% if (!dsgPlayerData.getEmailValid()) { %>
               <tr>
                  <td colspan="2">
                     <font face="Verdana, Arial, Helvetica, sans-serif" size="2"
                           color="<%= textColor2 %>">
                        <b>Pente.org has your email address marked as invalid. This means the last time an
                           email was sent to you it was returned with errors. Please correct
                           the address and click "Save Changes".
                        </b></font>
                  </td>
               </tr>
               <% } %>
               <tr>
                  <td>
                     <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
                        Email*
                     </font>
                  </td>
                  <td>
                     <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
                        <input type="text" name="changeEmail" size="30" maxlength="100"
                               value="<%= dsgPlayerData.getEmail() %>">
                     </font>
                  </td>
               </tr>
               <tr>
                  <td>
                     <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
                        Display email on profile
                     </font>
                  </td>
                  <td>
                     <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
                        <% String checked = dsgPlayerData.getEmailVisible() ? "checked" : ""; %>
                        <input type="checkbox" name="changeEmailVisible" <%= checked %> value="Y">
                     </font>
                  </td>
               </tr>
               <tr>
                  <td>
                     <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
                        Location
                     </font>
                  </td>
                  <td>
                     <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
                        <% String location = dsgPlayerData.getLocation() != null ? dsgPlayerData.getLocation() : ""; %>
                        <input type="text" name="changeLocation" size="30" maxlength="50" value="<%= location %>">
                     </font>
                  </td>
               </tr>
               <tr>
                  <td>
                     <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
                        Timezone
                     </font>
                  </td>
                  <td>
                     <select size="1" name="timezone">
                        <% String[][] timeZones = LocaleUtils.getTimeZoneList();
                           String timeZoneID = dsgPlayerData.getTimezone();
                           for (int i = 0; i < timeZones.length; i++) {
                              boolean selected = timeZones[i][0].equals(timeZoneID);
                        %>
                        <option value="<%= timeZones[i][0] %>"<%= (selected?" selected":"") %>><%= timeZones[i][1] %>

                              <%	} %>
                     </select>
                  </td>
               </tr>
               <tr>
                  <td>
                     <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
                        Sex
                     </font>
                  </td>
                  <td>
                     <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
                        <select name="changeSex">
                              <% char sexValues[] = { DSGPlayerData.UNKNOWN, DSGPlayerData.FEMALE, DSGPlayerData.MALE };
            String sexNames[] = { "", "Female", "Male" };
            for (int i = 0; i < sexValues.length; i++) {
                out.print("<option value=\"" + sexValues[i] + "\"");
                if (dsgPlayerData.getSex() == sexValues[i]) {
                    out.print(" selected");
                }
                out.println(">" + sexNames[i] + "</option>");
            }
         %>
                     </font>
                  </td>
               </tr>
               <tr>
                  <td>
                     <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
                        Age
                     </font>
                  </td>
                  <td>
                     <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
                        <% String age = dsgPlayerData.getAge() == 0 ? "" : Integer.toString(dsgPlayerData.getAge()); %>
                        <input type="text" name="changeAge" size="5" maxlength="3" value="<%= age %>">
                     </font>
                  </td>
               </tr>
               <tr>
                  <td>
                     <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
                        Home page
                     </font>
                  </td>
                  <td>
                     <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
                        <% String homepage = dsgPlayerData.getHomepage() != null ? dsgPlayerData.getHomepage() : ""; %>
                        <input type="text" name="changeHomepage" size="30" maxlength="100" value="<%= homepage %>">
                     </font>
                  </td>
               </tr>

               <tr>
                  <td>&nbsp;</td>
                  <td>
                     <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
                        <input type="submit" value="Save changes">
                     </font>
                  </td>
               </tr>
            </table>
         </td>
      </tr>

      <tr>
         <td>&nbsp;</td>
      </tr>

   </table>
</form>

<%@ include file="end.jsp" %>