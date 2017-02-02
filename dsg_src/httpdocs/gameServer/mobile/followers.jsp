<%@ page import="org.pente.gameServer.core.*,
                org.pente.gameServer.server.*,
                 java.util.*" %><%@ page contentType="text/html; charset=UTF-8" %><%
String loggedInStr = (String) request.getAttribute("name");
 if (loggedInStr == null) {
    response.sendRedirect("empty.jsp");
   } else {
    Resources resources = (Resources) application.getAttribute(Resources.class.getName());
    DSGPlayerStorer dsgPlayerStorer = resources.getDsgPlayerStorer();
    DSGPlayerData meData = dsgPlayerStorer.loadPlayer(loggedInStr);
    DSGFollowerStorer followerStorer = resources.getFollowerStorer();
    List<Long> followers = followerStorer.getFollowers(meData.getPlayerID());
    List<Long> following = followerStorer.getFollowing(meData.getPlayerID());

    for (long pid: followers) {
        DSGPlayerData playerData = dsgPlayerStorer.loadPlayer(pid);
        %><%="0;" + playerData.getName() + ";" + (playerData.hasPlayerDonated()?1:0) + ";" + (playerData.hasPlayerDonated()?playerData.getNameColorRGB():0) + ";" + playerData.getTourneyWinner() + ";"%>
<%
    }
    for (long pid: following) {
        DSGPlayerData playerData = dsgPlayerStorer.loadPlayer(pid);
        %><%="1;" + playerData.getName() + ";" + (playerData.hasPlayerDonated()?1:0) + ";" + (playerData.hasPlayerDonated()?playerData.getNameColorRGB():0) + ";" + playerData.getTourneyWinner() + ";"%>
<%
    }
}
%>
