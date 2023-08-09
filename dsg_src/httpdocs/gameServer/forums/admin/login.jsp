<%--
  - $RCSfile: login.jsp,v $
  - $Revision: 1.7.4.3 $
  - $Date: 2003/03/28 18:12:00 $
  -
  - Copyright (C) 2002-2003 Jive Software. All rights reserved.
  -
  - This software is the proprietary information of Jive Software. Use is subject to license terms.
--%>

<%@ page import="java.util.*,
                 com.jivesoftware.forum.*,
                 com.jivesoftware.forum.util.*,
                 com.jivesoftware.base.*,
                 com.jivesoftware.util.*"
         errorPage="error.jsp"
%>

<%@ include file="permMethods.jsp" %>

<%! // Global methods/vars, etc

   // Method to determine if the perms associated with the given auth token allow
   // any admin privledges.
   private boolean isAdmin(AuthToken authToken) {

      boolean isAdmin = false;
      ForumFactory forumFactory = ForumFactory.getInstance(authToken);
      isAdmin = forumFactory.isAuthorized(Permissions.SYSTEM_ADMIN);
      isAdmin = isAdmin || hasCategoryWithPermission(forumFactory, ForumPermissions.FORUM_CATEGORY_ADMIN);
      isAdmin = isAdmin || hasForumWithPermission(forumFactory, ForumPermissions.FORUM_ADMIN);
      isAdmin = isAdmin || forumFactory.isAuthorized(Permissions.USER_ADMIN);
      isAdmin = isAdmin || hasGroupWithPermission(forumFactory, Permissions.GROUP_ADMIN);
      isAdmin = isAdmin || hasForumWithPermission(forumFactory, ForumPermissions.MODERATOR);

      return isAdmin;
   }
%>

<% // get parameters
   String username = ParamUtils.getParameter(request, "username");
   String password = ParamUtils.getParameter(request, "password");
   boolean doLogin = request.getParameter("login") != null;

   // The user auth token:
   AuthToken authToken = null;

   boolean errors = false;

   // See if we can try an alternative login/auth
   if ("true".equals(JiveGlobals.getJiveProperty("admin.tryAlternativeLogin"))) {
      // Attempt to get the auth token
      try {
         authToken = AuthFactory.getAuthToken(request, response);
         if (isAdmin(authToken)) {
            // put the auth token in the session (for the admin tool)
            session.setAttribute("jive.admin.authToken", authToken);
            // Redirect to the index page:
            response.sendRedirect("index.jsp");
            return;
         }
      } catch (UnauthorizedException ue) {
         // we ignore this exception - not necessary to take note of it because if
         // this type of login fails, we'll just show the normal login screen.
      }
   }


   if (doLogin) {
      try {
         authToken = AuthFactory.getAuthToken(username, password);
      } catch (UnauthorizedException ue) {
         errors = true;
      }
   }

   if (!errors && doLogin) {
      if (isAdmin(authToken)) {
         session.setAttribute("jive.admin.authToken", authToken);
         response.sendRedirect("index.jsp");
         return;
      } else {
         errors = true;
      }
   }
%>

<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN">

<html>
<head>
   <title>Jive Forums 3 Admin Login</title>
   <script language="JavaScript" type="text/javascript">
      <!--
      // break out of frames
      if (self.parent.frames.length != 0) {
         self.parent.location = document.location;
      }

      function updateFields(el) {
         if (el.checked) {
            document.loginForm.username.disabled = true;
            document.loginForm.password.disabled = true;
         } else {
            document.loginForm.username.disabled = false;
            document.loginForm.password.disabled = false;
            document.loginForm.username.focus();
         }
      }

      //-->
   </script>
   <link rel="stylesheet" href="style/global.css" type="text/css">
   <style type="text/css">
       .jive-login-form TH {
           background-color: #eee;
           text-align: left;
           border-top: 1px #bbb solid;
           border-bottom: 1px #bbb solid;
       }

       .jive-login-form .jive-login-label {
           font-size: 0.8em;
       }

       .jive-login-form .jive-footer {
           font-size: 0.8em;
           font-weight: bold;
       }
   </style>
</head>

<body>

<form action="login.jsp" name="loginForm" method="post">
   <input type="hidden" name="login" value="true">

   <br><br><br><br>

   <table width="100%" border="0" cellspacing="0" cellpadding="0">
      <tr>
         <td width="49%"><br></td>
         <td width="2%">
            <noscript>
               <table border="0" cellspacing="0" cellpadding="0">
                  <td>
            <span class="jive-error-text">
            <b>Error:</b> You don't have JavaScript enabled. This tool uses JavaScript
            and much of it will not work correctly without it enabled. Please turn
            JavaScript back on and reload this page.
            </span>
                  </td>
               </table>
               <br><br><br><br>
            </noscript>

            <% if (errors) { %>
            <p class="jive-error-text">
               Login failed: Make sure your username and password are correct.
            </p>
            <% } %>

            <span class="jive-login-form">

        <table cellpadding="6" cellspacing="0" border="0" style="border : 1px #bbb solid;">
        <tr>
            <th style="padding-left:10px;">
                Jive Forums 3 Admin Login
            </th>
        </tr>
        <tr>
            <td>

                <table cellpadding="3" cellspacing="0" border="0">
                <tr valign="top">
                    <td>
                        <input type="text" name="username" size="15" maxlength="50">
                    </td>
                    <td>
                        <input type="password" name="password" size="15" maxlength="50">
                    </td>
                    <td align="center">
                        <input type="submit" value="&nbsp; Login &nbsp;">
                    </td>
                </tr>
                <tr valign="top">
                    <td class="jive-login-label">
                        username
                    </td>
                    <td class="jive-login-label">
                        password
                    </td>
                    <td>
                        &nbsp;
                    </td>
                </tr>
                <tr class="jive-login-label">
                    <td colspan="3"><img src="images/blank.gif" width="1" height="4" border="0"></td>
                </tr>
                <tr class="jive-footer">
                    <td colspan="3">
                        Jive Forums <%= Version.getEdition().getName() %>,
                        Version: <%= Version.getVersionNumber() %>
                    </td>
                </tr>
                </table>

            </td>
        </tr>
        </table>

        </span>

         </td>
         <td width="49%"><br></td>
      </tr>
   </table>

</form>

<script language="JavaScript" type="text/javascript">
   <!--
   document.loginForm.username.focus();
   //-->
</script>

</body>
</html>
