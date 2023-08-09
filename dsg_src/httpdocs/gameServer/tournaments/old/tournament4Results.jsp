<%@ page import="java.sql.*,
                 java.util.*,
                 org.pente.database.*,
                 javax.servlet.*,
                 javax.servlet.http.*,
                 org.apache.log4j.*" %>

<%
   ServletContext ctx = getServletContext();
   DBHandler dbHandler = (DBHandler)
      ctx.getAttribute(DBHandler.class.getName());
%>

<%!
   private static Vector ALL_TABLES = new Vector(3);

   static {
      ALL_TABLES.add("player");
      ALL_TABLES.add("dsg_tournament");
      ALL_TABLES.add("dsg_tournament_results");
      ALL_TABLES.add("game_event");
   }

   private class Data {
      public long resultId;
      public String name1;
      public int seed1;
      public String name2;
      public int seed2;
      public int round;
      public int section;
      public int p1_wins;
      public int p1_losses;
      public int p2_wins;
      public int p2_losses;
      public boolean forfeit;
      public int result;
      public boolean droppedOut1;
      public boolean droppedOut2;

      public String getPlayer1() {
         if (notPlayedYet() || player1Wins()) {
            return name1;
         } else {
            return name2;
         }
      }

      public String getPlayer2() {
         if (notPlayedYet() || player1Wins()) {
            return name2;
         } else {
            return name1;
         }
      }

      public int getSeed1() {
         if (notPlayedYet() || player1Wins()) {
            return seed1;
         } else {
            return seed2;
         }
      }

      public int getSeed2() {
         if (notPlayedYet() || player1Wins()) {
            return seed2;
         } else {
            return seed1;
         }
      }

      public String getResult() {
         if (isBye()) {
            return "bye";
         } else if (isTie()) {
            return "ties";
         } else if (notPlayedYet()) {
            return forfeit ? "(double forfeit)" : "vs.";
         } else if (player1Wins() || player2Wins()) {
            return forfeit ? "defeats (forfeit)" : "defeats";
         }

         return "vs.";
      }

      // return the winner's record, even though sometimes the
      // record's for each player could be wrong
      // or if double-forfeit, return 0-X
      public String getRecord() {

         if (notPlayedYet()) {
            if (forfeit) {
               return p1_wins + "-" + p1_losses;
            } else {
               return "";
            }
         } else if (isBye() || player1Wins() || isTie()) {
            return p1_wins + "-" + p1_losses;
         } else if (player2Wins()) {
            return p2_wins + "-" + p2_losses;
         }

         return "";
      }

      public boolean isBye() {
         return name1.equals("") || name2.equals("");
      }

      public boolean notPlayedYet() {
         return result == 0;
      }

      public boolean player1Wins() {
         return result == 1;
      }

      public boolean player2Wins() {
         return result == 2;
      }

      public boolean isTie() {
         return result == 3;
      }
   }

   private class BStandingData implements Comparable {
      public String name;
      public int wins;
      public int losses;
      public boolean droppedOut;

      public void addTo(Data d) {

         if (name.equals(d.name1)) {
            wins += d.p1_wins;
            losses += d.p1_losses;
         } else {
            wins += d.p2_wins;
            losses += d.p2_losses;
         }
      }

      public int compareTo(Object obj) {
         BStandingData bsd = (BStandingData) obj;
         if (wins > bsd.wins) {
            return -1;
         } else if (wins == bsd.wins) {
            if (losses < bsd.losses) {
               return -1;
            } else if (losses == bsd.losses) {
               return name.compareTo(bsd.name);
            } else {
               return 1;
            }
         } else {
            return 1;
         }
      }

      public boolean equals(Object obj) {
         BStandingData bsd = (BStandingData) obj;
         return wins == bsd.wins &&
            losses == bsd.losses &&
            name.equals(bsd.name);
      }
   }


   private static Category log4j =
      Category.getInstance("org.pente.gameServer.web.client.jsp");

   private static final String gameResults[] =
      new String[]{"vs.", "defeats", "defeats (forfeit)", "loses to", "loses to (forfeit)", "(double forfeit)",};

   private static final BStandingData findData(String name, Set set) {

      for (Iterator it = set.iterator(); it.hasNext(); ) {
         BStandingData d = (BStandingData) it.next();
         if (d.name.equals(name)) {
            return d;
         }
      }
      return null;
   }

