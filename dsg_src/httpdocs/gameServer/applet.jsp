<%@ page import="org.pente.gameServer.client.web.*,
                 java.io.*,
                 org.pente.gameServer.core.*" %>

<%!
   private static String version = null;
   private static int appletSize = 0;
   private DSGPlayerStorer dsgPlayerStorer;

   public void jspInit() {
      ServletContext ctx = getServletContext();
      version = (String) ctx.getAttribute("appletVersion");

      String appletPath = ctx.getRealPath("/gameServer/lib/pente__V" +
         version + ".jar");
      File appletFile = new File(appletPath);
      appletSize = (int) (appletFile.length() / 1024);   // in kb

      dsgPlayerStorer = (DSGPlayerStorer)
         ctx.getAttribute(DSGPlayerStorer.class.getName());
   }
%>

<%
   int port = 0; // the port to connect to

// don't allow pages to be cached so players always know if they are logged in or not
   response.setHeader("Cache-Control", "no-cache"); //HTTP 1.1
   response.setHeader("Pragma", "no-cache"); //HTTP 1.0
   response.setDateHeader("Expires", 0); //prevents caching at the proxy server

   String name = (String) request.getAttribute("name");
   String displayName = name == null ? "guest" : name;
   String password = (String) request.getAttribute("password");
   String guest = (String) request.getParameter("guest");
   DSGPlayerData meData = null;
   if (guest == null) {
      meData = dsgPlayerStorer.loadPlayer(name);
   }

   String pluginStr = request.getParameter("plugin");
   boolean plugin = pluginStr != null && pluginStr.equals("true");

   String requestPort = request.getParameter("port");
   if (requestPort != null) {
      try {
         port = Integer.parseInt(requestPort);
      } catch (NumberFormatException ne) {
      }
   }
   if (port == 0) {
      port = 16000; //default
   }

   int width = 640;
   int height = 390;
   String size = request.getParameter("size");
   if (size != null && size.equals("800")) {
      width = 800;
      height = 495;
   }
//String appletBack="#4e7df4";
//String appletFore="#ffffff";
   String appletBack = "#134621";
   String appletFore = "#C2E1CA";
%>

<%@ include file="colors.jspf" %>

<html>
<head>
   <title>Pente.org - Play Free Online Multiplayer Pente Game</title>
</head>
<script language="javascript">
   function helpWin() {
      window.open('/gameServer/help/helpWindow.jsp?file=faqAppletTroubleShooting', 'help', '');
   }
</script>
<body bgcolor="<%= appletBack %>" text="<%= appletFore %>" link="<%= appletFore %>" topmargin="0" leftmargin="0"
      marginwidth="0" marginheight="0">

