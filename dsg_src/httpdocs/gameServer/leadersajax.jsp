<%@ page import="java.util.*,
                 java.text.*,
                 org.pente.game.*, 
                 org.pente.gameServer.core.*, 
                 org.pente.gameServer.client.web.*" %>
<%!
private static final NumberFormat nf = NumberFormat.getNumberInstance();
private LeaderBoard leaderboard;
public void jspInit() {
    ServletContext ctx = getServletContext();
    leaderboard = (LeaderBoard) ctx.getAttribute("leaderboard");
}
%>
<%@ include file="leaders.jsp" %>