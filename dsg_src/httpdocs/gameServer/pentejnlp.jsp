<%
    response.setHeader("Pragma", "no-cache");
    response.setHeader("Expires", "0");
    response.setContentType("application/x-java-jnlp-file");
    response.setHeader("Content-Disposition", "filename=\"pente.jnlp\";");
%>

<%
    String name = (String) request.getParameter("name");
    String password = (String) request.getParameter("password");
    boolean hasParams = name != null && password != null;
%>

<?xml version="1.0" encoding="utf-8"?>

<!-- JNLP File for DSG Game Room -->
<!-- Certain keys are expanded in this file by other programs
4.3.0.6 is filled in by the ant build, and
$$codebase, $$name are expanded by the JNLPDownloadServlet at runtime -->

    <% if (hasParams) { %>
<jnlp spec="1.0+" codebase="<%=request.getScheme() + "://"+ request.getServerName() + request.getContextPath() + "/gameServer" %>" href="pente.jnlp?name=<%=name%>&amp;password=<%=password%>">
    <% } else { %>
<jnlp spec="1.0+" codebase="<%=request.getScheme() + "://"+ request.getServerName() + request.getContextPath() + "/gameServer" %>" href="pente.jnlp">
    <% } %>
    <information>
        <title>Pente.org Game Room</title>
        <vendor>Pente.org</vendor>
        <homepage href="https://www.pente.org/" />
        <description>Play Pente and other Games against other players.</description>
        <icon href="images/jws.jpg"/>
        <shortcut online="true">
            <desktop/>
            <menu submenu="Pente.org"/>
        </shortcut>
    </information>
    <security>
        <all-permissions/>
    </security>
    <resources>
        <j2se version="1.2+"/>
        <jar href="lib/jws/pente.jar" version="4.4.0.0" />
    </resources>
    <applet-desc
            documentBase="lib/jws"
            <%--documentbase="<%=request.getScheme() + "://"+ request.getServerName() + request.getContextPath() + "/gameServer" %>"--%>
            name="pente"
            main-class="org.pente.gameServer.client.awt.PenteApplet"
            width="800"
            height="600">
        <param name="loadSounds" value="true" />
        <% if (hasParams) { %>
            <param name="playerName" value="<%=name%>" />
            <param name="password" value="<%=password%>" />
            <param name="gameServerPort" value="16000" />
        <% } %>
    </applet-desc>
</jnlp>
