<%@ page import="org.pente.game.*, org.pente.turnBased.*,
                 java.util.*, java.security.MessageDigest,
                 org.apache.commons.codec.binary.Hex,
                 org.pente.gameServer.client.web.*,
                 org.pente.gameServer.server.*,
                 org.pente.gameServer.core.*,
                 org.pente.kingOfTheHill.*"
         errorPage="../five00.jsp" %>
<%!
private static final NumberFormat profileNF = NumberFormat.getPercentInstance();
%>

<% if (request.getAttribute("name") == null) {
    response.sendRedirect("/index.jsp");
   } %>
<%

Resources resources = (Resources) application.getAttribute(
   Resources.class.getName());
CacheKOTHStorer kothStorer = resources.getKOTHStorer();

String nm = (String) request.getAttribute("name");
String name = nm;
DSGPlayerData dsgPlayerData = dsgPlayerStorer.loadPlayer(nm);

int refresh = 5;
String grs = "800";
List prefs = dsgPlayerStorer.loadPlayerPreferences(
    dsgPlayerData.getPlayerID());
for (Iterator it = prefs.iterator(); it.hasNext();) {
    DSGPlayerPreference p = (DSGPlayerPreference) it.next();
    if (p.getName().equals("refresh")) {
        refresh = ((Integer) p.getValue());
    }
    else if (p.getName().equals("gameRoomSize")) {
        grs = (String) p.getValue();
    }
}
if (refresh != 0) {
    response.setHeader("Refresh", refresh * 60 + "; URL=index.jsp?refresh=1");
}   
TBGameStorer tbGameStorer = resources.getTbGameStorer();
List<TBSet> currentSets = tbGameStorer.loadSets(dsgPlayerData.getPlayerID());
List<TBSet> invitesTo = new ArrayList<TBSet>();
List<TBSet> invitesFrom = new ArrayList<TBSet>();
List<TBGame> myTurn = new ArrayList<TBGame>();
List<TBGame> oppTurn = new ArrayList<TBGame>();
Utilities.organizeGames(dsgPlayerData.getPlayerID(), currentSets,
    invitesTo, invitesFrom, myTurn, oppTurn);
String title2 = "Dashboard";
int gc = tbGameStorer.getNumGamesMyTurn(dsgPlayerData.getPlayerID());
if (gc > 0) {
    title2 += " (" + gc + ")";
}
int numMessages = resources.getDsgMessageStorer().getNumNewMessages(dsgPlayerData.getPlayerID());
int numGames = resources.getTbGameStorer().getNumGamesMyTurn(dsgPlayerData.getPlayerID());

boolean limitExceeded;
ServletContext ctx = getServletContext();
int gamesLimit = Integer.parseInt(ctx.getInitParameter("TBGamesLimit"));
// int gamesLimit = 6;
if (dsgPlayerData.unlimitedTBGames()) {
  limitExceeded = false;
} else {
  int currentCount = myTurn.size() + oppTurn.size();
  if (!invitesFrom.isEmpty()) {
    for (TBSet s : invitesFrom) {
      if (s.isTwoGameSet()) {
        currentCount += 2;
      } else {
        currentCount++;
      }
    }
  }
  if (currentCount > gamesLimit) {
    limitExceeded = true;
  } else {
    limitExceeded = false;
  }
}

