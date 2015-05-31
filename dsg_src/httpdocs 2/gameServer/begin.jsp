<%@ page import="java.text.*,
                 java.util.*,
                 java.sql.*,
                 java.util.Date,
                 
                 javax.servlet.*,
                 javax.servlet.http.*,
                 
                 com.jivesoftware.base.*,
                 com.jivesoftware.forum.*,
                 
                 org.pente.jive.*,
                 org.pente.database.*,
                 org.pente.game.*,
                 org.pente.gameServer.core.*,
                 org.pente.gameServer.server.*,
                 org.pente.gameServer.client.web.*,
                 org.pente.gameServer.tourney.*,
                 org.pente.turnBased.*" %>
<%!
private static final DateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy");
private static final NumberFormat numberFormat = NumberFormat.getInstance();
private static final NumberFormat nf = NumberFormat.getNumberInstance();
private SiteStatsData siteStatsData;
private DBHandler dbHandler;
private DSGPlayerStorer dsgPlayerStorer;
private Resources globalResources;
private int headerCount = 0;
private LeaderBoard leaderboard;
private static int ad = 0; 
 
public void jspInit() {
    ServletContext ctx = getServletContext();
    siteStatsData = (SiteStatsData) ctx.getAttribute(
    	SiteStatsData.class.getName());

    globalResources = (Resources) ctx.getAttribute(Resources.class.getName());
    dbHandler = (DBHandler) ctx.getAttribute(DBHandler.class.getName());
    dsgPlayerStorer = (DSGPlayerStorer) 
        ctx.getAttribute(DSGPlayerStorer.class.getName());
    leaderboard = (LeaderBoard) ctx.getAttribute("leaderboard");
}
%>

<%
// don't allow pages to be cached so players always know if they are logged in or not
response.setHeader("Cache-Control","no-cache"); //HTTP 1.1
response.setHeader("Pragma","no-cache"); //HTTP 1.0
response.setDateHeader ("Expires", 0); //prevents caching at the proxy server

ad++;

String me = (String) request.getAttribute("name");

    // setup jive forumfactory so news can be viewed, etc.
    AuthToken authToken = null;
    try {
        authToken = AuthFactory.getAuthToken(request,response);
    }
    catch (Exception ignored) {}

    if (authToken == null) {
        authToken = AuthFactory.getAnonymousAuthToken();
    }
    ForumFactory forumFactory = ForumFactory.getInstance(authToken);
%>

<%! private static final String loggedInTabs[][] = new String[][] {
 { "/index.jsp?s=1", "Home" },
 { "/gameServer/index.jsp", "Dashboard" },
 { "/gameServer/myprofile", "My Profile" },
 { "/gameServer/forums", "Forums" },
 { "/gameServer/tournaments", "Tournaments" },
 { "/help/helpWindow.jsp?file=gettingStarted", "Help" },
 { "/help/helpWindow.jsp?file=faq", "FAQ" },
 { "/gameServer/logout", "Logout" }
}; %>

<% pageContext.setAttribute("style", "style2.css"); %>
<% if (me != null) { pageContext.setAttribute("tabs", loggedInTabs); } %>
<%@include file="../top.jsp" %>

<%@ include file="colors.jspf" %>

<form name="submit_form" method="post" 
      action="<%= request.getContextPath()%>/gameServer/controller/search"
      style="margin-top:0px;margin-bottom:0px;margin-left:0px;margin-right:0px">
<input type="hidden" name="format_name" value="org.pente.gameDatabase.SimpleGameStorerSearchRequestFormat">
<input type="hidden" name="format_data">
</form>

<script language="javascript"
        src="/gameServer/js/openwin.js">
</script>
<script language="javascript"
        src="<%= request.getContextPath() %>/gameServer/js/submitDb.js">
</script>
<% String onLoad = (String) pageContext.getAttribute("onLoad");
   if (onLoad != null) { %>
<script type="text/javascript">addLoadEvent(function(){<%= onLoad %>});</script>
<% } %>

<% String pageWidth = (String) pageContext.getAttribute("pageWidth");
   if (pageWidth == null) pageWidth = "930";
   String pwd = pageWidth;
   String rightWidth = (String) pageContext.getAttribute("rightWidth");
   boolean leftNav = pageContext.getAttribute("leftNav") == null;
   leftNav = false;
   if (!pageWidth.endsWith("%")) {
	    if (leftNav && rightWidth == null) {
	    	rightWidth = ""+(Integer.parseInt(pageWidth) - 170);
	    } else if (rightWidth == null) {
	    	rightWidth = "100%";
	    }
	    pwd += "px";
   } else if (leftNav && rightWidth == null) {
	   rightWidth = "95%"; 
   } 
%>
<div class="pagebody" style="width:<%= pwd %>;">

  <table border="0" id="main" cellpadding="0" cellspacing="0" width="<%= pageWidth %>">
	<tr>
      <td valign="top" width="<%= rightWidth %>">
