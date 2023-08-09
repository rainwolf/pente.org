<%@ page import="java.util.*" %>

<% pageContext.setAttribute("title", "Login"); %>
<%@ include file="begin.jsp" %>

<table width="100%" border="0" colspacing="0" colpadding="0">
   <tr>
      <td>
         <h3>Login to Pente.org</h3>

         Please login to Pente.org. If you have not joined yet,
         please <b><a href="/join.jsp">join</a></b> to enjoy the many
         <b><a href="/features.jsp">features of pente.org</a></b>.
      </td>
   </tr>
   <tr>
      <td>&nbsp;</td>
   </tr>

   <% String invalidLogin = (String) request.getAttribute("invalidLogin");
      if (invalidLogin != null && invalidLogin.equals("invalid")) { %>

   <tr>
      <td>
         <b><font face="Verdana, Arial, Helvetica, sans-serif" size="2" color="<%= textColor2 %>">
            Invalid name or password, please try again.
         </font></b>
      </td>
   </tr>

   <%
   } else if (invalidLogin != null && invalidLogin.equals("speed")) { %>

   <tr>
      <td>
         <b><font face="Verdana, Arial, Helvetica, sans-serif" size="2" color="<%= textColor2 %>">
            This player has been converted from a "speed" player to a "normal"
            player. You should not be playing speed games with a separate player
            anymore, speed games will be recorded separately under your normal
            player (so login with your normal player).<br>
            <br>See the FAQ and the Forums for more information on this
            conversion. If you have no idea what this message is about or feel
            this player was wrongly deactivated please email dweebo@pente.org.
            <br>
         </font></b>
      </td>
   </tr>

   <% }
   %>

   <tr>
      <td>

         <%
            String action = (String) request.getAttribute("loginAction");
            String mobile = (String) request.getParameter("mobile");

            if (action == null || action.endsWith("login.jsp")) {
               // make sure index.jsp is specified because double logins
               // were occurring when specifying just /gameServer/
               if (mobile == null) {
                  action = request.getContextPath() + "/gameServer/index.jsp";
               } else {
                  action = request.getContextPath() + "/gameServer/mobile/empty.jsp";
               }
            }
         %>

         <form name="login_form" method="post" action="<%= action %>">

            <%
               Enumeration params = request.getParameterNames();
               while (params.hasMoreElements()) {
                  String name = (String) params.nextElement();
                  if (!name.equals(LoginCookieHandler.NAME_COOKIE) &&
                     !name.equals(LoginCookieHandler.PASSWORD_COOKIE)) {
                     String values[] = request.getParameterValues(name);
                     if (values != null) {
                        for (int i = 0; i < values.length; i++) { %>
            <input type="hidden" name="<%= name %>" value="<%= values[i] %>">
            <% }
            }
            }
            }
            %>

            <table border="0" colspacing="1" colpadding="1">
               <tr>
                  <td>
                     <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
                        Name
                     </font>
                  </td>
                  <td>
                     <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
                        <input type="text" name="<%= LoginCookieHandler.NAME_COOKIE %>" size="15" maxlength="10">
                     </font>
                  </td>
               </tr>
               <tr>
                  <td>
                     <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
                        Password
                     </font>
                  </td>
                  <td>
                     <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
                        <input type="password" name="<%= LoginCookieHandler.PASSWORD_COOKIE %>" size="15"
                               maxlength="16">
                     </font>
                  </td>
               </tr>
               <tr>
                  <td>&nbsp;</td>
                  <td>
                     <font face="Verdana, Arial, Helvetica, sans-serif" size="2" color="#ffffff">
                        <input type="submit" value="Login">
                     </font>
                  </td>
               </tr>
            </table>
         </form>
      </td>
   </tr>

   <tr>
      <td>
         <font face="Verdana, Arial, Helvetica, sans-serif" size="2" color="#ffffff">
            <b><a href="<%= request.getContextPath() %>/gameServer/forgotpassword.jsp">Forgot your password?</a></b>
         </font>
      </td>
   </tr>
</table>
<br>

<%@ include file="end.jsp" %>