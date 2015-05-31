<%@ page import="java.util.*,
                 java.io.*,
                 org.pente.gameServer.core.*, 
                 org.pente.gameServer.client.web.*" %>
<%!
private DSGPlayerStorer dsgPlayerStorer;
public void jspInit() {
    ServletContext ctx = getServletContext();
    dsgPlayerStorer = (DSGPlayerStorer) 
        ctx.getAttribute(DSGPlayerStorer.class.getName());
}
%>
<%@ include file="randomdonor.jsp" %>