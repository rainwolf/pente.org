
<% String homeLink = "/gameServer/index.jsp";
   if (request.getAttribute("name") == null) {
        homeLink = "/index.jsp";
   } %>
   
    <tr>
	  <td colspan="5">
	    <table border="0" cellspacing="0" cellpadding="0" width="100%">
          <tr>
            <td bgcolor="black">
              <table border="0" cellspacing="0" cellpadding="0" width="100%">
			    <tr bgcolor="<%= bgColor1 %>">
				  <td width="170" align="center">
				    <font face="Verdana, Arial, Helvetica, sans-serif" size="2" color="<%= textColor1 %>">
				      <b>Play Free Multiplayer Online Pente Live!</b>
				    </font>
				  </td>
				  <td align="center">
				    <a href="<%= homeLink %>"><img src="<%= request.getContextPath() %>/gameServer/images/header.gif" border="0" width="500" height="50"></a>
				  </td>
				  <td width="150" align="center">
			        <font face="Verdana, Arial, Helvetica, sans-serif" size="2" color="white">
				     <b><a href="<%= request.getContextPath() %>/gameServer/donations"><font color="<%= textColor1 %>">Support Pente.org</font></a></b><br>
					 <a href="<%= request.getContextPath() %>/gameServer/donations"><img src="<%= request.getContextPath() %>/gameServer/images/paypal.gif" width="62" height="31" border="0" alt="Donations"></a>
				    </font>
				  </td>
				</tr>
		      </table>
		    </td>
		  </tr>
		</table>
      </td>
    </tr>