<% response.sendRedirect("/join.jsp"); %>
<%--
<%@ page import="org.pente.gameServer.core.*, com.jivesoftware.util.*" %>

<% pageContext.setAttribute("title", "Register"); %>
<%@ include file="begin.jsp" %>

<table width="100%" border="0" colspacing="0" colpadding="0">

<tr>
 <td>
  <h3>Register at Pente.org</h3>
 </td>
</tr>

<tr>
 <td>
  <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
    Register at Pente.org to enjoy the 
    <b><a href="<%= request.getContextPath() %>/gameServer/index.jsp#top10" %>many benefits</a></b>
    of being a member.<br>
    <br>
    Please create a player name for yourself, and enter a password and your email
    address.  <a href="/gameServer/help/helpWindow.jsp?file=privacyPolicy">Privacy Policy</a>
    concerning your email address.<br>
    <br>
    The login name must contain only letters, digits and the underscore character
    and must be 5-10 characters.  The same is true for the password except passwords can be up to 16 characters.
    Email addresses must be in the format user@host.com.<br>
    <br>
    * Required field
  </font>
 </td>
</tr>

<tr>
 <td>&nbsp;</td>
</tr>


<% String registrationError = (String) request.getAttribute("registrationError");
   if (registrationError != null) { %>

<tr>
 <td>
  <font face="Verdana, Arial, Helvetica, sans-serif" size="2" color="<%= textColor2 %>">
   Registration failed: <%= registrationError %>
  </font>
 </td>
</tr>

<%   
   }
%>