%>

<%
   int resultsIterator = 0;
   boolean admin = false;
   boolean edit = false;
   boolean update = false;
   String t4Name = (String) request.getAttribute("name");
   if (t4Name == null) t4Name = "";

   if (t4Name.equals("dweebo") ||
      t4Name.equals("mmammel") ||
      t4Name.equals("progambler")) {

      admin = true;
      if (request.getParameter("edit") != null) {
         edit = true;
      }
      if (request.getParameter("update") != null) {
         update = true;
      }
   }

   if (update) {
      // database store stuff
   }

   List tournament4AData = new ArrayList(100);
   List tournament4BData = new ArrayList(100);
   Set tournament4BStandings = new TreeSet();

   Connection con = null;
   PreparedStatement nameStmt = null;
   ResultSet nameResults = null;
   PreparedStatement stmt = null;
   ResultSet results = null;
   try {
      con = dbHandler.getConnection();
      nameStmt = con.prepareStatement(
         "select player.name, dsg_tournament.seed, dsg_tournament.dropout_round " +
            "from player, dsg_tournament, game_event " +
            "where dsg_tournament.event_id = game_event.eid " +
            "and game_event.name = ? " +
            "and player.pid = ? " +
            "and dsg_tournament.pid = player.pid");

      stmt = con.prepareStatement(
         "select pid1, pid2, round, section, result, forfeit, p1_wins, p1_losses, p2_wins, p2_losses, result_id " +
            "from dsg_tournament_results, game_event " +
            "where dsg_tournament_results.event_id = game_event.eid " +
            "and game_event.name = ? " +
            "and round = ? " +
            "and section = ? " +
            "order by round, section, result_id");

      MySQLDBHandler.lockTables(ALL_TABLES, con);
      stmt.setString(1, "Tournament 4A");
      nameStmt.setString(1, "Tournament 4A");
      List data = tournament4AData;

      for (int tournament = 0; tournament < 2; tournament++) {
         if (tournament == 1) {
            stmt.setString(1, "Tournament 4B");
            nameStmt.setString(1, "Tournament 4B");
            data = tournament4BData;
         }

         outer:
         for (int round = 1; ; round++) {
            boolean playersInRound = false;
            for (int section = 1; section < 3; section++) {

               stmt.setInt(2, round);
               stmt.setInt(3, section);
               results = stmt.executeQuery();

               if (!results.next()) {
                  results.close();
                  continue;
               }
               playersInRound = true;

               do {
                  Data d = new Data();

                  // get player 1 name
                  long pid = results.getLong(1);
                  if (pid == 0) {
                     d.name1 = "";
                     d.seed1 = 0;
                  } else {
                     nameStmt.setLong(2, pid);
                     nameResults = nameStmt.executeQuery();
                     nameResults.next();
                     d.name1 = nameResults.getString(1);
                     d.seed1 = nameResults.getInt(2);
                     d.droppedOut1 = nameResults.getInt(3) != 0;
                     nameResults.close();
                  }

                  // get player 2 name
                  pid = results.getLong(2);
                  if (pid == 0) {
                     d.name2 = "";
                     d.seed2 = 0;
                  } else {
                     nameStmt.setLong(2, results.getLong(2));
                     nameResults = nameStmt.executeQuery();
                     nameResults.next();
                     d.name2 = nameResults.getString(1);
                     d.seed2 = nameResults.getInt(2);
                     d.droppedOut2 = nameResults.getInt(3) != 0;
                     nameResults.close();
                  }

                  d.round = results.getInt(3);
                  d.section = results.getInt(4);
                  d.result = results.getInt(5);
                  d.forfeit = results.getString(6).equals("Y");
                  d.p1_wins = results.getInt(7);
                  d.p1_losses = results.getInt(8);
                  d.p2_wins = results.getInt(9);
                  d.p2_losses = results.getInt(10);
                  d.resultId = results.getLong(11);

                  data.add(d);

               } while (results.next());

               results.close();
            }
            if (!playersInRound) {
               break;
            }
         }
      }

   } finally {
      if (results != null) {
         results.close();
      }
      if (stmt != null) {
         stmt.close();
      }
      if (nameResults != null) {
         nameResults.close();
      }
      if (nameStmt != null) {
         nameStmt.close();
      }
      if (con != null) {
         MySQLDBHandler.unLockTables(con);
         dbHandler.freeConnection(con);
      }
   }
