<% pageContext.setAttribute("title", "Sets"); %>
<%@ include file="begin.jsp" %>

        <table border="0" cellpadding="0" cellspacing="0" width="100%">

          <tr>
            <td>

			  <h2>Set-based Ratings at pente.org</h2>
			  
              <b>What are set-based ratings?</b><br>
              To play a rated match against another player you must now play 2 
              games, one as player one and one as player two.  You must win both 
              games to win the set and gain any ratings points.  If you and your 
              opponent split the games, then the set is a draw and no ratings change takes place. <br/>
<br/>
Sets are only required for rated matches, unrated games can still be played as before.<br/>
<br/><br/>
             <b>Why do we need set-based ratings?</b><br>
			 Many players have made the argument that player one in Pente
			 has a large advantage over player 2, and that therefore it is
			 difficult for a player to maintain an accurate rating, due to
			 losses as player 2.  Requiring a set of games be played eliminates
			 the advantage of player 1 in a single game.<br/>
			 <br/>
			 <b>How are set-based ratings implemented in the live game room?</b><br>
			 <ol><br><li>Any rated game will be played in a set, so when 2 players sit down and click play, if the rated checkbox is checked then 2 games must be played, one after the other.<br><li>After game 1 is complete the player's seats are swapped and neither player should be able to leave or change the game/timer/rated, or be able to stand.  Nothing happens until someone clicks play.<br><li>Once a player clicks play to begin game 2 of the set, a timer is started, if the other player doesn't also click play before the timer expires then the player who clicked play can end the set (just like now when a player is disconnected and doesn't return).  They can cancel the set, resign the set or force the resignation of the set.<br><li>After game 2 is complete, the result is either a draw if each player won 1 game, or one player won the set.  A draw will result in no ratings change.<br><li>Resigning.  If you click resign during either game 1 or game 2, you are resigning just the game, not the set. See #7 though for a slight contradiction of that.<br><li>Cancel.  The cancel request is now a cancel set request since it doesn't make any sense to cancel a single game in a set.  Note however that if game 1 was already completed it will still be in the database.<br><li>Player disconnections during game 1.  If a player is disconnected during game one and doesn't return within 7 minutes, the remaining player may force the resignation of the <b>set</b>, or resign the <b>set</b>.<br><li>Player disconnections during game 2.  If a player is disconnected during game two and doesn't return within 7 minutes, the remaining player may force the resignation of the <b>game</b>, or resign the <b>game</b>.<br><li>There are a few other things that can happen in the period of time between game 1 and game 2 involving disconnections.  If a player is disconnected between game 1 and game 2, the same timer mentioned in #3 is started if it wasn't already started.  Also, if for example player 1 clicks play, then gets disconnected the timer will reset to the full 7 minutes to give player 1 the full time to return.  And if a player disconnects, waits for 6 minutes to return and then gets disconnected again, the timer will not reset, so its a total of 7 minutes that a player can "stall" the set from continuing.<br></ol>

            </td>
			 
            </td>
          </tr>
        </table>
        <br>

<%@ include file="end.jsp" %>
