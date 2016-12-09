<%@ page import="org.pente.gameServer.server.*,
                java.util.*, java.security.MessageDigest, 
                org.apache.commons.codec.binary.Hex,
                 org.pente.gameServer.core.*" %>

<%
    String name = (String) request.getAttribute("name");

    if (name != null) {

        Resources resources = (Resources) application.getAttribute(Resources.class.getName());
        List<Server> serverList = resources.getServers();
        for (Iterator it = serverList.iterator(); it.hasNext();) {
            Server s = (Server) it.next();
            s.bootPlayer(name, 0);
        }
    }

%>