List<TBSet> waitingSets = tbGameStorer.loadWaitingSets();
int openTBgames = 0;
// int concurrentPlayLimit = 2;
DSGPlayerData meData = dsgPlayerData;
long myPID = meData.getPlayerID();
for (Iterator<TBSet> iterator = waitingSets.iterator(); iterator.hasNext();) {
    TBSet s = iterator.next();

     if (s.getPlayer1Pid() != meData.getPlayerID() && s.getPlayer2Pid() != meData.getPlayerID()) { 
         openTBgames++;
     } else {
          iterator.remove();
          continue;
     }

    int nrGamesPlaying = 0;
    boolean alreadyPlaying = false, iAmIgnored = false;
    long theirPID = (0 == s.getPlayer1Pid()) ? s.getPlayer2Pid() : s.getPlayer1Pid();
    if (s.getInvitationRestriction() == TBSet.ANYONE_NOTPLAYING) {
        String setGame = GridStateFactory.getGameName(s.getGame1().getGame());
        for (TBGame g : myTurn) {
            long oppPid = myPID == g.getPlayer1Pid() ? g.getPlayer2Pid() : g.getPlayer1Pid();
            String myTurnGame = GridStateFactory.getGameName(g.getGame());
            if ((theirPID == oppPid) && (myTurnGame.equals(setGame))) {
//                nrGamesPlaying++;
//                if (nrGamesPlaying > concurrentPlayLimit) {
                    alreadyPlaying = true;
                    break;
//                }
            }
        }
        if (!alreadyPlaying) {
            for (TBGame g : oppTurn) {
                long oppPid = myPID == g.getPlayer1Pid() ? g.getPlayer2Pid() : g.getPlayer1Pid();
                String myTurnGame = GridStateFactory.getGameName(g.getGame());
                if ((theirPID == oppPid) && (myTurnGame.equals(setGame))) {
//                    nrGamesPlaying++;
//                    if (nrGamesPlaying > concurrentPlayLimit) {
                        alreadyPlaying = true;
                        break;
//                    }
                }
            }
        }

        if (alreadyPlaying && !"rainwolf".equals(name)) {
            openTBgames--;
            iterator.remove();
            continue;
        }
    }
    

    List<DSGIgnoreData> ignoreData = dsgPlayerStorer.getIgnoreData(theirPID);
    for (Iterator<DSGIgnoreData> it = ignoreData.iterator(); it.hasNext();) {
        DSGIgnoreData i = it.next();
        if (i.getIgnorePid() == myPID) {
            if (i.getIgnoreInvite()) {
                iAmIgnored = true;
                break;
            } 
        } 
    }
    if (iAmIgnored && !alreadyPlaying) {
        openTBgames--;
        iterator.remove();
        continue;
    }

    if (s.getInvitationRestriction() == TBSet.ANY_RATING) {
        continue;
    }
    DSGPlayerGameData myGameData = meData.getPlayerGameData(s.getGame1().getGame());
    int myRating = 1200;
    if (myGameData != null && myGameData.getTotalGames() > 0) {
        myRating = (int) Math.round(myGameData.getRating());
    }
    DSGPlayerData oppData = null;
    oppData = dsgPlayerStorer.loadPlayer(theirPID);
    DSGPlayerGameData oppGameData = null;
    if (oppData != null) {
        oppGameData = oppData.getPlayerGameData(s.getGame1().getGame());
    }
    int oppRating = 1200;
    if (oppGameData != null && oppGameData.getTotalGames() > 0) {
        oppRating = (int) Math.round(oppGameData.getRating());
    }
    if (s.getInvitationRestriction() == TBSet.LOWER_RATING) {
        if (myRating > oppRating) {
            openTBgames--;
            iterator.remove();
        }
        continue;
    }
    if (s.getInvitationRestriction() == TBSet.HIGHER_RATING) {
        if (myRating < oppRating) {
            openTBgames--;
            iterator.remove();
        }
        continue;
    }
    int delta = 100;
    if (s.getInvitationRestriction() == TBSet.SIMILAR_RATING) {
        if ((myRating + delta < oppRating) || (myRating - delta > oppRating)) {
            openTBgames--;
            iterator.remove();
        }
        continue;
    }
    if (s.getInvitationRestriction() == TBSet.CLASS_RATING) {
        if (1900 <= myRating && 1900 <= oppRating) {
            continue;
        }
        if ((myRating >= 1700 && myRating < 1900) && (oppRating >= 1700 && oppRating < 1900)) {
            continue;
        }
        if ((myRating >= 1400 && myRating < 1700) && (oppRating >= 1400 && oppRating < 1700)) {
            continue;
        }
        if ((myRating >= 1000 && myRating < 1400) && (oppRating >= 1000 && oppRating < 1400)) {
            continue;
        }
        if (1000 > myRating && oppRating < 1000) {
            continue;
        }
        openTBgames--;
        iterator.remove();
    }
}

%>
<% pageContext.setAttribute("title", title2); %>
<% pageContext.setAttribute("current", "Dashboard"); %>
<%-- pageContext.setAttribute("pageWidth", "1024"); --%>
<%-- pageContext.setAttribute("rightWidth", "650"); --%>
<%@ include file="begin.jsp" %>


<%--
<style type="text/css">
#wrapper { width:1024px; }
#footer { width:1014px; padding-right:10px;margin-bottom:5px; }
#header { width:900px; }
</style>
--%>
<style type="text/css">
h2 { font-size:14pt;margin-top:0;padding:2px 0 2px 0}
</style>
<script type="text/javascript" src="/gameServer/js/go.js"></script>

<script type="text/javascript">
window.google_analytics_uacct = "UA-20529582-2";
</script>



