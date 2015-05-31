<%@ page import="org.pente.gameServer.server.*,
                java.util.*, java.security.MessageDigest, 
                org.apache.commons.codec.binary.Base64,
                 org.pente.gameServer.core.*" %>

<html>
<body>

<%
    String name = request.getParameter("name");
    String pidHash = request.getParameter("pidHash");

    ServletContext ctx = getServletContext();
    DSGPlayerStorer dsgPlayerStorer = (DSGPlayerStorer) ctx.getAttribute(DSGPlayerStorer.class.getName());
    DSGPlayerData dsgPlayerData = dsgPlayerStorer.loadPlayer(name);

    MessageDigest md = MessageDigest.getInstance("SHA-256");
    String text = "pente seeds-" + dsgPlayerData.getPlayerID();
    md.reset();
    md.update(text.getBytes("UTF-8")); 
    Base64 base64 = new Base64();
    String checkHash = new String(base64.encode( md.digest() ));

    if (pidHash.equals(checkHash)) {
        Resources resources = (Resources) application.getAttribute(Resources.class.getName());
        List<Server> serverList = resources.getServers();
        for (Iterator it = serverList.iterator(); it.hasNext();) {
            Server s = (Server) it.next();
            s.bootPlayer(name, 0);
        }
        response.sendRedirect("/gameServer/index.jsp");
    } else {
        %>

        <b>naughty, naughty, naughty... trying to boot someone else </b><br><br>

        <%
    }

%>

</body>
</html>

