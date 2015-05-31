<%@ page import="java.util.*, java.sql.*, java.text.*,
                 org.pente.gameServer.server.*, 
                 org.pente.gameServer.tourney.*,
                 org.pente.game.*,
                 org.pente.gameServer.core.*,
                 org.apache.log4j.* "%>

<%! private static Category log4j = 
        Category.getInstance("org.pente.gameServer.web.client.jsp");
    private static final DateFormat dateFormat =
        new SimpleDateFormat("MM/dd/yyyy hh:mm"); %>
    
<% Resources resources = (Resources) application.getAttribute(
       Resources.class.getName());

   String action = request.getParameter("action");
   Tourney tourney = null;
   if (action != null && action.equals("add")) {
       
       int game = Integer.parseInt(request.getParameter("game"));
       String name = request.getParameter("name");

       tourney = new Tourney();
       tourney.setName(name);
       tourney.setGame(game);
       tourney.setInitialTime(Integer.parseInt(request.getParameter("initial")));
       tourney.setIncrementalTime(Integer.parseInt(request.getParameter("incremental")));
       String speed = request.getParameter("speed");
       tourney.setSpeed(speed != null && speed.equals("Y"));
       if (!tourney.isSpeed()) {
          tourney.setRoundLengthDays(Integer.parseInt(request.getParameter("roundLength")));
       }
       String signupEnd = request.getParameter("signupEnd");
       tourney.setSignupEndDate(dateFormat.parse(signupEnd));
       String start = request.getParameter("start");
       if (start == null || start.equals("")) {
           tourney.setStartDate(tourney.getSignupEndDate());
       }
       else {
	       tourney.setStartDate(dateFormat.parse(start));
	   }
	   int format = Integer.parseInt(request.getParameter("format"));
	   if (format == 1) {
	       tourney.setFormat(new RoundRobinFormat());
	   }
	   else if (format == 2) {
	       tourney.setFormat(new SingleEliminationFormat());
	   }
	   else if (format == 3) {
	       tourney.setFormat(new DoubleEliminationFormat());
	   }
	   else if (format == 4) {
	       tourney.setFormat(new SwissFormat());
	   }
	   String admins = request.getParameter("admins");
	   StringTokenizer st = new StringTokenizer(admins, ",");
	   while (st.hasMoreTokens()) {
	       DSGPlayerData d = resources.getDsgPlayerStorer().loadPlayer(st.nextToken());
	       tourney.addDirector(d.getPlayerID());
	   }
	   
	   String rrTypeStr = request.getParameter("rrType");
	   String rrValueStr = request.getParameter("rr");
	   if (rrTypeStr != null && rrValueStr != null) {
		   try {
		      int rrType = Integer.parseInt(rrTypeStr);
		      int rrValue = Integer.parseInt(rrValueStr);
		      if (rrType != 0) {
		        Restriction r = new Restriction(rrType, rrValue);
		        tourney.addRestriction(r);
		      }
		   } catch (NumberFormatException nfe) {
		       nfe.printStackTrace();
		   }
	   }

       String erType = request.getParameter("erType");
       if (erType != null && erType.equals("1")) {
           tourney.addRestriction(new Restriction(
               Restriction.GAMES_RESTRICTION_ABOVE, 20));
       }

       String prize = request.getParameter("prize");
       if (prize != null && !prize.equals("")) {
           tourney.setPrize(prize);
       }
       
       resources.getTourneyStorer().insertTourney(tourney);
   }
%>

<html>
<head><title>Add new tournament</title></head>
<body>

<% if (tourney != null) { %>
     New tourney "<%= tourney.getName() %>" successfully created.<br>
<% } %>

<font size="5">Add new tournament</font><br>
<br>
<form name="addTourney" action="newTourney.jsp" method="post">
  <input type="hidden" name="action" value="add">
  <table border="0" cellspacing="0" cellpadding="0">
    <tr><td valign="top"><b>Name:</b></td>
    <td><input type="text" maxLength="255" name="name"></td></tr>
    <tr><td valign="top"><b>Game:</b></td>
        <td>
          <select name="game">
          <% Game games[] = GridStateFactory.getAllGames();
             for (int i = 1; i < games.length; i++) { %>
               <option value="<%= games[i].getId() %>"><%= games[i].getName() %>
          <% } %>
        </td>
    </tr>
    <tr><td valign="top"><b>Initial time:</b></td>
        <td><input type="text" maxLength="3" size="3" name="initial"></td>
    </tr>
    <tr><td valign="top"><b>Incremental time:</b></td>
        <td><input type="text" maxLength="3" size="3"  name="incremental"></td>
    </tr>
    <tr><td valign="top"><b>Rating restriction:</b></td>
        <td><input type="radio" name="rrType" value="0" selected>None&nbsp;
            <input type="radio" name="rrType" value="1">Above&nbsp;
            <input type="radio" name="rrType" value="2">Below&nbsp;
            <input type="text" maxLength="4" size="4"  name="rr">
        </td>
    </tr>
    <tr><td valign="top"><b>Require established players (>20 games)?:</b></td>
        <td valign="top">Yes <input type="radio" name="erType" value="1">&nbsp;
                         No <input type="radio" name="erType" value="0">
        </td>
    </tr>
    <tr><td valign="top"><b>Prize:</b></td>
        <td><input type="text" name="prize" value="gold,silver,or any other text"></td>
    </tr>
    <tr><td valign="top"><b>Speed tourney?:</b></td>
        <td valign="top">Yes <input type="radio" name="speed" value="Y">
            No <input type="radio" name="speed" value="N">
        </td>
    </tr>
    <tr><td valign="top"><b>Round length days:</b><br>(if this is non-speed)</td>
        <td valign="top"><input type="text" maxLength="3" size="3" name="roundLength"></td>
    </tr>
    <tr><td valign="top"><b>Signup end date/time:</b><br>(MM/dd/yyyy hh:mm format)</td>
        <td valign="top"><input type="text" maxLength="16" size="16" name="signupEnd"></td>
    </tr>
    <tr><td valign="top"><b>Start date/time:</b><br>(MM/dd/yyyy hh:mm format)<br>defaults to right after signup end date</td>
        <td valign="top"><input type="text" maxLength="16" size="16"  name="start"></td>
    </tr>
    <tr><td valign="top"><b>Format:</b><br>(speed tournies should be single-elim)</td>
        <td>
          <select name="format">
           <option value="1">Round-Robin
           <option value="2">Single-Elimination
           <option value="3">Double-Elimination
           <option value="4">Swiss
          </select>
        </td>
    </tr>
    <tr><td valign="top"><b>Directors:</b><br>(comma-delimited names)</td>
        <td>
          <input type="text" name="admins" value="partica">
        </td>
    </tr>
    <tr><td>&nbsp;</td></tr>
    <tr><td valign="top"><input type="submit" value="Add"></td><td>&nbsp;</td></tr>
  </table>
</form>

<br>
<a href=".">Back to admin</a>

</body>
</html>