<% if (dsgPlayerData.showAds()) { %>
    <div id = "senseReplace" style="width:728px;height:90px;" top="50%"> </div>
    <%@ include file="728x90ad.jsp" %>
    <br style="clear:both">
<% } %>
<% if (dsgPlayerData.showAds()) { %>
    <script type="text/javascript">
        sensePage();
    </script>
<% } %>
<%--
--%>


 <div style="font-family:Verdana, Arial, Helvetica, sans-serif;
 float:left;width:70%">
 
    <h2 style="margin:0;padding:0;">Dashboard - Hi <%= ((dsgPlayerData.hasPlayerDonated() && (dsgPlayerData.getNameColorRGB() != 0)) ? "<span style='color:#" + Integer.toHexString(dsgPlayerData.getNameColorRGB()).substring(2) + "'>" : "<span>") %><%= dsgPlayerData.getName() %></span>!</h2>
    <a href="/gameServer/myprofile">Edit Profile</a> | <a href="/gameServer/mymessages">My Messages <%= numMessages > 0 ? "("+numMessages+" unread)" : "" %></a>
    <% if ("rainwolf".equals(dsgPlayerData.getName())) { %>
 | <a href="/gameServer/admin">adminLink</a> | <a href="/gameServer/who.jsp">who</a>
  <%}%>
    <br>
    
      <font size="-1">
      Refresh: <%= refresh == 0 ? "No refresh" : refresh + " minute" + (refresh == 1 ? "" : "s") %> - 
      <a href="/gameServer/myprofile/prefs">Change</a>
      </font>

<% if (true || dsgPlayerData.getLogins() < 5 || numMessages > 0 || numGames > 0) { %>
   <div style="font-family:Verdana, Arial, Helvetica, sans-serif;
   margin-top:10px;margin-bottom:10px;
   background:#fffbcc;
   border:1px solid #e6db55;
   padding:5px;
   font-weight:bold;
   width:100%;">
<% if (dsgPlayerData.getLogins() < 5) { %>
    Welcome to pente.org!<br>
    Read the <a href="/help/helpWindow.jsp?file=gettingStarted"><b>
    Getting Started</b></a> documentation to learn how to use all of pente.org or
    consult the <a href="/gameServer/forums">Forums</a>.<br>
    <br>
    <a href="/gameServer/myprofile">My Profile</a> - change your email address, 
    or any other information in your profile.<br> <hr>
<% } %>
<% if (numMessages > 0 || numGames > 0) {  %>
    <% if (numMessages > 0) {  %>
    You have <a href="/gameServer/mymessages"><%= numMessages %></a> new <%= numMessages > 1 ? "messages" : "message" %>.<br>
    <% } %>
    <% if (numGames > 0) { %>
    It is your turn in <%= numGames %> turn-based <%= numGames > 1 ? "games" : "game" %>. 
    <% } %>
<% } %>

  <%= (numGames + numMessages) > 0 ? "<hr>" : ""%>
<%--
    The server will undergo maintenance at 11am CET (2am PST), this may take 6hrs to complete.        
        <hr>
--%>
        <ul>
<%--
          <li>Public/Open Invitations have been <a href="http://www.pente.org/gameServer/forums/thread.jspa?forumID=5&threadID=230031&tstart=0">limited</a>.<hr>
          </li>
          <li><font color ="red">Pente.org is going offline at half past the hour.</font> We are moving servers, more updates on our <a href="https://www.facebook.com/pente.org">Facebook page</a>. <hr>
          </li>
          <li>New BK tournament. More information <a href="http://www.pente.org/gameServer/forums/thread.jspa?forumID=1&threadID=3312&start=60&tstart=0">here</a>. (March 17th, 2014)
          </li>
          <li><a href="http://www.pente.org/gameServer/forums/thread.jspa?forumID=1&threadID=230251">King of the Hill!</a> Every Tuesday from 6pm EST (3pm PST, 12am CET).<br>
          Want a <a href="http://www.pente.org/gameServer/forums/thread.jspa?forumID=1&threadID=230250">crown</a>? Come and get it!
          </li>
          <li><a href="/gameServer/forums/thread.jspa?forumID=1&threadID=230403">King of the Hill!</a> Every 3rd Thursday monthly from 6pm EST (3pm PST, 12am CET). Next: May 19th. 
          Prize: a <a href="/gameServer/forums/thread.jspa?forumID=1&threadID=230250">crown</a> and subscriber goodies!
          </li>
--%>
          <li>Looking for <a href="/gameServer/forums/forum.jspa?forumID=34&start=0">resources</a> to get started?
          </li>
            <li>Want to play turn-based? Try posting an <a href="/gameServer/tb/new.jsp">open invitation</a><%= openTBgames > 0 ? " or try " + (openTBgames == 1 ? "" : "one of ") + "the <a href=\"/gameServer/tb/waiting.jsp\">" + openTBgames + " open turn-based invitation" + (openTBgames == 1 ? "" : "s") + "</a>" : ""%>.</li>
            </li>
            <hr>

          <li>16th Anniversary World Champion <a href="/gameServer/tournaments/statusRound.jsp?eid=1184&round=3">tournament</a> - 2015. Round 3 has started! 
          The <a href="/gameServer/forums/forum.jspa?forumID=35&start=0">tournament forum</a> is now opened. 

<%--
            <hr>
          <li>The <a href="https://play.google.com/store/apps/details?id=be.submanifold.pentelive">Android Turn-Based Pente app</a> is finally here. Get your copy today!
          (<a href="/gameServer/forums/thread.jspa?forumID=35&threadID=230731&tstart=0">schedule</a>)
--%>
        </ul>
</div>
<% } %>
</div>
<%-- todo find actual width of avatar in case less than 80 --%>
<div style="margin-left:10px;float:left;width:25%;">
  <a href="/gameServer/myprofile/donor"><img align="right" width="80" style="border:1px solid gray" src="<%= (dsgPlayerData.hasAvatar() ? "/gameServer/avatar?name=" + dsgPlayerData.getName() : "/gameServer/images/no_photo.gif") %>"></a><br>
  <a href="/gameServer/myprofile/donor"><p style="clear:both;text-align:right;font-size:10px">Edit</p></a>
