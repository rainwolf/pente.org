<%@ page contentType="text/html; charset=UTF-8" %>
<%@ page import="org.pente.gameServer.core.*, com.jivesoftware.util.*" %>

<% if (request.getAttribute("name") != null) {
	response.sendRedirect("gameServer/index.jsp");
   } %>


<% pageContext.setAttribute("current", "Join"); %>
<% pageContext.setAttribute("title", "Play Pente Here"); %>
<%@ include file="top.jsp" %>

<style type="text/css">
#jointable tr { height:30px; vertical-align:top; }
#jointable input { font-size: 20px; }
body,div,dl,dt,dd,ul,ol,li,h1,h2,h3,h4,h5,
h6,pre,form,fieldset,input,p,blockquote,table,
th,td {margin:0;padding:0;}
fieldset,img,abbr {border:0;}
address,caption,code,dfn,h1,h2,h3,
h4,h5,h6,th,var {font-style:normal;font-weight:normal;}
caption,th {text-align:left;}
q:before,q:after {content:'';}
a {text-decoration:none;}
</style>

<script language="javascript"
        src="/gameServer/js/openwin.js">
</script>

<div class="pagebody">

	<div id="intro">
		<h2>Join pente.org</h2>
	    <p>Fill out this simple form and you'll be a member in seconds!</p>
	</div>
	

	<div id="text">

	  
		<div id="signupnow">

			<div id="signupnow-text">
				<h2>Just want to try it out? </h2>
				<p>...it's ok, I hate filling out forms too</p>
			</div>

			<div id="signupnow-button">
				<a href="javascript:handlePlay('true','800','true')">Play as a Guest!</a>
			</div>
		</div>  
	  
	  
	  
	  <form name="register_form" method="post" action="/join">
   <table border="0" width="100%" cellpadding="9" cellspacing="9" 
          bgcolor="#deecde" id="jointable" style="padding: 5px 5px 5px 5px;">
          
<% String registrationError = (String) request.getAttribute("registrationError");
   if (registrationError != null) { %>

	<tr>
	 <td class="fail" colspan="2">
	   Registration failed: <%= registrationError %>
	 </td>
	</tr>
<% } %>
     <tr>
      <td class="b">
        User Name
      </td>
      <td>
        <%
		String registerName = request.getParameter("registerName");
		if (registerName == null) {
		    registerName = "";
		}
	    %>
        <input type="text" name="registerName" id="registerName" size="30" maxlength="10" value="<%= registerName %>"><br>
        <p class="s">5 to 10 characters, digits or underscore _</p>
      </td>
     </tr>
     <tr>
      <td class="b">
        Password
      </td>
      <td>
        <%
        String registerPassword = request.getParameter("registerPassword");
        if (registerPassword == null) {
	        registerPassword = "";
        }
        %>
        <input type="password" name="registerPassword" size="30" maxlength="16" value="<%= registerPassword %>">
        <p class="s">5 to 16 characters, digits or underscore _</p>
      </td>
     </tr>
     <tr>
      <td class="b">
        Confirm Password
      </td>
      <td>
        <%
        String registerPasswordConfirm = request.getParameter("registerPasswordConfirm");
        if (registerPasswordConfirm == null) {
	        registerPasswordConfirm = "";
        }
        %>
        <input type="password" name="registerPasswordConfirm" size="30" maxlength="16" value="<%= registerPasswordConfirm %>">
      </td>
     </tr>
     <tr>
      <td class="b">
        Email
      </td>
      <td>
        <%
        String registerEmail = request.getParameter("registerEmail");
        if (registerEmail == null) {
            registerEmail = "";
        }
        %>
        <input type="text" name="registerEmail" size="30" maxlength="100" value="<%= registerEmail %>">
      </td>
     </tr>
     <tr>
      <td colspan="2">
        <% String registerEmailUpdates = request.getParameter("registerEmailUpdates");
           String checkedUpdates = registerEmailUpdates != null && registerEmailUpdates.equals("Y") ? " checked" : ""; %>
        <input type="checkbox" name="registerEmailUpdates" value="Y"<%= checkedUpdates %>>
        Email me site updates
      </td>
     </tr>

     <tr>
      <td colspan="2">
        <br>
        Pente.org's Policy for Playing Rated Games<br>
        <textarea rows="6" cols="75" readonly>
Pente.org maintains a rating for you when you play "rated"
games.  The ratings system is important to help
you determine your skill level and to help you find
worthy opponents.  Pente.org attempts to ensure that ratings
accurately reflect a players skill, and therefore
certain guidelines must be followed by all players!
          
1. Play rated games using only your brain.  Do no play
with any outside assistance.  Just to be clear,
here are some examples of what you should NOT do: use
another pente board to examine future positions, use a
game database to lookup the current or future
positions, use a computer opponent to find moves,
consult written notes or books.

2. Play rated games at Pente.org with only one user account.
Do not create multiple users at and play rated
games with them.

3. When playing games, you can request to undo
your last move, your opponent can choose to accept or
deny this request (Pente.org does not care, it is up to
you). If you do not plan to accept undo's, you should
mention this to your opponent before starting a rated
game.

4. When watching a rated game, do not make comments
about specific game moves, this could affect the
outcome of the game.  There is plenty of time for
analysis after the game.

5.If your opponent is disconnected from the internet,
Pente.org allows him/her 7 minutes to return to resume the
game.  After that point you may decide to cancel the
game or force your opponent to resign.  This feature
was implemented to stop other players from bailing out
of a losing game.  However, do not abuse this feature,
if you are sure you will lose, you should resign the
game.  Do not force your opponent to resign unless you
are absolutely sure you will win.

That's it, and remember to have fun of course!</textarea>
      </td>
     </tr>
     <tr>
       <td colspan="2">
         <input type="checkbox" name="agreePolicy" value="Y"> I have read and
         understand Pente.org's Policy for Playing Rated Games
       </td>
     </tr>
     <tr>
      <td>&nbsp;</td>
      <td>
        <input type="submit" value="Join">
      </td>
     </tr>
   </table>
   </form>
	  
    </div>
</div>

	<div id="right">
    <%@ include file="loginbox.jsp" %>
	</div>
	
<script type="text/javascript">addLoadEvent(function(){var a=document.getElementById('registerName');if(a){a.focus();}else{alert('no');}});</script>
 
<%@ include file="bottom.jsp" %>
