<%@ page import="org.pente.gameServer.client.web.*,
                 org.pente.gameServer.server.Resources,
                 org.pente.gameServer.core.*,
                 java.io.*,
                 java.util.*" %>

<%
   String pluginStr = request.getParameter("plugin");
   String requestPort = request.getParameter("port");
   String name = (String) request.getAttribute("name");
   String guest = request.getParameter("guest");

   int width = 640;
   String size = request.getParameter("size");
   if (size != null && size.equals("800")) {
      width = 800;
   }
   int adWidth = 0;
   String loc = "/gameServer/applet.jsp?plugin=" + pluginStr + "&port=" +
      requestPort + "&size=" + size;
   if (guest != null) {
      loc += "&guest=" + guest;
   }
%>

<%@ include file="colors.jspf" %>

<html>
<head>
   <title>Pente.org - Play Free Online Multiplayer Pente Game</title>
   <script language="javascript">
      function sub() {
         window.location = "<%= loc %>";
      }
   </script>
   <style type="text/css">
       body {
           font-family: Verdana, Arial;
       }
   </style>

</head>
<body bgcolor="#ffffff" link="<%= linkColor %>" alink="<%= linkColor %>"
      vlink="<%= linkColor %>"
      text="black"
      topmargin="0" leftmargin="0" marginwidth="0" marginheight="0">

<div style="padding:10px">

   <h2 style="color:<%= bgColor1 %>">Happy Holidays - Rated Sets Arrive at Pente.org!</h2>

   You've asked for it over the years and now it is finally here! Set-based ratings
   are now implemented for live games at pente.org.<br/><br/>

   To play a rated match against another player you must now play 2 games,
   one as player one and one as player two. You must win both games to win
   the set and gain any ratings points. If you and your opponent split the games, then
   the set is a draw and no ratings change takes place. <br/>
   <br/>
   Sets are only required for rated matches, unrated games can still be played as before.<br/>
   <br/>
   <a href="/gameServer/sets.jsp" target="_blank">View more details about set-based ratings</a>.<br/>
   <br/>
   <center>
      <form>
         <input type="button" value="Continue Login" onclick="javascript:sub();">
      </form>
   </center>

</div>
</body>
</html>