</div>

<div style="width:100%;clear:both;">
<h2 style="background:#e5e5e5">Live Game Room</h2>
</div>

<% if (request.getParameter("jws") != null) { %>

<form name="jws" method="post" action="" style="margin:0;padding:0;">
  <input type="hidden" name="showPopupMessage" value="true">
  <input type="hidden" name="pass" value="http://www.pente.org/<%= request.getContextPath() %>/gameServer/jwsInstall.jsp">
  <input type="hidden" name="fail" value="http://java.sun.com/javase/downloads/ea.jsp">
</form>

<SCRIPT LANGUAGE="JavaScript"> 
var javawsInstalled = 0;  
isIE = "false"; 
if (navigator.mimeTypes && navigator.mimeTypes.length) { 
   x = navigator.mimeTypes['application/x-java-jnlp-file']; 
   if (x) { 
      javawsInstalled = 1; 
  } 
} 
else { 
   isIE = "true"; 
} 
</SCRIPT> 
<SCRIPT LANGUAGE="VBScript">
on error resume next
If isIE = "true" Then
  If Not(IsObject(CreateObject("JavaWebStart.isInstalled"))) Then
     javawsInstalled = 0
  Else
     javawsInstalled = 1
  End If
End If
</SCRIPT>

<script language="javascript">

function goJws() {
  // send to the jnlp file, load it up
  if (javawsInstalled || (navigator.userAgent.indexOf("Gecko") !=-1)) {
    document.jws.action="/gameServer/pente.jnlp";
    document.jws.method="get";
  }
  // try to autoinstall
  else {
    document.jws.action="http://java.sun.com/PluginBrowserCheck";
    document.jws.method="get";
  }
      
  document.jws.submit();
}
addLoadEvent(goJws);
</script>


<% } %>

<% 
  LoginCookieHandler handler = new LoginCookieHandler();
  handler.loadCookie(request);
  
  boolean plugin = true;
  if (handler.pluginChoiceMade() && !handler.usePlugin()) {
      plugin = false;
  } 
%>

<script language="javascript" src="/gameServer/js/openwin.js"></script>
<script type="text/javascript">
   function play() {
       handlePlay('<%= plugin %>', document.mainPlayForm.gameRoomSize.options[document.mainPlayForm.gameRoomSize.selectedIndex].value, false);
   }
</script>













 <table style="width:100%">
  <tr>
    <td style="width: 72%;">
      
 <form name="mainPlayForm" method="post" action="" style="margin:0;padding:0;">
