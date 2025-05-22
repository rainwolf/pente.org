<%@ page import="org.pente.gameServer.server.*,
                 java.util.*,
                 java.security.MessageDigest,
                 org.apache.commons.codec.binary.Hex,
                 org.pente.gameServer.core.*" %>

<%
   String name = (String) request.getAttribute("name");

   if (name != null) {

      Resources resources = (Resources) application.getAttribute(Resources.class.getName());
      List<Server> serverList = resources.getServers();
      for (Server s : serverList) {
         s.bootPlayer(name, 0);
      }
   }

%>

