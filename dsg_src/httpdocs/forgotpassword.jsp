<% pageContext.setAttribute("title", "Forgot Password"); %>
<%@ include file="begin.jsp" %>

<table width="100%" border="0" cellspacing="0" cellpadding="0">
   <tr>
      <td>
         <h3>Forgot your password?</h3>

         <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
            If you remember your user name, you can have your password
            sent to the email address you joined with.
         </font>
      </td>
   </tr>

   <% String forgotPasswordError = (String) request.getAttribute("forgotPasswordError");
      if (forgotPasswordError != null) { %>
   <tr>
      <td>
         <br>
         <font face="Verdana, Arial, Helvetica, sans-serif" size="2" color="<%= textColor2 %>">
            <b>Sending password failed: <%= forgotPasswordError %>
            </b>
         </font>
      </td>
   </tr>

   <%
      }
   %>

   <tr>
      <td>

         <form name="forgotpassword_form" method="post" action="forgotpassword">
            <table border="0" colspacing="1" colpadding="1">
               <tr>
                  <td>
                     <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
                        Name
                     </font>
                  </td>
                  <td>
                     <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
                        <input type="text" name="forgotPasswordName" size="15" maxlength="10">
                     </font>
                  </td>
               </tr>
               <tr>
                  <td>&nbsp;</td>
                  <td>
                     <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
                        <input type="submit" value="Submit">
                     </font>
                  </td>
               </tr>
            </table>
         </form>
      </td>
   </tr>
</table>
<br>
<%@ include file="end.jsp" %>
