<% pageContext.setAttribute("title", "Install Java Web Start"); %>
<%@ include file="begin.jsp" %>

<h3>Install Java Web Start</h3>

<p>
   Pente.org has detected you do not have Java Web Start installed and will
   now attempt to auto-install it for you. When the installation is complete, the
   Game Room will be started.
</p>

<OBJECT codebase="http://java.sun.com/update/1.6.0/jinstall-6-windows-i586.cab#Version=6,0,0,0"
        classid="clsid:5852F5ED-8BF4-11D4-A245-0080C6F74284" height=0 width=0>
   <PARAM name="app" value="http://www.pente.org/<%= request.getContextPath() %>/gameServer/pente.jnlp">
   <PARAM name="back" value="true">
   <!-- Alternate HTML for browsers which cannot instantiate the object -->
   If the installation doesn't start on its own <A href="http://java.sun.com/javase/downloads/ea.jsp">
   Download Java Web Start</A>.
</OBJECT>
<br>

<br>
<%@ include file="end.jsp" %>