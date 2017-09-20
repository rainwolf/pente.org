<%@ page import="java.util.*,
                 java.text.*,
                 org.pente.game.*, 
                 org.pente.gameServer.core.*, 
                 org.pente.gameServer.client.web.*" %>

<% int cg = 51;
   String cgStr = LoginCookieHandler.getCookie(request, "g");
   if (cgStr != null) {
	   try { cg = Integer.parseInt(cgStr); } catch (NumberFormatException n) {}
   }
%>
<style type="text/css">
  .box { width:200px; }
</style>

<script type="text/javascript" src="/gameServer/js/leaders.js"></script>

<div class="box">
  <div class="boxhead">
    <h4>Leader Board</h4>
    <select id="game" onchange="javascript:load();" style="font-size:10pt;">    
		<% for (Game g : GridStateFactory.getDisplayGames()) { %>
		<option value="<%= g.getId() %>" <% if (cg == g.getId()) {%>selected<% } %>><%= g.getName() %></option>
		<% } %>
    </select>
  </div>
  <div id="lb" class="boxcontents"> 
   <% pageContext.setAttribute("g", cg); %>   
   <%@ include file="leaders.jsp" %>
   </div>
</div>
<form name="rank" id="rank" action="/gameServer/stats" method="POST" style="margin:0;padding:0">
<input type="hidden" name="game" value="<%= cg %>">
<input type="hidden" name="sortField" value="2">
<input type="hidden" name="playerType" value="H">
<input type="hidden" name="length" value="25">
<input type="hidden" name="startNum" value="0">
<input type="hidden" name="command" value="playerStats">
</form>