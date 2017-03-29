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
  <h3>Start a new turn-based game against <a href="/gameServer/profile?viewName=cropleyb">
            Bruce Cropley</a>'s AI player.</h3> 
     
     Follow the links for more <a href="http://submanifold.be/Bruce%20Cropley%20-%20PentAI.html" target="info">information</a> and <a href="https://github.com/cropleyb/pentai" target="source">source code</a>.
     <br>
     You can also play this AI on your <a href="http://submanifold.be/Bruce%20Cropley%20-%20PentAI_files/pentai.zip" target="ms">Windows computer</a> or <a href="http://submanifold.be/Bruce%20Cropley%20-%20PentAI_files/PentAI.dmg" target="mac">mac</a>.
     <br>
     <br>
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
        <input type="text" size="10"
               maxlength="10" value="computer" disabled>
       </font>
        <input type="hidden" name="invitee" size="10"
               maxlength="10" value="computer">
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
            for (int i = 0; i < games.length; i++) {
            if (games[i].getId() != 51) continue; %>
               <option value="<%= games[i].getId() %>"><%= games[i].getName() %></option>
<%--
               <option <% if (i == 0) { %>selected <% } %>value="<%= games[i].getId() %>"><%= games[i].getName() %></option>
--%>               
         <% } %>
         </select>
       </font>
       
    <script type="text/javascript">
      <% if (request.getParameter("game") != null) { %>
        SelectElement(<%=request.getParameter("game") %>);
      <% } else { %>
        SelectElement(51);
        <% } %>
    </script>
       
       
      </td>
     </tr>
     <tr>
      <td>
       <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
        Difficulty:
       </font>
      </td>
      <td>
       <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
        <select name="difficulty" size="1">
        <% for (int i = 1; i < 11; i++) { %>         
         <option value="<%= i %>"><%= i %></option>
        <% } %>
        </select>
       </font>
      </td>
     </tr>
        <input type="hidden" name="daysPerMove" value="30">
     <tr>
      <td>
       <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
        Set game:
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
       <td colspan="3">
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
       </table>
       </div>
     </td>
     </tr>
   </table>
   <br>

<br>

        <a class="boldbuttons" href="javascript: submitnewgameform()" style="margin-right:6px; margin-left: 6px"><span>Create Game Invitation</span></a>
    

   
   </form>
 </td>
</tr>

</table>

    <br>
    <br>

<%@ include file="../end.jsp" %>