<div class="buttonwrapper">

    <a class="boldbuttons" href="javascript:void(0);" 
       style="float:left;margin-right:5px;" 
       onClick="javascript:play();"><span>Join Game Room</span></a>
       
   <div style="margin-top:5px;">
    
    <%-- no sense making players choose when only one choice --%>
    <% if (resources.getServerData().size() == 1) { %>
      <input type="hidden" name="port" value="<%= ((ServerData) resources.getServerData().get(0)).getPort() %>">
    <% } else { %>
      <select name="lobbies">
      <% for (Iterator it = resources.getServerData().iterator(); it.hasNext();) {
             ServerData data = (ServerData) it.next(); %>
             <option value="<%= data.getPort() %>"><%= data.getName() %></option>
      <% } %>
    <% } %>
    </select>
    Size: <select name="gameRoomSize">
        <option value="640" <% if (grs.equals("640")) { %>selected<% } %>>640x480</option>
        <option value="800" <% if (grs.equals("800")) { %>selected<% } %>>800x600</option>
    </select>

   </div>
</div>

</form>

<div style="margin-top:5px;">
     or <a href="/gameServer/index.jsp?jws=1"><span>install</span></a> the game room on your desktop
</div>
<%--
--%>
   </td>
      






<%
SessionListener sessionListener = (SessionListener) application.getAttribute(SessionListener.class.getName());
List<WhosOnlineRoom> rooms = WhosOnline.getPlayers(globalResources, sessionListener);

boolean inLiveGameRoom = false;
for (int i = 0; i < rooms.size(); i++) {
    WhosOnlineRoom room = rooms.get(i);
    if (room.getName().equals("web")) {
      continue;
    }

    for (DSGPlayerData d : room.getPlayers()) {
      if (d.getName().equals(nm)) {
        inLiveGameRoom = true;
        break;
      }
    }
    if (inLiveGameRoom) {
      break;
    }
}

if (inLiveGameRoom) {
  MessageDigest md = MessageDigest.getInstance("SHA-256");
  String text = "pente seeds-" + dsgPlayerData.getPlayerID();
  md.update(text.getBytes("UTF-8")); 
  String checkHash = new String(Hex.encodeHex( md.digest() ));
  %>
    <td style="width: 18%;">
    <div class="buttonwrapper">
      <a class="boldbuttons" href="bootMe.jsp?name=<%= nm %>&pidHash=<%= checkHash %>" 
         style="margin-right:5px;"><span>Boot me NOW!!!</span></a>
    </div>
</td>
<%
}
%>



  </tr>
</table> 






<%--
<% if (!dsgPlayerData.hasPlayerDonated()) { %>
<div align="left">
<%@include file="dashboardad.jsp" %>
</div>
<br>
<% } %>
--%>


<div style="width:100%;height:175px;margin-top:10px;">
  <div style="width:43%;float:left;border:1px solid black;height:100%;overflow:auto;">
      <h2 style="background: #e5e5e5"><span style="padding-left:5px;">In the <a href="/gameServer/forums"><span>Forums</span></a></span></h2>
<%--
--%>
      <div style="padding-left:5px;">
      <%@ include file="jivePopularTemplate.jsp" %>
      <br>
        <a class="boldbuttons" href="/gameServer/forums/post!default.jspa?forumID=1"><span>Post a Message</span></a>
      <br>
      </div>

  </div>
  <div style="width:55%;float:right;border:1px solid #ffd0a7;;height:100%;">
  <h2 style="color:white;background:#ff8105"><span style="padding-left:5px;">Strategy Center</span></h2>
  <table style="padding-left:5px;">
   <tr>
    <td width="170px">
    <div class="buttonwrapper">
      <a class="boldbuttons" href="/gameServer/controller/search?quick_start=1" 
         style="margin-right:5px;"><span>Game Database</span></a>
    </div>
    </td>
    <td style="vertical-align:middle">
      Search and filter <span style="color:<%= textColor2 %>;font-weight:bold"><%= numberFormat.format(siteStatsData.getNumGames()) %></span> games by position
    </td>
   </tr>
   <tr>
    <td>
    <div class="buttonwrapper" style="margin-top:5px;">
      <a class="boldbuttons" href="javascript:play();" 
         style="margin-right:5px;"><span>Play the Computer</span></a>
    </div>
    </td>
    <td style="vertical-align:middle">
     Challenge the tough computer opponent with 8 skill levels
    </td>
   </tr>
   <tr>
    <td>
    <div class="buttonwrapper" style="margin-top:5px;">
        <a class="boldbuttons" href="/gameServer/strategy.jsp" 
           style="margin-right:5px;"><span>Tutorials</span></a> 
      <a class="boldbuttons" href="/gameServer/puzzle.jsp" 
         style="margin-right:5px;"><span>Puzzles</span></a>
    </div>
    </td>
    <td style="vertical-align:middle">
      Learn the basics with the tutorials and challenge yourself with some fun puzzles
    </td>
   </tr>
  </table>    
  </div>
  <div style="clear:both"></div>
