<%@ page import="java.text.*,
                 java.util.*,
                 org.pente.gameServer.server.*,
                 org.pente.gameServer.client.web.*" %>
<%!
private SiteStatsData siteStatsData;
private static final NumberFormat numberFormat = NumberFormat.getInstance();
private ActivityLogger activityLogger = null;
public void jspInit() {
    ServletContext ctx = getServletContext();
    siteStatsData = (SiteStatsData) ctx.getAttribute(
        SiteStatsData.class.getName());
    activityLogger = (ActivityLogger)
        ctx.getAttribute(ActivityLogger.class.getName());
}
%>
<% 
    //TODO wasteful, keep track with sessionlistener perhaps
    int onlinePlayers = 0;
    List<String> seen = new ArrayList<String>();
    {
        SessionListener sl = (SessionListener)
            application.getAttribute(SessionListener.class.getName());
         
        Object sessions[] = sl.getActiveSessions().toArray();
    
        for (int i = 0; i < sessions.length; i++) {
            HttpSession s = (HttpSession) sessions[i];
            try {
                if (s == null) continue;
                String n = (String) s.getAttribute("name");
                if (n != null &&
                    !seen.contains(n)) {
                    onlinePlayers++;
                    seen.add(n);
                }
            } catch (IllegalStateException ignore) {}
        }
    } 
    ActivityData d[] = activityLogger.getPlayers();
    for (ActivityData a : d) {
    	if (!seen.contains(a.getPlayerName())) {
    		onlinePlayers++;
    		seen.add(a.getPlayerName());
    	}
    }
%>
<div id="intro">
     <h6><span><%= numberFormat.format(siteStatsData.getNumPlayers()) %></span> players, <span><%= numberFormat.format(siteStatsData.getNumGames()) %></span> games, <span><%= onlinePlayers %></span> players online now.</h6>
</div>