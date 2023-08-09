<%@ page import="org.pente.gameServer.client.web.*,
                 org.pente.gameServer.server.Resources,
                 org.pente.gameServer.core.*,
                 java.io.*,
                 java.util.*" %>

<%!
   private Resources resources;
   private DSGPlayerStorer dsgPlayerStorer;

   public void jspInit() {
      ServletContext ctx = getServletContext();

      resources = (Resources) ctx.getAttribute(Resources.class.getName());
      dsgPlayerStorer = (DSGPlayerStorer)
         ctx.getAttribute(DSGPlayerStorer.class.getName());
   }
%>

<%! private static int adCounter = 0; %>
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
   if (guest != null && guest.equals("true")) {
      response.sendRedirect("/gameServer/applet.jsp?plugin=" +
         pluginStr + "&port=" + requestPort + "&size=" + size + "&guest=true");
      return;
   }
   if (true) {
//if (name != null) {
      //try {
      //    DSGPlayerData me = dsgPlayerStorer.loadPlayer(name);
      //     if (me.hasPlayerDonated()) {
      response.sendRedirect("/gameServer/applet.jsp?plugin=" +
         pluginStr + "&port=" + requestPort + "&size=" + size);
      return;
      //    }
      //} catch (DSGPlayerStoreException dpse) {
      //}
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
      function submit() {
         window.location = "<%= loc %>";
      }

      function wait() {
         setTimeout("submit()", 20000);
      }
   </script>
</head>
<body bgcolor="<%= bgColor1 %>" link="<%= linkColor %>" alink="<%= linkColor %>"
      vlink="<%= linkColor %>"
      text="white"
      topmargin="0" leftmargin="0" marginwidth="0" marginheight="0"
      onload="javascript:wait();">

<table width="<%= width %>" border="0" cellpadding="0" cellspacing="0">
   <tr>
      <td align="right" colspan="3">
         <font size="-2" face="Verdana, Arial, Helvetica, sans-serif">
            <a href="javascript:submit();"><font color="white">Skip this ad</font></a>
         </font>
      </td>
   </tr>

   <tr>
      <%--
         <% adCounter++; %>
         <% if (adCounter %2 == 0) { %>
      --%>
      <%@ include file="winningMovesAd.jsp" %>
      <%--
         <% } %>
         <% else { %>
           <%@ include file="dsgStoreAd.jsp" %>
         <% } %>

         <% adCounter++;
            if (adCounter % 2 == 0) { %>
           <%@ include file="tourneyAd.jsp" %>
         <% } else if (adCounter % 2 == 1) { %>
           <%@ include file="speedAd.jsp" %>
         <% } %>
      --%>
   </tr>

   <tr>
      <td width="<%= (width - adWidth) / 2 %>">&nbsp;</td>
      <td width="adWidth">
         <font face="Verdana, Arial, Helvetica, sans-serif">
            The game room will load momentarily.</font>
      </td>
      <td width="<%= (width - adWidth) / 2 %>">&nbsp;</td>
   </tr>


</table>
</html>
