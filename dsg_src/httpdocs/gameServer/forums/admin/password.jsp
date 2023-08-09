<%
   /**
    *	$RCSfile: password.jsp,v $
    *	$Revision: 1.2 $
    *	$Date: 2002/10/02 01:20:37 $
    */
%>

<%@ page
   import="java.util.*,
           com.jivesoftware.forum.*,
           com.jivesoftware.forum.util.*,
           com.jivesoftware.util.ParamUtils"
   errorPage="error.jsp"
%>

<%@ include file="global.jsp" %>

<% ////////////////////
   // Security check

   // make sure the user is authorized to create forums::

%>

<% ////////////////////
   // get parameters

   boolean doChange = ParamUtils.getBooleanParameter(request, "doChange");
   String adminPassword = ParamUtils.getParameter(request, "adminPassword");
   String username = ParamUtils.getParameter(request, "username");
   String newPassword = ParamUtils.getParameter(request, "newPassword");
   String confirmNew = ParamUtils.getParameter(request, "confirmNew");
%>

<% //////////////////
   // error variables

   boolean errorAdminPassword = (adminPassword == null);
   boolean errorUsername = (username == null);
   boolean errorNewPassword = (newPassword == null);
   boolean errorConfirmNew = (confirmNew == null);
   boolean errorNewPasswordsNotEqual = true;
   if (!errorNewPassword && !errorConfirmNew) {
      if (newPassword.equals(confirmNew)) {
         errorNewPasswordsNotEqual = false;
      }
   }
   boolean errors = (errorAdminPassword
      || errorUsername
      || errorNewPassword
      || errorConfirmNew
      || errorNewPasswordsNotEqual
   );
%>

<% ////////////////////
   // set the password if there are no errors

   UserManager manager = forumFactory.getUserManager();
   boolean errorUserNotExist = false;
   if (!errors && doChange) {

      // reauthenticate the admin:
      try {
         User adminUser = manager.getUser(authToken.getUserID());
         //AuthorizationFactory authFactory = AuthorizationFactory.getInstance();
         authToken = AuthFactory.getAuthToken(adminUser.getUsername(), adminPassword);
         if (authToken == null) {
            errorAdminPassword = true;
         }
      } catch (Exception e) {
         errorAdminPassword = true;
      }

      // try to load specified user:
      if (!errorAdminPassword) {
         try {
            User user = manager.getUser(username);
            user.setPassword(newPassword);
            response.sendRedirect(
               response.encodeRedirectURL("users.jsp?msg=Password changed successfully")
            );
         } catch (UserNotFoundException unfe) {
            errorUserNotExist = true;
         }
      }
   }
%>

<% //////////////////
   // recheck for errors

   errors = (errors || errorUserNotExist || errorAdminPassword);
%>

<html>
<head>
   <title></title>
   <link rel="stylesheet" href="style/global.css">
</head>

<body bgcolor="#ffffff" text="#000000" link="#0000ff" vlink="#800080" alink="#ff0000">

<% ///////////////////////
   // pageTitleInfo variable (used by include/pageTitle.jsp)
   String[] pageTitleInfo = {"System Settings", "Change Passwords"};
%>
<% ///////////////////
   // pageTitle include
%>

<p>

   Change Passwords

<p>

   <i>(All fields are required)</i>

<p>

<form action="password.jsp" method="post">
   <input type="hidden" name="doChange" value="true">

   <table bgcolor="#666666" cellpadding="0" cellspacing="0" border="0" width="80%" align="center">
      <td>
         <table bgcolor="#666666" cellpadding="3" cellspacing="1" border="0" width="100%">
            <tr bgcolor="#ffffff">
               <td>
                  <%= (doChange && errorAdminPassword) ? "<font color=\"#ff0000\">(error)</font><br>" : "" %>
                  Please re-enter your<br>admin password
               </td>
               <td><input type="password" name="adminPassword" size="30"></td>
            </tr>
            <tr bgcolor="#ffffff">
               <td colspan="2">&nbsp;</td>
            </tr>
            <tr bgcolor="#ffffff">
               <td>
                  <%= (doChange && (errorUsername || errorUserNotExist)) ? "<font color=\"#ff0000\">(error)</font><br>" : "" %>
                  Username of person to change:
               </td>
               <td><input type="text" name="username" size="30"></td>
            </tr>
            <tr bgcolor="#ffffff">
               <td>
                  <%= (doChange && (errorNewPassword || errorNewPasswordsNotEqual)) ? "<font color=\"#ff0000\">(Error: no password entered or passwords not equal)</font><br>" : "" %>
                  New password
               </td>
               <td><input type="password" name="newPassword" size="30"></td>
            </tr>
            <tr bgcolor="#ffffff">
               <td>
                  <%= (doChange && (errorConfirmNew || errorNewPasswordsNotEqual)) ? "<font color=\"#ff0000\">(Error: no password entered or passwords not equal)</font><br>" : "" %>
                  Confirm new password
               </td>
               <td><input type="password" name="confirmNew" size="30"></td>
            </tr>
         </table>
      </td>
   </table>

   <p>

   <center>
      <input type="submit" value="Change Password">
   </center>

</form>

</body>
</html>

