<% if (request.getAttribute("name") != null) {
   response.sendRedirect("/gameServer/index.jsp");
} %>
<%--
<%@ page import="java.util.*,
                 org.pente.gameServer.client.web.*,
                 org.pente.gameServer.server.*,
                 org.pente.gameServer.core.*"
         errorPage="../five00.jsp" %>

<% 
   pageContext.setAttribute("title", "Play"); 
   pageContext.setAttribute("googleSide", Boolean.valueOf(false));
   Resources resources = (Resources) application.getAttribute(
       Resources.class.getName());   
%>
<%@ include file="begin.jsp" %>

<!-- javascript to detect if java web start is installed -->
<script language="JavaScript">
  var javawsInstalled = 0;
  isIE = "false";
  if (navigator.mimeTypes && navigator.mimeTypes.length) {
    x = navigator.mimeTypes['application/x-java-jnlp-file'];
    if (x) {
      javawsInstalled = 1;
    }
  }
  else {
    isIE = "true";
  } 
</script> 
<script language="VBScript">
  on error resume next
  If isIE = "true" Then
    If Not(IsObject(CreateObject("JavaWebStart.isInstalled"))) Then
       javawsInstalled = 0
    Else
       javawsInstalled = 1
    End If
  End If
</script>


<!-- javascript to submit to different places depending on choice of launch method -->
<script language="javascript">
  function play() {

    if (document.playForm.plugin[2].checked) {
      // send to the jnlp file, load it up
      if (javawsInstalled==1) {
        document.playForm.action="/gameServer/dsg.jnlp";
        document.playForm.method="get";
      }
      // try to autoinstall
      else {
        document.playForm.action="http://java.sun.com/PluginBrowserCheck";
        document.playForm.method="get";
      }
    }
    else {
      document.playForm.action="/gameServer/play.jsp";
    }
    document.playForm.submit();
  }

</script>

<script language="javascript" src="/gameServer/js/openwin.js"></script>

<% // if the user is coming from the play screen, save their plugin
   // choice for next time and also open a new window to start playing
   String setPlugin = request.getParameter("setPlugin");
   String plugin = request.getParameter("plugin");
   String port = request.getParameter("port");
   LoginCookieHandler handler = new LoginCookieHandler();
   handler.loadCookie(request);
   if (setPlugin != null) 
   {
       session.setAttribute("pluginChoiceMade", new Object());
       
       handler.setPluginChoice(
       	 (plugin != null && plugin.equals("true")));
       handler.setCookie(request, response);
       
       if (plugin.equals("true")) {
    	   session.setAttribute("plugin", new Object());
       }
   %>

<script language="javascript">openwin('<%= handler.usePlugin() %>', '<%= port %>', '800', '590');</script>

<% } %>


<form name="playForm" method="post" action="">
  <input type="hidden" name="setPlugin" value="true">
  <input type="hidden" name="showPopupMessage" value="true">
  <input type="hidden" name="pass" value="http://www.pente.org/<%= request.getContextPath() %>/gameServer/jwsInstall.jsp">
  <input type="hidden" name="fail" value="http://java.sun.com/j2se/1.4.2/download.html">

<table border="0" cellpadding="0" cellspacing="0" width="100%">
 <tr> 
   <td valign="top" align="left"> 
     <h3>Start Playing Pente!</h3>

<%  if (request.getParameter("showPopupMessage") != null) { %>
        If the game room does not open in a new window
        <input type="button"
               onclick="javascript:openwin('<%= handler.usePlugin() %>', '<%= port %>', '800', '590');"
               value="Open Game Room"><br>

<%  }
    else { %>
        You are about to start Playing Free Online Multiplayer Pente.  
        The Game Room is written in java, and can be launched within
        any browser that supports java, or you can launch it with Java Web Start.
        If you have trouble getting the applet to work in your browser please
        read the 
        <b><a href="javascript:helpWin('faqAppletTroubleShooting');">Applet Troubleshooting</a></b>.<br>
        <br>
        <img align="left" src="images/jws.jpg">Want to launch the Game Room
        from your desktop without having to download it every time?  Try
        using Java Web Start below, or <b><a href="javascript:helpWin('jws');">Learn more about Java Web Start</a></b>.<br>
        <br><br>
        <input type="radio" name="plugin" value="false" <%= (!handler.usePlugin()) ? "checked" : "" %>>Play with my browsers default Java client<br>
        <input type="radio" name="plugin" value="true" <%= (handler.usePlugin()) ? "checked" : "" %>>Play with the Java browser plugin (Install may be required)<br>
        <input type="radio" name="plugin" value="jws">Play with Java Web Start (Install may be required)<br>
        <br>
        <% if (resources.getServerData().size() == 1) { %>
          <input type="hidden" name="port" value="<%= ((ServerData) resources.getServerData().get(0)).getPort() %>">
        <% } else { %>
          Game Room:
          <select name="port">
          <% for (Iterator it = resources.getServerData().iterator(); it.hasNext();) {
                 ServerData data = (ServerData) it.next(); %>
                 <option value="<%= data.getPort() %>"><%= data.getName() %></option>
          <% } %>
        <% } %>
        </select>
        <br>
        <input type="button" value="Start Playing" onClick="javascript:play();">
        <br>
	    <br>
<%  } %>

    </font>
  </td>
    <td valign="top" align="right">
      <%@ include file="rightAd.jsp" %>
    </td>

 </tr>
</table>

</form>

<%@ include file="end.jsp" %>
--%>