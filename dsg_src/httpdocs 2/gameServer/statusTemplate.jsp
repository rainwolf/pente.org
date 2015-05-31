<% {
       String name = (String) request.getAttribute("name");
       boolean guest = false;
       if (name == null) {
           name = "guest";
           guest = true;
       } %>

    <tr bgcolor="#dcdcdc">
	  <td colspan="5">
	     <div style="background: #fff url(/gameServer/images/dropshadow_top.gif) repeat-x;">

	    <table border="0" cellspacing="0" cellpadding="0" width="100%">
	      <tr>
	        <td height="20">
              <font face="Verdana, Arial, Helvetica, sans-serif" size="2"><b>
 	            Welcome, <font color="<%= textColor2 %>"><%= name %></font>
 	          </b></font>
 	        </td>
	        <td align="right">
	          <font face="Verdana, Arial, Helvetica, sans-serif" size="2"><b>
	          
	            
               <form method="get" action="http://www.google.com/custom" target="_top"
                     style="margin-top:0px;margin-bottom:0px;margin-left:0px;margin-right:0px">
			    <input type="hidden" name="domains" value="pente.org"></input>
			    <input type="hidden" name="sitesearch" value="pente.org"></input>
				<input type="hidden" name="client" value="pub-3840122611088382"></input>
				<input type="hidden" name="forid" value="1"></input>
				<input type="hidden" name="ie" value="ISO-8859-1"></input>
				<input type="hidden" name="oe" value="ISO-8859-1"></input>
				<input type="hidden" name="cof" value="GALT:#008000;GL:1;DIV:#336699;VLC:663399;AH:center;BGC:FFFFFF;LBGC:336699;ALC:0000FF;LC:0000FF;T:000000;GFNT:0000FF;GIMP:0000FF;LH:50;LW:150;L:http://www.pente.org/gameServer/images/logo.gif;S:http://;LP:1;FORID:1;"></input>
				<input type="hidden" name="hl" value="en"></input>


	            <% if (!guest) { %>
	              <% if (!request.getServletPath().equals("/gameServer/index.jsp")) { %>
	              <a href="<%= request.getContextPath() %>/gameServer/index.jsp">Dashboard</a>
	              <% } %>
	              <% if (!request.getServletPath().startsWith("/gameServer/myprofile")) { %>
                  <a href="<%= request.getContextPath() %>/gameServer/myprofile">My Profile</a>
                  <% } %>
	              <a href="<%= request.getContextPath() %>/gameServer/logout">Logout</a>
	            <% } else { %>
	              <a href="<%= request.getContextPath() %>/gameServer/login.jsp">Login</a>
	              <a href="<%= request.getContextPath() %>/join.jsp">Join</a>
	              <a href="<%= request.getContextPath() %>/gameServer/forgotpassword.jsp">Forgot Password</a>
                  <a href="<%= request.getContextPath() %>/index.jsp">Home</a>
	            <% } %>
	              <a href="/help/helpWindow.jsp?file=gettingStarted">Help</a>
	              <input type="text" name="q" size="8" maxlength="255" value=""></input>
  			      <input type="submit" name="sa" value="Search"></input>
			   </form>  			      
	 	      </b></font>
	        </td>
          </tr>
        </table>

          </div>

      </td>
    </tr>
<% } %>