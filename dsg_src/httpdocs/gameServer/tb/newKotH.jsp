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
    List<DSGPlayerPreference> prefs = dsgPlayerStorer.loadPlayerPreferences(meData.getPlayerID());
    int tbTimeout = 7;
    String tbRestriction = "A";
    for (DSGPlayerPreference pref: prefs) {
        if ("tbKotHTimeout".equals(pref.getName())) {
            tbTimeout = (Integer) pref.getValue();
        }
        if ("tbKotHRestriction".equals(pref.getName())) {
            tbRestriction = (String) pref.getValue();
        }
    }


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
  <h3>Start a new King of the Hill turn-based game</h3> 
     
     King of the Hill games are always rated, and the player name or game cannot be changed here anymore. Please select a timeout for your game and send the invitation.
     <br>
     <br>

     <div align="left" style="position:relative;font-weight:bold;border:2px <%= textColor2 %> solid; background:#ffd0a7">
     </div> 
     <br>
 </td>
</tr>

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
        <input type="text" name="invitee" size="10"
               maxlength="10" value="<%= invitee %>" disabled="true"> 
        <input type="hidden" name="invitee" value="<%= invitee %>">
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
         <select size="1" id="game" name="game" disabled="true">
         <% Game games[] = GridStateFactory.getTbGames();
            for (int i = 0; i < games.length; i++) { %>
               <option value="<%= games[i].getId() %>"><%= games[i].getName() %></option>
<%--
               <option <% if (i == 0) { %>selected <% } %>value="<%= games[i].getId() %>"><%= games[i].getName() %></option>
--%>               
         <% } %>
       </font>
       
    <script type="text/javascript">
        SelectElement(<%=request.getParameter("game") %>);
    </script>
       
        <input type="hidden" name="game" value="<%= request.getParameter("game") %>">
       
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
        Rated game:
       </font>
      </td>
      <td>
        <select size="1" name="rated" disabled="true">
         <option selected value="Y">Yes</option>
         <option value="N">No</option>
        </select>
        <input type="hidden" name="rated" value="Y">
      </td>
     </tr>
     <% if (invitee.equals("")) { %>
     <tr>
      <td>
       <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
        Open to players
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
       </font>
      </td>
     </tr>
     <%}%>
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

<!--     <textarea cols="50" rows="3" name="inviterMessage"></textarea><br><br>
 -->
        <a class="boldbuttons" href="javascript: submitnewgameform()" style="margin-right:6px; margin-left: 6px"><span>Create Game Invitation</span></a>
    <input type="hidden" name="koth">

   
   </form>
 </td>
</tr>

</table>

    <br>
    <br>

<%@ include file="../end.jsp" %>