<table width="<%= width %>" border="0" cellpadding="0" cellspacing="0">
   <tr>
      <td width="10">&nbsp;</td>
      <td align="left" width="475">
         <% if (guest != null || meData == null || meData.showAds()) { %>
         <iframe src="/gameServer/appletAd.jsp" width="475" height="60" scrolling="no" frameborder="0" marginwidth="0"
                 marginheight="0">
            <%-- for browsers that do not support iframe --%>
            <%@ include file="amazonHeaderPlay.jsp" %>
         </iframe>
         <% } %>
      </td>
      <td align="right" valign="top" width="150">
         <img src="<%= request.getContextPath() %>/gameServer/images/logo2.png">
      </td>
      <td width="5">&nbsp;</td>
   </tr>
   <tr>
      <td width="10">&nbsp;</td>
      <td width="470" valign="bottom">
         <font size="2" face="Verdana, Arial, Helvetica, sans-serif">
            Welcome, <b><%=displayName %>
         </b>. The game room is large
            (<%= appletSize %> kb) and may take a few minutes to load.
            <b><a href="javascript:helpWin();">Applet Troubleshooting</a></b>.
         </font>
      </td>
      <td align="right" valign="bottom" width="150">
         <font size="1" face="Verdana, Arial, Helvetica, sans-serif">
            (Version <%= version %>)
         </font>
      </td>
      <td width="10">&nbsp;</td>
   </tr>
   <tr>
      <td colspan="4">


         <% if (plugin) { %>

         <object classid="clsid:8AD9C840-044E-11D1-B3E9-00805F499D93"
                 width="<%= width %>"
                 height="<%= height %>"
                 name="pente"
                 codebase="http://java.sun.com/products/plugin/1.3/jinstall-13-win32.cab#Version=1,3,0,0">

            <param name="CODE" VALUE="org.pente.gameServer.client.awt.PenteApplet.class">
            <param name="CODEBASE" VALUE="<%= request.getContextPath() %>/gameServer/lib/">
            <param name="ARCHIVE" VALUE="pente__V<%= version %>.jar">
            <param name="NAME" VALUE="pente">
            <param name="type" VALUE="application/x-java-applet;version=1.3">
            <param name="scriptable" VALUE="false">
            <param name="loadSounds" value="true">
            <% if (name != null) { %>
            <param name="playerName" value="<%= name %>">
            <% } %>
            <% if (password != null) { %>
            <param name="password" value="<%= password %>">
            <% } %>
            <% if (guest != null) { %>
            <param name="guest" value="<%= guest %>">
            <% } %>
            <param name="gameServerPort" value="<%= port %>">
            <param name="background" value="<%= appletBack %>">
            <param name="foreground" value="<%= appletFore %>">
            <param name="Permissions" value="sandbox"/>
            <comment>
               <embed type="application/x-java-applet;version=1.3"
                      code="org.pente.gameServer.client.awt.PenteApplet.class"
                      codebase="<%= request.getContextPath() %>/gameServer/lib/"
                      archive="pente__V<%= version %>.jar"
                      name="pente"
                      width="<%= width %>"
                      height="<%= height %>"
                      loadSounds=true
                  <% if (name != null) { %>
                      playerName="<%= name %>"
                  <% }
              if (password != null) { %>
                      password="<%= password %>"
                  <% } %>
                  <% if (guest != null) { %>
                      guest="<%= guest %>"
                  <% } %>
                      gameServerPort="<%= port %>"
                      background="<%= appletBack %>"
                      foreground="<%= appletFore %>"
                      scriptable=false
                      pluginspage="http://java.sun.com/products/plugin/1.3/plugin-install.html">
               <noembed>
            </comment>
            </noembed>
            </embed>
         </object>

         <% } else { %>

         <applet name="pente"
                 codebase="<%= request.getContextPath() %>/gameServer/lib/"
                 code="org.pente.gameServer.client.awt.PenteApplet.class"
                 archive="pente__V<%= version %>.jar"
                 width="<%= width %>"
                 height="<%= height %>">

            <param name="loadSounds" value="true">

            <% if (name != null) { %>
            <param name="playerName" value="<%= name %>">
            <% } %>

            <% if (password != null) { %>
            <param name="password" value="<%= password %>">
            <% } %>

            <% if (guest != null) { %>
            <param name="guest" value="<%= guest %>">
            <% } %>

            <param name="gameServerPort" value="<%= port %>">
            <param name="background" value="<%= appletBack %>">
            <param name="foreground" value="<%= appletFore %>">

            <br>
            <br>
            <font size="2" face="Verdana, Arial, Helvetica, sans-serif">
               Your browser does not support java or is not configured properly to
               run java programs.<br>
               <br>
            </font>
            <center>
               <h1>
                  To access the game room, please use the install link and not the "join game room" button.
                  <br>If you do not have Java installed, please download and install it from <a
                  href="java.com">java.com</a> first.
               </h1>
            </center>

         </applet>

         <% } %>

      </td>
   </tr>
</table>
<script src="http://www.google-analytics.com/urchin.js" type="text/javascript">
</script>
<script type="text/javascript">
   _uacct = "UA-1958590-1";
   urchinTracker();
</script>
</html>