%>

<% pageContext.setAttribute("title", "Tournament 4 - Completed"); %>
<%@ include file="../../begin.jsp" %>

<% if (edit) { %>
<form name="updateForm" method="post" action="tournament4Results.jsp">
   <input type="hidden" name="edit" value="true">
      <% } %>

   <table border="0" cellpadding="0" cellspacing="0" width="490">
      <tr bgcolor="<%= bgColor1 %>">
         <td colspan="2"><b><font face="Verdana, Arial, Helvetica, sans-serif" size="2" color="white">
            Tournament 4 - Completed</font></b></td>
      </tr>
      <tr>
         <td>&nbsp;</td>
      </tr>
      <tr>
         <td colspan="2">
            <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
               Section A Winner: <a href="../../profile?viewName=virag">virag</a> &
               <a href="../../profile?viewName=sjustice">sjustice</a><br>
               Section B Winner: <a href="../../profile?viewName=ediscs">ediscs</a><br>
               Total Players: 50 (2 Sections)<br>
               Completed on: 05/26/2003<br>
               <b><a href="javascript:submitToDatabase('Pente.org', 'Tournament 4A', 'All Rounds', 'All Sections');">View
                  Section A Games</a></b><br>
               <b><a href="javascript:submitToDatabase('Pente.org', 'Tournament 4B', 'All Rounds', 'All Sections');">View
                  Section B Games</a></b><br>
               <b><a href="tournament4Rules.jsp">Rules/Format</a></b><br>
            </font>
         </td>
      </tr>
      <tr>
         <td>&nbsp;</td>
      </tr>
      <tr bgcolor="<%= bgColor1 %>">
         <td width="460"><b><font face="Verdana, Arial, Helvetica, sans-serif" size="2" color="#FFFFFF">
            Tournament 4 Detailed Results
         </font></b></td>
      </tr>
      <tr>
         <td width="100%">
            <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
               <b><a href="#tournament4A">Tournament 4A Detailed Results</a></b><br>
               <% int currentRound = 0, currentSection = 0;
                  for (int i = 0; i < tournament4AData.size(); i++) {
                     Data d = (Data) tournament4AData.get(i);
                     if (d.round != currentRound) {
                        currentRound = d.round;
                        currentSection = 0;
                     }
                     if (d.section != currentSection) {
                        currentSection = d.section; %>
               &nbsp;&nbsp;<b><a
               href="#tournament4A-<%= currentRound %>.<%= currentSection %>">Round <%= currentRound %> -
               Section <%= currentSection %>
            </a></b><br>
               <% }
               } %>
               <br>
               <b><a href="#tournament4B">Tournament 4B Detailed Results</a></b><br>
               <% currentRound = 0;
                  for (int i = 0; i < tournament4BData.size(); i++) {
                     Data d = (Data) tournament4BData.get(i);
                     if (d.round != currentRound) {
                        currentRound = d.round; %>
               &nbsp;&nbsp;<b><a href="#tournament4B-<%= currentRound %>">Round <%= currentRound %>
            </a></b><br>
               <% }
               } %>
               &nbsp;&nbsp;<b><a href="#tournament4B-Overall">Overall Standings</a></b><br>
               &nbsp;&nbsp;<b><a href="#tournament4BPlayoff1">Playoff Round 1</a></b><br>
               &nbsp;&nbsp;<b><a href="#tournament4BPlayoff2">Playoff Round 2</a></b><br>
               &nbsp;&nbsp;<b><a href="#tournament4BPlayoff3">Playoff Round 3</a></b><br>
            </font>
         </td>
      </tr>
      <tr>
         <td>&nbsp;</td>
      </tr>
      <tr bgcolor="<%= bgColor1 %>">
         <td width="460"><b><font face="Verdana, Arial, Helvetica, sans-serif" size="2" color="#FFFFFF">
            <a name="tournament4A">Tournament 4A Detailed Results
         </font></b></td>
      </tr>
      <tr>
         <td width="100%">
            <table width="95%" align="center" border="0" cellpadding="0" cellspacing="0">

               <% currentRound = 0;
                  currentSection = 0;
                  for (int i = 0; i < tournament4AData.size(); i++) {
                     Data d = (Data) tournament4AData.get(i);
                     if (d.round != currentRound) {
                        currentRound = d.round;
                        currentSection = 0;
                        if (currentRound != 1) { %>
               <tr>
                  <td>&nbsp;</td>
               </tr>
               <%
                     }
                  }
                  if (d.section != currentSection) {
                     currentSection = d.section; %>
               <tr>
                  <td>&nbsp;</td>
               </tr>
               <tr bgcolor="<%= bgColor1 %>">
                  <td colspan="4">
                     <font face="Verdana, Arial, Helvetica, sans-serif" size="2" color="white">
                        <a name="tournament4A-<%= currentRound %>.<%= currentSection %>"><b>Round <%= currentRound %> -
                           Bracket <%= currentSection %>
                        </b>
                     </font>
                  </td>
               </tr>
               <% if (edit) { %>
               <tr>
                  <td>
                     <input type="button"
                            value="Add match"
                            name="add.<%= currentRound %>.<%= currentSection %>"
                            onclick="updateForm.submit()">
                  </td>
               </tr>
               <% } %>
               <% } %>

               <tr>
                  <td width="25%">
                     <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
                        <a href="../../profile?viewName=<%= d.getPlayer1() %>"><%= d.getPlayer1() %>
                        </a> <% if (d.getSeed1() != 0) { %> (<%= d.getSeed1() %>) <% } %>
                     </font>
                  </td>
                  <td width="25%">
                     <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
                        <% if (edit) { %>
                        <select name="<%= d.resultId %>.result">
                           <% for (resultsIterator = 0; resultsIterator < gameResults.length; resultsIterator++) { %>
                           <option
                              value="<%= gameResults[resultsIterator] %>" <% if (gameResults[resultsIterator].equals(d.getResult())) { %>
                              selected <% } %>><%= gameResults[resultsIterator] %>
                                 <%     } %>
                        </select>
                        <% } else { %>
                        <%= d.getResult() %>
                        <% } %>
                     </font>
                  </td>
                  <td width="25%">
                     <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
                        <a href="../../profile?viewName=<%= d.getPlayer2() %>"><%= d.getPlayer2() %>
                        </a> <% if (d.getSeed2() != 0) { %> (<%= d.getSeed2() %>) <% } %>
                     </font>
                  </td>
                  <td width="25%">
                     <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
                        <% if (!d.isBye()) { %>
                        <% if (edit) { %>
                        <input type="text" name="<%= d.resultId %>.record" value="<%= d.getRecord() %>">
                        <% } else { %>
                        <%= d.getRecord() %>
                        <% } %>
                        <% } %>
                        &nbsp;
                     </font>
                  </td>
               </tr>

               <% } %>

            </table>
         </td>
      </tr>
      <tr>
         <td>&nbsp;</td>
      </tr>
      <tr>
         <td>&nbsp;</td>
      </tr>
      <tr bgcolor="<%= bgColor1 %>">
         <td width="460"><b><font face="Verdana, Arial, Helvetica, sans-serif" size="2" color="#FFFFFF">
            <a name="tournament4B">Tournament 4B Detailed Results
         </font></b></td>
      </tr>
      <tr>
         <td width="100%">
            <table width="95%" align="center" border="0" cellpadding="0" cellspacing="0">

               <% currentRound = 0;
                  for (int i = 0; i < tournament4BData.size(); i++) {
                     Data d = (Data) tournament4BData.get(i);

                     if (!d.name1.equals("")) {
                        BStandingData b1 = findData(d.name1, tournament4BStandings);
                        if (b1 == null) {
                           b1 = new BStandingData();
                           b1.name = d.name1;
                           b1.droppedOut = d.droppedOut1;
                        } else {
                           tournament4BStandings.remove(b1);
                        }
                        b1.addTo(d);
                        tournament4BStandings.add(b1);
                     }
                     if (!d.name2.equals("")) {
                        BStandingData b2 = findData(d.name2, tournament4BStandings);
                        if (b2 == null) {
                           b2 = new BStandingData();
                           b2.name = d.name2;
                           b2.droppedOut = d.droppedOut2;
                        } else {
                           tournament4BStandings.remove(b2);
                        }
                        b2.addTo(d);
                        tournament4BStandings.add(b2);
                     }

                     if (d.round != currentRound) {
                        currentRound = d.round; %>
               <tr>
                  <td>&nbsp;</td>
               </tr>
               <tr bgcolor="<%= bgColor1 %>">
                  <td colspan="4">
                     <font face="Verdana, Arial, Helvetica, sans-serif" size="2" color="white">
                        <a name="tournament4B-<%= currentRound %>"><b>Round <%= currentRound %>
                        </b>
                     </font>
                  </td>
               </tr>
               <% } %>

               <tr>
                  <td width="25%">
                     <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
                        <a href="../../profile?viewName=<%= d.getPlayer1() %>"><%= d.getPlayer1() %>
                        </a>
                     </font>
                  </td>
                  <td width="25%">
                     <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
                        <%= d.getResult() %>
                     </font>
                  </td>
                  <td width="25%">
                     <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
                        <a href="../../profile?viewName=<%= d.getPlayer2() %>"><%= d.getPlayer2() %>
                        </a>
                     </font>
                  </td>
                  <td width="25%">
                     <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
                        <%= d.getRecord() %>
                     </font>
                  </td>
               </tr>

               <% } %>

            </table>
         </td>
      </tr>
      <tr>
         <td>&nbsp;</td>
      </tr>
      <tr bgcolor="<%= bgColor1 %>">
         <td width="460"><b><font face="Verdana, Arial, Helvetica, sans-serif" size="2" color="#FFFFFF">
            <a name="tournament4B-Overall">Pre-Playoff Tournament 4B Standings
         </font></b></td>
      </tr>
      <tr>
         <td width="100%">
            <table width="95%" align="right" border="0" cellpadding="0" cellspacing="0">
               <tr>
                  <td>
                     <table border="0" cellpadding="0" cellspacing="0">
                        <% int ii = 1;
                           int prevI = ii;
                           int advance = 0;
                           BStandingData last = null;
                           BStandingData b = null;
                           boolean drawnCutoff = false;
                           for (Iterator standings = tournament4BStandings.iterator(); standings.hasNext(); ) {
                              last = b;
                              b = (BStandingData) standings.next();
                              if (last != null) {
                                 prevI = ii;
                                 if (last.wins != b.wins || last.losses != b.losses) {
                                    ii++;
                                 }
                              }
                              advance++;
                              if (!drawnCutoff && advance >= 9 && ii != prevI) {
                                 drawnCutoff = true; %>
                        <tr>
                           <td colspan="3">
                              <hr>
                           </td>
                        </tr>
                        <% } %>
                        <tr>
                           <td>
                              <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
                                 <%= ii %>.
                              </font>
                           </td>
                           <td>
                              <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
                                 &nbsp;&nbsp;<a href="../../profile?viewName=<%= b.name %>"><%= b.name %>
                              </a><% if (b.droppedOut) { %>*<% } %>&nbsp;&nbsp;
                              </font>
                           </td>
                           <td>
                              <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
                                 <%= b.wins %> - <%= b.losses %>
                              </font>
                           </td>
                        </tr>

                        <% } %>
                     </table>
                  </td>
               </tr>

               <tr>
                  <td>
                     <br>
                     <font face="Verdana, Arial, Helvetica, sans-serif" size="2" color="#FFFFFF">
                        * Player has dropped out
                     </font>
                  </td>
               </tr>
            </table>
         </td>
      </tr>
      <tr bgcolor="<%= bgColor1 %>">
         <td width="460"><b><font face="Verdana, Arial, Helvetica, sans-serif" size="2" color="#FFFFFF">
            <a name="tournament4B-Overall">Tournament 4B Playoffs
         </font></b></td>
      </tr>
      <tr>
         <td width="100%">
            <table width="95%" align="center" border="0" cellpadding="0" cellspacing="0">
               <tr>
                  <td>&nbsp;</td>
               </tr>
               <tr bgcolor="<%= bgColor1 %>">
                  <td colspan="4">
                     <font face="Verdana, Arial, Helvetica, sans-serif" size="2" color="white">
                        <a name="tournament4BPlayoff1"><b>Round 1</b>
                     </font>
                  </td>
               </tr>
               <tr>
                  <td width="25%">
                     <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
                        <a href="../../profile?viewName=wrhino23">wrhino23</a> (1)
                     </font>
                  </td>
                  <td width="25%">
                     <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
                        defeats
                     </font>
                  </td>
                  <td width="25%">
                     <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
                        <a href="../../profile?viewName=daxoux">daxoux</a> (8)
                     </font>
                  </td>
                  <td width="25%">
                     <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
                        2-0
                     </font>
                  </td>
               </tr>
               <tr>
                  <td width="25%">
                     <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
                        <a href="../../profile?viewName=bob_azari">bob_azari</a> (2)
                     </font>
                  </td>
                  <td width="25%">
                     <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
                        defeats
                     </font>
                  </td>
                  <td width="25%">
                     <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
                        <a href="../../profile?viewName=schipy">schipy</a> (7)
                     </font>
                  </td>
                  <td width="25%">
                     <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
                        3-1
                     </font>
                  </td>
               </tr>
               <tr>
                  <td width="25%">
                     <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
                        <a href="../../profile?viewName=ediscs">ediscs</a> (3)
                     </font>
                  </td>
                  <td width="25%">
                     <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
                        defeats
                     </font>
                  </td>
                  <td width="25%">
                     <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
                        <a href="../../profile?viewName=andru">andru</a> (6)
                     </font>
                  </td>
                  <td width="25%">
                     <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
                        2-0
                     </font>
                  </td>
               </tr>
               <tr>
                  <td width="25%">
                     <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
                        <a href="../../profile?viewName=vanluu">vanluu</a> (4)
                     </font>
                  </td>
                  <td width="25%">
                     <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
                        defeats
                     </font>
                  </td>
                  <td width="25%">
                     <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
                        <a href="../../profile?viewName=topspot">topspot</a> (5)
                     </font>
                  </td>
                  <td width="25%">
                     <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
                        3-1
                     </font>
                  </td>
               </tr>
            </table>
            <table width="95%" align="center" border="0" cellpadding="0" cellspacing="0">
               <tr>
                  <td>&nbsp;</td>
               </tr>
               <tr bgcolor="<%= bgColor1 %>">
                  <td colspan="4">
                     <font face="Verdana, Arial, Helvetica, sans-serif" size="2" color="white">
                        <a name="tournament4BPlayoff2"><b>Round 2</b>
                     </font>
                  </td>
               </tr>
               <tr>
                  <td width="25%">
                     <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
                        <a href="../../profile?viewName=ediscs">ediscs</a> (6)
                     </font>
                  </td>
                  <td width="25%">
                     <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
                        defeats
                     </font>
                  </td>
                  <td width="25%">
                     <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
                        <a href="../../profile?viewName=wrhino23">wrhino23</a> (1)
                     </font>
                  </td>
                  <td width="25%">
                     <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
                        2-0
                     </font>
                  </td>
               </tr>
               <tr>
                  <td width="25%">
                     <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
                        <a href="../../profile?viewName=vanluu">vanluu</a> (4)
                     </font>
                  </td>
                  <td width="25%">
                     <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
                        defeats
                     </font>
                  </td>
                  <td width="25%">
                     <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
                        <a href="../../profile?viewName=bob_azari">bob_azari</a> (2)
                     </font>
                  </td>
                  <td width="25%">
                     <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
                        2-0
                     </font>
                  </td>
               </tr>
            </table>
            <table width="95%" align="center" border="0" cellpadding="0" cellspacing="0">
               <tr>
                  <td>&nbsp;</td>
               </tr>
               <tr bgcolor="<%= bgColor1 %>">
                  <td colspan="4">
                     <font face="Verdana, Arial, Helvetica, sans-serif" size="2" color="white">
                        <a name="tournament4BPlayoff3"><b>Round 3 Final!</b>
                     </font>
                  </td>
               </tr>
               <tr>
                  <td width="25%">
                     <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
                        <a href="../../profile?viewName=ediscs">ediscs</a> (4)
                     </font>
                  </td>
                  <td width="25%">
                     <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
                        defeats
                     </font>
                  </td>
                  <td width="25%">
                     <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
                        <a href="../../profile?viewName=vanluu">vanluu</a> (2)
                     </font>
                  </td>
                  <td width="25%">
                     <font face="Verdana, Arial, Helvetica, sans-serif" size="2">

                     </font>
                  </td>
               </tr>
            </table>
         </td>
      </tr>
   </table>

   <%@ include file="../../end.jsp" %>
