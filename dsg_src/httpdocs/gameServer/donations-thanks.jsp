<% pageContext.setAttribute("title", "Donations"); %>
<%@ include file="begin.jsp" %>

<table border="0" cellpadding="0" cellspacing="0" width="100%">
   <tr>
      <td><h3>Thank you for your Donation</h3></td>
   </tr>
   <tr>
      <td>
         <font size="2" face="Verdana, Arial, Helvetica, sans-serif">
            Pente.org thanks you for your donation! Your support will help to create a better site.<br>
            <br>
            As soon as I accept your payment (should be within 24 hours) I will email
            you and then you can setup your donor settings at
            <b><a href="<%= request.getContextPath() %>/gameServer/myprofile/donor">My Profile</a></b>
            as a reward for your donation!
         </font></td>
   </tr>
   <tr>
      <td colspan="2">&nbsp;</td>
   </tr>
</table>

<%@ include file="end.jsp" %>
