<%
   /**
    *	$RCSfile: editPasswordReset.jsp,v $
    *	$Revision: 1.2 $
    *	$Date: 2002/11/22 21:45:45 $
    */
%>

<%@ page import="java.util.*,
                 java.text.*,
                 java.net.*,
                 java.sql.*,
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

   // get parameters
   boolean passwordNotify = ParamUtils.getBooleanParameter(request, "passwordNotify");
   String fromName = ParamUtils.getParameter(request, "fromName", true);
   String fromEmail = ParamUtils.getParameter(request, "fromEmail", true);
   String subject = ParamUtils.getParameter(request, "subject", true);
   String body = ParamUtils.getParameter(request, "body", true);
   boolean savePasswordSettings = ParamUtils.getBooleanParameter(request, "savePasswordSettings");
   boolean savePasswordProps = ParamUtils.getBooleanParameter(request, "savePasswordProps");

   // save the password settings if requested
   if (savePasswordSettings) {
      JiveGlobals.setJiveProperty("passwordReset.enabled", String.valueOf(passwordNotify));
      response.sendRedirect("editPasswordReset.jsp");
      return;
   }

   if (savePasswordProps) {
      if (fromName != null) {
         JiveGlobals.setJiveProperty("passwordReset.email.fromName", fromName);
      }
      if (fromEmail != null) {
         JiveGlobals.setJiveProperty("passwordReset.email.fromEmail", fromEmail);
      }
      if (subject != null) {
         JiveGlobals.setJiveProperty("passwordReset.email.subject", subject);
      }
      if (body != null) {
         JiveGlobals.setJiveProperty("passwordReset.email.body", body);
      }
      response.sendRedirect("editPasswordReset.jsp");
      return;
   }

   fromName = JiveGlobals.getJiveProperty("passwordReset.email.fromName");
   fromEmail = JiveGlobals.getJiveProperty("passwordReset.email.fromEmail");
   subject = JiveGlobals.getJiveProperty("passwordReset.email.subject");
   body = JiveGlobals.getJiveProperty("passwordReset.email.body");

   boolean isPasswordResetEnabled =
      Boolean.valueOf(JiveGlobals.getJiveProperty("passwordReset.enabled")).booleanValue();
%>

<%@ include file="header.jsp" %>

<p>

      <%  // Title of this page and breadcrumbs
    String title = "Password Resetting";
    String[][] breadcrumbs = {
        {"Main", "main.jsp"},
        {title, "editPasswordReset.jsp"}
    };
%>
   <%@ include file="title.jsp" %>

   <font size="-1">
      Password resetting gives users a way to securely reset their password if they
      forget it. Use the form below to enable or disable password resetting.
   </font>

<p>

<form action="editPasswordReset.jsp" name="f">
   <input type="hidden" name="savePasswordSettings" value="true">
   <font size="-1"><b>Password Resetting Status</b></font>
   <ul>
      <table cellpadding="2" cellspacing="0" border="0">
         <tr>
            <td width="1%"><input type="radio" name="passwordNotify"
                                  value="true"<%= (isPasswordResetEnabled?" checked":"") %> id="rb01"></td>
            <td><font size="-1"><label for="rb01">Password Resetting Enabled</label></font></td>
         </tr>
         <tr>
            <td width="1%"><input type="radio" name="passwordNotify"
                                  value="false"<%= (!isPasswordResetEnabled?" checked":"") %> id="rb02"></td>
            <td><font size="-1"><label for="rb02">Password Resetting Disabled</label></font></td>
         </tr>
         <tr>
            <td colspan="2">
               <br><input type="submit" value="Save Settings">
            </td>
         </tr>
      </table>
   </ul>
</form>

<form action="editPasswordReset.jsp" name="f">
   <input type="hidden" name="savePasswordProps" value="true">
   <font size="-1"><b>Password Reset Email Properties</b></font>
   <ul>
      <font size="-1">
         A confirmation email will be sent to the user when they ask to have their
         password reset. You can customize the fields of the email using
         the following tokens. Each token will be replaced with the appropriate
         value when the email is sent.
         <br>
      </font>
      <font color="#006600">
         <tt>
            {userID}
            {username}
            {name}
            {email}
            {token}
            {requestIP}
            {jiveURL}
         </tt>
      </font>
      <p>
      <table cellpadding="2" cellspacing="0" border="0">
         <tr>
            <td nowrap><font size="-1">From Name:</font></td>
            <td><input type="text" name="fromName" size="30" value="<%= ((fromName!=null)?fromName:"") %>"></td>
         </tr>
         <tr>
            <td nowrap><font size="-1">From Email:</font></td>
            <td><input type="text" name="fromEmail" size="30" value="<%= ((fromEmail!=null)?fromEmail:"") %>"></td>
         </tr>
         <tr>
            <td nowrap><font size="-1">Subject:</font></td>
            <td><input type="text" name="subject" size="40"
                       value="<%= ((subject!=null)?StringUtils.replace(subject,"\"","&quot;"):"") %>"></td>
         </tr>
         <tr>
            <td valign="top" nowrap><font size="-1">Body of email:</font></td>
            <td>
               <textarea name="body" cols="55" rows="6" wrap="virtual"><%= ((body != null) ? body : "") %></textarea>
            </td>
         </tr>
         <tr>
            <td>&nbsp;</td>
            <td><br><input type="submit" value="Save Settings"></td>
         </tr>
      </table>
   </ul>
</form>

<%@ include file="footer.jsp" %>
