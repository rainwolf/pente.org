<%
String invitee = request.getParameter("invitee");
if (invitee == null) {
    invitee = "";
}
%>
<% pageContext.setAttribute("title", "New Game"); %>
<%@ include file="../begin.jsp" %>

<%
Resources resources = (Resources) application.getAttribute(
   Resources.class.getName());

String name = (String) request.getAttribute("name");
DSGPlayerData meData = dsgPlayerStorer.loadPlayer(name);

String gameStr = request.getParameter("game");

List<DSGPlayerPreference> prefs = dsgPlayerStorer.loadPlayerPreferences(meData.getPlayerID());
int tbTimeout = 7;
int tbGame = 51;
if (gameStr != null) {
  tbGame = Integer.parseInt(gameStr);
}
String tbRestriction = "A";
for (DSGPlayerPreference pref: prefs) {
  if ("tbTimeout".equals(pref.getName())) {
    tbTimeout = (Integer) pref.getValue();
  }
  if (gameStr == null && "tbGame".equals(pref.getName())) {
    tbGame = (Integer) pref.getValue();
  }
  if ("tbRestriction".equals(pref.getName())) {
    tbRestriction = (String) pref.getValue();
  }
}

TBGameStorer tbGameStorer = resources.getTbGameStorer();
List<TBSet> waitingSets = tbGameStorer.loadWaitingSets();
List<TBSet> currentSets = tbGameStorer.loadSets(meData.getPlayerID());
List<TBSet> invitesTo = new ArrayList<TBSet>();
List<TBSet> invitesFrom = new ArrayList<TBSet>();
List<TBGame> myTurn = new ArrayList<TBGame>();
List<TBGame> oppTurn = new ArrayList<TBGame>();
Utilities.organizeGames(meData.getPlayerID(), currentSets,
    invitesTo, invitesFrom, myTurn, oppTurn);


boolean limitExceeded = false;
// int gamesLimit = 2000;
// if (meData.unlimitedTBGames()) {
//   limitExceeded = false;
// } else {
//   int currentCount = myTurn.size() + oppTurn.size();
//   if (!invitesFrom.isEmpty()) {
//     for (TBSet s : invitesFrom) {
//       if (s.isTwoGameSet()) {
//         currentCount += 2;
//       } else {
//         currentCount++;
//       }
//     }
//   }
//   if (currentCount > gamesLimit) {
//     limitExceeded = true;
//   } else {
//     limitExceeded = false;
//   }
// }

%>


<script language="javascript">
var ns4 = (document.layers) ? true : false;
var ie4 = (document.all) ? true : false;
var ns6 = ((navigator.vendor) && (navigator.vendor.indexOf("Netscape6"))) != -1;

function changeRated(rated) {

    if (rated == 'Y') {
        hide('unrated');
    }
    else {
        show('unrated');
    }
    
    return true;
}

function hide(id) {

    if (ns4) {
        document.layers[id].visibility = "hide";
    }
    else if (ie4) {
        document.all[id].style.visibility = "hidden";
    }
    else if (ns6) {
        document.getElementById(id).style.visibility = "hidden";
    } else {
        document.getElementById(id).style.visibility = "hidden";
    }
}

function show(id) {

    if (ns4) {
        document.layers[id].visibility = "show";
    }
    else if (ie4) {
        document.all[id].style.visibility = "visible";
    }
    else if (ns6) {
        document.getElementById(id).style.visibility = "visible";
    } else {
        document.getElementById(id).style.visibility = "visible";
    }
}

function SelectElement(valueToSelect)
{    
    var element = document.getElementById('game');
    element.value = valueToSelect;
}

</script>

<script type="text/javascript">
function submitnewgameform()
{
  document.new_game_form.submit();
}
</script>

<table align="left" width="100%" border="0" colspacing="1" colpadding="1">

<tr>
 <td>
  <h3>Start a new turn-based game</h3> 
     
     <div align="left" style="position:relative;font-weight:bold;border:2px <%= textColor2 %> solid; background:#ffd0a7">
       Pente.org turn-based games that are rated must be
     played in a set of two games to make the ratings system fair (most games give
     player 1 a slight advantage).  If you choose rated below Pente.org will create a
     set of two games.<br>
     <br>
     If you play unrated, you can choose to play as white or black and choose
     to make the game private.
     </div> 
     <br>
     <div align="left" style="position:relative;font-weight:bold;border:2px <%= textColor2 %> solid; background:#ffd0a7">
     - Players in your Ignored list will not be able to see or accept your open invitations.
     </div> 
     <br>
     <font color="red"><b>New: </b></font>Beginner invitations, just like regular ones, except that when your opponent accepts such an invitation, the server will post an identical one in their name
     <br>
     &nbsp
     <br>
 </td>
