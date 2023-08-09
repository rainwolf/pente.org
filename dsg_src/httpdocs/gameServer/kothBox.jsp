<%@ page import="java.util.*,
                 java.text.*,
                 org.pente.game.*,
                 org.pente.gameServer.core.*,
                 org.pente.gameServer.client.web.*" %>


<%
   // SessionListener sessionListener = (SessionListener)
//     application.getAttribute(SessionListener.class.getName());
// List<WhosOnlineRoom> rooms = WhosOnline.getPlayers(globalResources, sessionListener);
%>
<style type="text/css">
    .box {
        width: 200px;
    }
</style>

<div class="box">
   <div class="boxhead">
      <h4>Kings of the Hill</h4>
   </div>
   <div class="boxcontents">
      <table width="100%">
         <tr>
            <!-- <th></th> -->
            <th align="center">Hill</th>
            <th align="center">King</th>
            <!-- <th>Games</th> -->
         </tr>
         <%!
            public class KotHPlayerGame implements Comparable<KotHPlayerGame> {
               private Player player;
               private int game;

               public KotHPlayerGame(Player player, int game) {
                  this.player = player;
                  this.game = game;
               }

               public int getGame() {
                  return game;
               }

               public Player getPlayer() {
                  return player;
               }

               @Override
               public int compareTo(KotHPlayerGame o) {
                  return player.getLastGame().compareTo(o.getPlayer().getLastGame());
               }
            }
         %><%
         List<KotHPlayerGame> players = new ArrayList();
         for (int i = 0; i < CacheKOTHStorer.tbGames.length; i++) {
            Hill hill = kothStorer.getHill(CacheKOTHStorer.tbGames[i]);
            if (hill != null) {
               Player player = hill.getKingPlayer();
               if (player != null) {
                  players.add(new KotHPlayerGame(player, CacheKOTHStorer.tbGames[i]));
               }
            }
         }
         for (int i = 0; i < CacheKOTHStorer.liveGames.length; i++) {
            Hill hill = kothStorer.getHill(CacheKOTHStorer.liveGames[i]);
            if (hill != null) {
               Player player = hill.getKingPlayer();
               if (player != null) {
                  players.add(new KotHPlayerGame(player, CacheKOTHStorer.liveGames[i]));
               }
            }
         }
         Collections.sort(players, Collections.reverseOrder());

         for (int i = 0; i < players.size(); i++) {
            if (i > 5) {
               break;
            }
            DSGPlayerData d = dsgPlayerStorer.loadPlayer(players.get(i).getPlayer().getPid());
            String color = i % 2 == 1 ? "style=\"background:white\"" : "";
            // DSGPlayerGameData dsgPlayerGameData = d.getPlayerGameData(("Mobile".equals(room.getName())?GridStateFactory.TB_PENTE:GridStateFactory.PENTE));
      %>
         <tr <%= color %>>
            <!-- <td>&nbsp;&nbsp;</td> -->
            <td><a
               href="/gameServer/stairs.jsp?game=<%=players.get(i).getGame()%>"><b><%=(players.get(i).getGame() > 50 ? "TB-" : "") + GridStateFactory.getGameName(players.get(i).getGame())%>
            </b></a></td>
            <td align="right">
               <%@ include file="playerLink.jspf" %>&nbsp;
            </td>
         </tr>
         <% } %>
      </table>
      <div style="float:right;padding:2px 2px;">
         <a class="boldbuttons" href="stairs.jsp"><span>More Hills and Kings&rarr;</span></a>
      </div>
      <br style="clear:both">
   </div>
</div>