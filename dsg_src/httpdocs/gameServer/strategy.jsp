<% pageContext.setAttribute("title", "Tutorials"); %>
<%@ include file="begin.jsp" %>


<applet name="tutorial"
        codebase="<%= request.getContextPath() %>/gameServer/lib/"
	    code="org.pente.tutorial.TutorialApplet.class" 
	    archive="tutorial.jar" 
		width="580" 
    	height="700"> 

  <font size="2" face="Verdana, Arial, Helvetica, sans-serif" color="white">
    Your browser does not support java or is not configured properly to
    run java programs.<br>
    <br>
    <b><a href="/gameServer/help/helpWindow.jsp?file=faqAppletTroubleShooting">
         Applet Troubleshooting</a></b>
  </font>

   </applet>
<br>

<%@ include file="end.jsp" %>
