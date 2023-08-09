<%
   String invitee = request.getParameter("invitee");
   if (invitee == null) {
      invitee = "";
   }
%>
<% pageContext.setAttribute("title", "New Game"); %>
<%@ include file="../begin.jsp" %>

<script language="javascript">
   var ns4 = (document.layers) ? true : false;
   var ie4 = (document.all) ? true : false;
   var ns6 = ((navigator.vendor) && (navigator.vendor.indexOf("Netscape6"))) != -1;

   function changeRated(rated) {

      if (rated == 'Y') {
         hide('unrated');
      } else {
         show('unrated');
      }

      return true;
   }

   function hide(id) {

      if (ns4) {
         document.layers[id].visibility = "hide";
      } else if (ie4) {
         document.all[id].style.visibility = "hidden";
      } else if (ns6) {
         document.getElementById(id).style.visibility = "hidden";
      }
   }

   function show(id) {

      if (ns4) {
         document.layers[id].visibility = "show";
      } else if (ie4) {
         document.all[id].style.visibility = "visible";
      } else if (ns6) {
         document.getElementById(id).style.visibility = "visible";
      }
   }

   function SelectElement(valueToSelect) {
      var element = document.getElementById('game');
      element.value = valueToSelect;
   }

</script>

<table align="left" width="100%" border="0" colspacing="1" colpadding="1">

   <tr>
      <td>
         <h3>Start a new turn-based game</h3>

         <div align="left"
              style="position:relative;font-weight:bold;border:2px <%= textColor2 %> solid; background:#ffd0a7">
            Pente.org turn-based games that are rated must be
            played in a set of two games to make the ratings system fair (most games give
            player 1 a slight advantage). If you choose rated below Pente.org will create a
            set of two games.<br>
            <br>
            If you play unrated, you can choose to play as white or black and choose
            to make the game private.
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
            <table border="0" cellspacing="0" cellpadding="1" bordercolor="black">

               <tr width="400">
                  <td width="150">
                     <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
                        Player to invite:
                     </font>
                  </td>
                  <td>
                     <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
                        <input type="text" name="invitee" size="10"
                               maxlength="10" value="<%= invitee %>"> (leave blank for open invitation)
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
                           <option value="<%= games[i].getId() %>"><%= games[i].getName() %>
                           </option>
                           <%--
                                          <option <% if (i == 0) { %>selected <% } %>value="<%= games[i].getId() %>"><%= games[i].getName() %></option>
                           --%>
                              <% } %>
                     </font>

                     <script type="text/javascript">
                        SelectElement(<%=request.getParameter("game") %>);
                     </script>


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
                           <option <% if (i == 7) { %>selected <% } %>value="<%= i %>"><%= i %> Days</option>
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
                     <select size="1" name="rated"
                             onchange="javascript:changeRated(this.options[this.selectedIndex].value);">
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
            </table>
            <br>

            Message (Optional, 255 character max):<br>
            <textarea cols="50" rows="3" name="inviterMessage"></textarea><br>

            <input type="submit" value="Create Game">


         </form>
      </td>
   </tr>

</table>


<%@ include file="../end.jsp" %>