<tr>
 <td>

   <form name="register_form" method="post" action="<%= request.getContextPath() %>/gameServer/register">
   <table border="0" colspacing="1" colpadding="1">
     <tr>
      <td>
       <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
        Name*
       </font>
      </td>
      <td>
       <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
        <%
		String registerName = request.getParameter("registerName");
		if (registerName == null) {
		    registerName = "";
		}
	    %>
        <input type="text" name="registerName" size="15" maxlength="10" value="<%= registerName %>">
       </font>
      </td>
     </tr>
     <tr>
      <td>
       <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
        Password*
       </font>
      </td>
      <td>
       <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
        <%
        String registerPassword = request.getParameter("registerPassword");
        if (registerPassword == null) {
	        registerPassword = "";
        }
        %>
        <input type="password" name="registerPassword" size="15" maxlength="16" value="<%= registerPassword %>">
       </font>
      </td>
     </tr>
     <tr>
      <td>
       <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
        Re-enter Password*
       </font>
      </td>
      <td>
       <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
        <%
        String registerPasswordConfirm = request.getParameter("registerPasswordConfirm");
        if (registerPasswordConfirm == null) {
	        registerPasswordConfirm = "";
        }
        %>
        <input type="password" name="registerPasswordConfirm" size="15" maxlength="16" value="<%= registerPasswordConfirm %>">
       </font>
      </td>
     </tr>
     <tr>
      <td>
       <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
        Email*
       </font>
      </td>
      <td>
       <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
        <%
        String registerEmail = request.getParameter("registerEmail");
        if (registerEmail == null) {
            registerEmail = "";
        }
        %>
        <input type="text" name="registerEmail" size="30" maxlength="100" value="<%= registerEmail %>">
       </font>
      </td>
     </tr>
     <tr>
      <td>
       <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
        Display email on profile
       </font>
      </td>
      <td>
       <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
        <% String registerEmailVisible = request.getParameter("registerEmailVisible");
           String checked = registerEmailVisible != null && registerEmailVisible.equals("Y") ? " checked" : ""; %>
        <input type="checkbox" name="registerEmailVisible" value="Y"<%= checked %>>
       </font>
      </td>
     </tr>
     <tr>
      <td>
       <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
        Email me site updates
       </font>
      </td>
      <td>
       <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
        <% String registerEmailUpdates = request.getParameter("registerEmailUpdates");
           String checkedUpdates = registerEmailUpdates != null && registerEmailUpdates.equals("Y") ? " checked" : ""; %>
        <input type="checkbox" name="registerEmailUpdates" value="Y"<%= checkedUpdates %>>
       </font>
      </td>
     </tr>
     <tr>
      <td>
       <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
        Location
       </font>
      </td>
      <td>
       <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
        <% String registerLocation = request.getParameter("registerLocation");
           if (registerLocation == null) {
               registerLocation = "";
           }
           else {
               registerLocation = registerLocation.trim();
           }
        %>
        <input type="text" name="registerLocation" size="30" maxlength="50" value="<%= registerLocation %>">
       </font>
      </td>
     </tr>
     <tr>
       <td>
	       <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
	        Timezone
	       </font>
	   </td>
       <td>
        <select size="1" name="timezone">
        <%	String[][] timeZones = LocaleUtils.getTimeZoneList();
			String timeZoneID = request.getParameter("timezone");
			if (timeZoneID == null) {
				timeZoneID = "America/New_York";
			}
            for (int i=0; i<timeZones.length; i++) {
                boolean selected = timeZones[i][0].equals(timeZoneID);
        %>
            <option value="<%= timeZones[i][0] %>"<%= (selected?" selected":"") %>><%= timeZones[i][1] %>

        <%	} %>
        </select>
       </td>
     </tr>
     <tr>
      <td>
       <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
        Sex
       </font>
      </td>
      <td>
       <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
        <select name="registerSex">
         <% char sexValues[] = { DSGPlayerData.UNKNOWN, DSGPlayerData.FEMALE, DSGPlayerData.MALE };
            String sexNames[] = { "", "Female", "Male" };
            String sexStr = request.getParameter("registerSex");
            char sex = DSGPlayerData.UNKNOWN;
            if (sexStr != null && sexStr.length() == 1) {
                sex = sexStr.charAt(0);
            }
            for (int i = 0; i < sexValues.length; i++) {
                out.print("<option value=\"" + sexValues[i] + "\"");
                if (sex == sexValues[i]) {
                    out.print(" selected");
                }
                out.println(">" + sexNames[i] + "</option>");
            }
         %>
       </font>
      </td>
     </tr>
     <tr>
      <td>
       <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
        Age
       </font>
      </td>
      <td>
       <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
        <% String registerAgeStr = request.getParameter("registerAge");
           int age = 0;
           if (registerAgeStr != null) {
               registerAgeStr = registerAgeStr.trim();
               try {
                   age = Integer.parseInt(registerAgeStr);
               } catch (NumberFormatException ex) {
               }
           }
           String registerAge = age == 0 ? "" : Integer.toString(age);
        %>
        <input type="text" name="registerAge" size="5" maxlength="3" value="<%= registerAge %>">
       </font>
      </td>
     </tr>
     <tr>
      <td>
       <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
        Home page
       </font>
      </td>
      <td>
       <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
        <% String registerHomepage = request.getParameter("registerHomepage");
           if (registerHomepage == null) {
               registerHomepage = "";
           }
           else {
               registerHomepage = registerHomepage.trim();
           }
        %>
        <input type="text" name="registerHomepage" size="30" maxlength="100" value="<%= registerHomepage %>">
       </font>
      </td>
     </tr>
     <tr>
      <td colspan="2">
        <br>
        Pente.org's Policy for Playing Rated Games<br>
        <textarea rows="10" cols="55" readonly>
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
         <input type="checkbox" name="agreePolicy" value="Y"> * I have read and
         understand Pente.org's Policy for Playing Rated Games
       </td>
     </tr>
     <tr>
      <td>&nbsp;</td>
      <td>
       <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
        <input type="submit" value="Register">
       </font>
      </td>
     </tr>
   </table>
   </form>
 </td>
</tr>

</table>


<%@ include file="end.jsp" %>
--%>