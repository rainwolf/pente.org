<% pageContext.setAttribute("title", "Forgot Password Confirmation"); %>
<%@ include file="begin.jsp" %>

<table width="100%" border="0" colspacing="0" colpadding="0">

   <tr>
      <td>
         <h3>Password emailed confirmation</h3>
      </td>
   </tr>

   <tr>
      <td>
         <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
            <%= request.getParameter("forgotPasswordName") %>, your password has been
            sent to the email address stored with your profile. Once you receive your
            password, you can <b><a href="<%= request.getContextPath() %>/gameServer/login.jsp">login</a></b>.
            <% if (request.getParameter("emailValid") != null) { %>
            <br><br>
            <font color="<%= textColor2 %>">
               Pente.org has your email address marked as invalid. This means the last time an
               email was sent to you from Pente.org it was returned with errors. Therefore, if
               you don't receive your password, you are probably out of luck. Of course,
               you can always <b><a href="<%= request.getContextPath() %>/join.jsp">
               join</a></b> as a new player.
            </font>
            <% } %>
         </font>
      </td>
   </tr>

</table>
<br>

<%@ include file="end.jsp" %>
