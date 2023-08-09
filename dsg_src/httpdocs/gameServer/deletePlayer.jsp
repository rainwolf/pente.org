<% pageContext.setAttribute("title", "Delete Player"); %>
<%@ include file="begin.jsp" %>

<table width="100%" border="0" colspacing="1" colpadding="1">

   <tr>
      <td>
         <h3>Delete Player</h3>
      </td>
   </tr>

   <% String deletePlayerError = (String) request.getAttribute("deletePlayerError");
      String deletePlayerSuccess = (String) request.getAttribute("deletePlayerSuccess");

      if (deletePlayerError != null) { %>

   <tr>
      <td>
         <font face="Verdana, Arial, Helvetica, sans-serif" size="2" color="<%= textColor2 %>">
            Deleting player failed: <%= deletePlayerError %>
         </font>
      </td>
   </tr>

   <%
   } else if (deletePlayerSuccess != null) {
   %>

   <tr>
      <td>
         <font face="Verdana, Arial, Helvetica, sans-serif" size="2" color="<%= textColor2 %>">
            Deleting player successful: <%= deletePlayerSuccess %><br>
            <br>
         </font>
      </td>
   </tr>

   <%
   } else {
      String deleteName = (String) request.getAttribute("name");
   %>

   <tr>
      <td>
         <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
            <form method="post" action="deletePlayer">
               <input type="hidden" name="deleteConfirm" value="yes">

               Are you sure you want to delete your account for <%= deleteName %>?<br>
               <input type="submit" value="Delete account">
            </form>
         </font>
      </td>
   </tr>

   <%
      }
   %>

</table>

<%@ include file="end.jsp" %>
