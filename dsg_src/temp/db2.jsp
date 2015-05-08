<%@ page import="java.util.*" %>
<%@ page import="java.text.*" %>
<%@ page import="org.pente.gameServer.core.*" %>

<% pageContext.setAttribute("title", "DB2"); %>

<%@ include file="begin.jsp" %>

<table width="100%">
<tr>
<td>

This tool is still in development.<br>
Enter a player's name to lookup their last 100 games and review them.<br>
Requires java version 1.5 plugin, if you're using IE, it should prompt you to
download/install automatically.  Firefox users go to 
<a href="http://java.sun.com/j2se/1.5.0/download.html">http://java.sun.com/j2se/1.5.0/download.html</a>.
<br>

</td>
</tr>
</table>

	<object 
	  classid="clsid:8AD9C840-044E-11D1-B3E9-00805F499D93"
	  width="100" height="100"
	  codebase="http://java.sun.com/products/plugin/autodl/jinstall-1_5_0-windows-i586.cab#Version=1,5,0,0">
	  
	  <param name="code" value="org.pente.gameDatabase.swing.SimpleApplet.class">
	  <param name="codebase" value="/gameServer/lib/">
	  <param name="archive" value="db2.jar">
	  <param name="type" value="application/x-java-applet;version=1.5">
      <param name="scriptable" value="false">
      <param name="cookie" value="<%= session.getId() %>">
      <comment>
       <embed type="application/x-java-applet;version=1.5"  
              code="org.pente.gameDatabase.swing.SimpleApplet.class" 
              codebase="/gameServer/lib/" 
              archive="db2.jar" 
              width="100" 
              height="100"
              cookie="<%= session.getId() %>"
              scriptable="false"
              pluginspage="http://java.sun.com/j2se/1.5.0/download.html"><noembed></comment></noembed>
      </embed>
    </object>

<%@ include file="end.jsp" %>
