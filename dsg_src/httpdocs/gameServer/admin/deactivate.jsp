<%@ page import="org.pente.gameServer.server.*,
                 org.pente.gameServer.core.*,
                 org.apache.log4j.*,
                 java.io.*,
                 java.text.*,
                 java.util.*,
                 org.pente.jive.*,
                 com.jivesoftware.base.*,
                 com.jivesoftware.forum.*" %>

<%! private static Category log4j = 
        Category.getInstance("org.pente.gameServer.web.client.jsp"); %>

<%! private static final DateFormat df = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss"); %>

<%! private static final DSGAuthToken adminToken =
        new DSGAuthToken(22000000000002L); %>
        
<% Resources resources = (Resources) application.getAttribute(
       Resources.class.getName());
   DSGPlayerStorer storer = resources.getDsgPlayerStorer();
   String name = request.getParameter("name");
   
   boolean success = false;
   
   if (name != null) {
	   try {
		   DSGPlayerData d = storer.loadPlayer(name);
		   if (d != null) {
			   d.deRegister(DSGPlayerData.CHEATER);
			   storer.updatePlayer(d);
		   }
	   
           ForumFactory forumFactory = ForumFactory.getInstance(
               adminToken);
           UserManager userManager = forumFactory.getUserManager();
           User user = userManager.getUser(name);
           WatchManager watchManager = forumFactory.getWatchManager();
           watchManager.deleteWatches(user);

	       success = true;
	       
	   } catch (Throwable t) {
		   log4j.error("Error deactivating", t);
	   }
   }
%>

<html>
<head><title>Deactivate Player</title></head>
<body>

<h3>Deactivate Player</h3>

<% if (name != null) {
	if (success) { %>
	  Successfully deactivated player <%= name %>.
<%  } else { %>
	  Did not deactivate player <%= name %>, error detected.
<%  }
} %>

<form name="deactivate" action="deactivate.jsp" method="post">
  <table border="0" cellspacing="0" cellpadding="0">
    <tr>
        <td valign="top">Player name:&nbsp;&nbsp;</td>
        <td><input type="text" name="name"></td>
    </tr>


    <tr>
        <td>&nbsp;</td>
        <td valign="top">
          <input type="submit" value="Deactivate">
        </td>
      </tr>
  </table>
</form>

<a href=".">Back to admin</a>

</body>
</html>