</div>
<br>

<div style="width:100%;margin-top:10px">
<h2 style="background:#e5e5e5">My Turn-Based Games</h2> 
</div>

<%--
--%>



<table border="0" cellpadding="0" cellspacing="0" width="100%">
  <tr>
   <td align="left" colspan="2">
      <div class="buttonwrapper">
       <a class="boldbuttons" href="/gameServer/tb/new.jsp"><span>Start a New Game</span></a> <a class="boldbuttons" href="/gameServer/tb/waiting.jsp" style="margin-right:6px; margin-left: 6px"><span>Find an Open Game <b>(<%=openTBgames %>)</b><a class="boldbuttons" href="/gameServer/tb/newAIgame.jsp" style="margin-right:6px"><span>Play the TB AI</span></a></span></a> 

       <div style="margin-top:7px;">
          Active games: <b><%= numberFormat.format(siteStatsData.getNumTbGames()) %></b>, Open TB games: <b><%=openTBgames %></b><%-- --%>
       </div>
      </div>
   </td>
   <td width="5">&nbsp;</td>
 </tr>
<% if (invitesTo.isEmpty() && invitesFrom.isEmpty() && myTurn.isEmpty() && oppTurn.isEmpty()) { %>
  <tr>
   <td align="left" colspan="3">
    <br>You have no active turn-based games.
   </td>
  </tr>
<% } else { %>
 <tr>
   <td colspan="2"><br>
     <% if (!invitesTo.isEmpty()) { %>

     <table border="0"  cellspacing="0" cellpadding="0" width="100%">
       <tr bgcolor="<%= textColor2 %>">
         <td colspan="5">
           <font color="white">
             <b>Invitations received (<%= invitesTo.size() %>) <%=(limitExceeded?"(Free account limit reached)":"")%></b>
           </font>
         </td>
       </tr>
       <tr>
         <td><b>Game</b></td>
         <td><b>Opponent</b></td>
         <td><b>Play as</b></td>
         <td><b>Time/Move</b></td>
         <td><b>Rated</b></td>
       </tr>
     <% for (TBSet s : invitesTo) {
         String color = null;
         if (s.isTwoGameSet()) {
             color = "white,black (2 game set)";
         }
         else if (dsgPlayerData.getPlayerID() == s.getPlayer1Pid()) {
             color = "white (p1)";
         }
         else {
             color = "black (p2)";
         }
         DSGPlayerData d = dsgPlayerStorer.loadPlayer(s.getInviterPid());
         DSGPlayerGameData dsgPlayerGameData = d.getPlayerGameData(s.getGame1().getGame());
         %>
         <tr>
          <td>
<!--           <%  if (limitExceeded) { %>
           <%= GridStateFactory.getGameName(s.getGame1().getGame()) %>
          <%} else {%>
           <a href="/gameServer/tb/replyInvitation?command=load&sid=<%= s.getSetId() %>">
             <%= GridStateFactory.getGameName(s.getGame1().getGame()) %></a>
          <%}%>
 -->           <a href="/gameServer/tb/replyInvitation?command=load&sid=<%= s.getSetId() %>">
             <%= GridStateFactory.getGameName(s.getGame1().getGame()) + (s.getGame1().getEventId()==kothStorer.getEventId(s.getGame1().getGame())?" (KotH)":"")%></a>
          </td>
           <td><%@include file="playerLink.jspf" %><%@ include file="ratings.jspf" %></td>
           <td><%= color %></td>
           <td><%= s.getGame1().getDaysPerMove() %> days</td>
           <td><%= s.getGame1().isRated() ? "Rated" : "Not Rated" %></td>
         </tr>
     <% } %>
     </table>
     <br>
     <% } %>
     
     <% if (!invitesFrom.isEmpty()) { %>
     <table border="0"  cellspacing="0" cellpadding="0" width="100%">
       <tr bgcolor="<%= bgColor2 %>">
         <td colspan="5">
           <b>Invitations sent (<%= invitesFrom.size() %>)</b>
         </td>
       </tr>
       <tr>
         <td><b>Game</b></td>
         <td><b>Opponent</b></td>
         <td><b>You are</b></td>
         <td><b>Time/Move</b></td>
         <td><b>Rated</b></td>
       </tr>
     <% for (TBSet s : invitesFrom) {
         String color = null;
         if (s.isTwoGameSet()) {
             color = "white,black (2 game set)";
         }
         else if (dsgPlayerData.getPlayerID() == s.getPlayer1Pid()) {
             color = "white (p1)";
         }
         else {
             color = "black (p2)";
         }
         long pid = s.getInviteePid();
         DSGPlayerGameData dsgPlayerGameData = null;
         DSGPlayerData d = null;
         String anyoneString = "Anyone";
         if (pid != 0) {
             d = dsgPlayerStorer.loadPlayer(pid);
             dsgPlayerGameData = d.getPlayerGameData(s.getGame1().getGame());
         } else {
              DSGPlayerGameData myGameData = null;
              int myRating = 1600;
              if (s.getInvitationRestriction() != TBSet.ANY_RATING) {
                  myGameData = dsgPlayerData.getPlayerGameData(s.getGame1().getGame());
                  if (myGameData != null && myGameData.getTotalGames() > 0) {
                      myRating = (int) Math.round(myGameData.getRating());
                  }
              }
              if (s.getInvitationRestriction() == TBSet.ANYONE_NOTPLAYING) {
                  anyoneString += " (new opponents)";
              }
              if (s.getInvitationRestriction() == TBSet.LOWER_RATING) {
                  anyoneString += " under " + myRating;
              }
              if (s.getInvitationRestriction() == TBSet.HIGHER_RATING) {
                  anyoneString += " over " + myRating;
              }
              if (s.getInvitationRestriction() == TBSet.SIMILAR_RATING) {
                  anyoneString += " similar";
              }
              if (s.getInvitationRestriction() == TBSet.CLASS_RATING) {
                  SimpleDSGPlayerGameData tmpData = new SimpleDSGPlayerGameData();
                  anyoneString += " <img src=\"/gameServer/images/" + tmpData.getRatingsGifRatingOnly(myRating) + "\">";
              }
         }
         %>
         <tr>
           <td><a href="/gameServer/tb/cancelInvitation?command=load&sid=<%= s.getSetId() %>">
               <%= GridStateFactory.getGameName(s.getGame1().getGame()) + (s.getGame1().getEventId()==kothStorer.getEventId(s.getGame1().getGame())?" (KotH)":"")%></a></td>
           <td><% if (pid == 0) { %> <%=anyoneString%> <% } else {%><%@include file="playerLink.jspf" %><% } %><% if (dsgPlayerGameData != null) { %><%@ include file="ratings.jspf" %><% } %></td>
           <td><%= color %></td>
           <td><%= s.getGame1().getDaysPerMove() %> days</td>
           <td><%= s.getGame1().isRated() ? "Rated" : "Not Rated" %></td>
         </tr>
     <% } %>
     </table>
     
     <br>
     <% } %>

     <% if (!myTurn.isEmpty()) { %>

     <table border="0"  cellspacing="0" cellpadding="0" width="100%">
       <tr bgcolor="<%= textColor2 %>">
         <td colspan="6">
           <font color="white">
             <b>Active Games - My Turn (<%= myTurn.size() %>)</b>
           </font>
         </td>
       </tr>
       <tr>
         <td><b>Game</b></td>
         <td><b>Opponent</b></td>
         <td><b>You are</b></td>
         <td><b>Move</b></td>
         <td><b>Time Left</b></td>
         <td><b>Rated</b></td>
       </tr>
     <% for (TBGame g : myTurn) {
         String color =  dsgPlayerData.getPlayerID() == g.getPlayer1Pid() ?
             "white (p1)" : "black (p2)";
         long oppPid = dsgPlayerData.getPlayerID() == g.getPlayer1Pid() ?
             g.getPlayer2Pid() : g.getPlayer1Pid();
         DSGPlayerData d = dsgPlayerStorer.loadPlayer(oppPid);
         DSGPlayerGameData dsgPlayerGameData = d.getPlayerGameData(g.getGame());
     %>
           
         <tr>
           <td>
         <!-- <a href="javascript:goWH('/gameServer/tb/game?gid=<%= g.getGid() %>&command=load&mobile');"><img src="/gameServer/images/mobile.png" title="Without Java" height="12" width="12"></a> -  -->
         <a href="javascript:goWH('/gameServer/tb/game?gid=<%= g.getGid() %>&command=load&mobile');"><%= GridStateFactory.getGameName(g.getGame()) + (g.getEventId()==kothStorer.getEventId(g.getGame())?" (KotH)":"")%></a>
<!--
          - 
           (<a href="javascript:goWH('/gameServer/tb/game?gid=<%= g.getGid() %>&command=load');"><img src="/gameServer/images/java.png" title="With Java" height="14" width="14"></a>)
-->           
             </td>
           <td><%@ include file="playerLink.jspf" %>&nbsp;<% if (dsgPlayerGameData != null) { %><%@ include file="ratings.jspf" %><% } %></td>
           <td><%= color %></td>
           <td><%= g.getNumMoves() + 1 %></td>
           <td><%= Utilities.getTimeLeft(g.getTimeoutDate().getTime()) %></td>
           <td><%= g.isRated() ? "Rated" : "Not Rated" %></td>
         </tr>
     <% } %>
     </table>

     <br>
     <% } %>
     
     <% if (!oppTurn.isEmpty()) { %>
     <table border="0"  cellspacing="0" cellpadding="0" width="100%">
       <tr  bgcolor="<%= bgColor1 %>">
         <td colspan="6">
           <font color="white">
             <b>Active Games - Opponents Turn (<%= oppTurn.size() %>)</b>
           </font>
         </td>
       </tr>
       <tr>
         <td><b>Game</b></td>
         <td><b>Opponent</b></td>
         <td><b>You are</b></td>
         <td><b>Move</b></td>
         <td><b>Time Left</b></td>
         <td><b>Rated</b></td>
       </tr>
     <% for (TBGame g : oppTurn) {
         String color =  dsgPlayerData.getPlayerID() == g.getPlayer1Pid() ?
             "white (p1)" : "black (p2)";
         long oppPid = dsgPlayerData.getPlayerID() == g.getPlayer1Pid() ?
             g.getPlayer2Pid() : g.getPlayer1Pid();
         DSGPlayerData d = dsgPlayerStorer.loadPlayer(oppPid);
         DSGPlayerGameData dsgPlayerGameData = d.getPlayerGameData(g.getGame());
      %>
           
         <tr>
           <td>
         <!-- <a href="javascript:goWH('/gameServer/tb/game?gid=<%= g.getGid() %>&command=load&mobile');"><img src="/gameServer/images/mobile.png" title="Without Java" height="12" width="12"></a> -  -->
         <a href="javascript:goWH('/gameServer/tb/game?gid=<%= g.getGid() %>&command=load&mobile');"><%= GridStateFactory.getGameName(g.getGame()) + (g.getEventId()==kothStorer.getEventId(g.getGame())?" (KotH)":"")%></a> 
<!--
- 
           (<a href="javascript:goWH('/gameServer/tb/game?gid=<%= g.getGid() %>&command=load');"><img src="/gameServer/images/java.png" title="With Java" height="14" width="14"></a>)
-->
             </td>
           <td><%@ include file="playerLink.jspf" %></a>&nbsp;<% if (dsgPlayerGameData != null) { %><%@ include file="ratings.jspf" %><% } %></td>
           <td><%= color %></td>
           <td><%= g.getNumMoves() + 1 %></td>
           <td><%= Utilities.getTimeLeft(g.getTimeoutDate().getTime()) %></td>
           <td><%= g.isRated() ? "Rated" : "Not Rated" %></td>
         </tr>
     <% } %>
     </table>
     
     <% } %>

    </td>
    
   <td width="5">&nbsp;</td>
  </tr>
<% } %>
</table>
<br>

<%--
<div style="width:100%;">
<h2 style="background:#e5e5e5">My Profile</h2>
</div>
<a href="/gameServer/myprofile">Edit Profile</a> | <a href="/gameServer/mymessages">My Messages</a><br>
<br>
--%>

<% if (dsgPlayerData.getTotalGames() == 0) { %>
  You haven't completed any rated games yet.<br>
<% } else {
int tourneyWinner = 0; %>
<%@ include file="playerstatsbox.jsp" %>
<% } %>
<br>
</td>
<td valign="top" align="right" >
<%@ include file="leaderboard.jsp" %>

<% if (dsgPlayerData.showAds()) { %>
<div class="box" style="background-color:white; border: 1px solid white;">
<%@ include file="dash200ad.jsp" %>
</div>
<% } %>
<%--
--%>

<%@ include file="whobox.jsp" %>
<%@ include file="social.jsp" %>
<%@ include file="mobile.jsp" %>
<%@ include file="donorsbox.jsp" %>
<%@ include file="statbox.jsp" %>
<%@ include file="end.jsp" %>