</tr>
<%
if (limitExceeded) {%>
<tr>
 <td>
     <div align="left" style="position:relative;font-weight:bold;border:2px <%= textColor2 %> solid; background:#ffd0a7">
     You have reached the limit of games you can play simultaneously on a free account. You can only send open invitations until you finish some games.
     This limit can be removed by becoming a <a href="../subscriptions">subscriber </a>.
     </div> 
     <br>
 </td>
</tr>

<%}%>

<% String error = (String) request.getAttribute("error");
   if (error != null) { %>

<tr>
 <td>
  <b><font face="Verdana, Arial, Helvetica, sans-serif" size="2" color="#8b0000">
   Creating set failed: <%= error %>
  </font></b>
 </td>
</tr>

<%   
   }
%>

<tr>
 <td>

   <form name="new_game_form" method="post" 
         action="<%= request.getContextPath() %>/gameServer/tb/newGame">
   <table border="0"  cellspacing="0" cellpadding="1"   bordercolor="black">

     <tr width="400">
      <td width="200">
       <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
        Player to invite:
       </font>
      </td>
      <td>
       <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
<%
if (!limitExceeded) {%>
        <input type="text" name="invitee" size="10"
               maxlength="10" value="<%= invitee %>"> (leave blank for open invitation)
<%} else {%>
        <input type="hidden" name="invitee" size="10"
               maxlength="10" value="<%= invitee %>"> (You can currently only post open invitations)
<%}%>
       </font>
      </td>
     </tr>
     <tr>
      <td>
       <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
        Game:
       </font>
      </td>
      <td>
       <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
         <select size="1" id="game" name="game">
         <% Game games[] = GridStateFactory.getTbGames();
            for (int i = 0; i < games.length; i++) { %>
               <option <%=(tbGame==games[i].getId()?"selected":"")%> value="<%= games[i].getId() %>"><%= games[i].getName() %></option>
<%--
               <option <% if (i == 0) { %>selected <% } %>value="<%= games[i].getId() %>"><%= games[i].getName() %></option>
--%>               
         <% } %>
       </font>
       </select>
       
     
       
      </td>
     </tr>
     <tr>
      <td>
       <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
        Days per move:
       </font>
      </td>
      <td>
       <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
        <select name="daysPerMove" size="1">
        <% for (int i = 1; i < 31; i++) { %>         
         <option <%= (i == tbTimeout)?"selected":"" %> value="<%= i %>"><%= i %> Days</option>
        <% } %>
        </select>
       </font>
      </td>
     </tr>
     <tr>
      <td>
       <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
        Open to players of ...
       </font>
      </td>
      <td>
       <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
        <select name="invitationRestriction" size="1">
          <option <%= ("A".equals(tbRestriction)?"selected":"") %> value="A">Any rating</option>
          <option <%= ("B".equals(tbRestriction)?"selected":"") %> value="B">Beginners</option>
          <option <%= ("N".equals(tbRestriction)?"selected":"") %> value="N">Not already playing</option>
          <option <%= ("L".equals(tbRestriction)?"selected":"") %> value="L">Lower rating</option>
          <option <%= ("H".equals(tbRestriction)?"selected":"") %> value="H">Higher rating</option>
          <option <%= ("S".equals(tbRestriction)?"selected":"") %> value="S">Similar rating (&plusmn 100)</option>
          <option <%= ("C".equals(tbRestriction)?"selected":"") %> value="C">Same rating class</option>
        </select>
       </font> (This only affects open invitations)
      </td>
     </tr>
     <tr>
      <td>
       <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
        Rated game:
       </font>
      </td>
      <td>
        <select size="1" name="rated" onchange="javascript:changeRated(this.options[this.selectedIndex].value);">
         <option selected value="Y">Yes</option>
         <option value="N">No</option>
        </select>
      </td>
     </tr>
     <tr>
       <td colspan="2">
         <div id="unrated" visibility="hide" style="visibility:hidden">
         <table border="0" cellspacing="0" cellpadding="0">
          <tr>
           <td width="150">
         <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
           Play as color:
         </font>
           </td>
           <td>
           <select size="1" name="playAs">
             <option value="1">White</option>
             <option value="2">Black</option>
  
           </select>
         </td>
        </tr>
       <tr>
        <td>
         <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
          Private game:
         </font>
        </td>
        <td>
          <select size="1" name="privateGame">
           <option selected value="N">No</option>
           <option value="Y">Yes</option>
          </select>
        </td>
       </tr>
       </table>
       </div>
     </td>
     </tr>
     <tr>
      <td colspan="2">
      <label><input id="remember" name="remember" type="checkbox" value="yes"/> remember my settings </label>
      </td>
     </tr>
   </table>
   <br>

    Message (Optional, 255 character max):<br>
    <textarea cols="50" rows="3" name="inviterMessage"></textarea><br><br>

        <a class="boldbuttons" href="javascript: submitnewgameform()" style="margin-right:6px; margin-left: 6px"><span>Create Game Invitation</span></a>
    

   
   </form>
 </td>
</tr>

</table>

    <br>
    <br>

<%@ include file="../end.jsp" %>