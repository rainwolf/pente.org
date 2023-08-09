<% pageContext.setAttribute("title", "Tournament 4 Rules/Format"); %>
<%@ include file="../../begin.jsp" %>

<table border="0" cellpadding="0" cellspacing="0" width="100%">
   <tr>
      <td bgcolor="<%= bgColor1 %>">
         <font face="Verdana, Arial, Helvetica, sans-serif" size="2" color="white"><b>
            Tournament 4 Rules/Format<br>
         </b></font>
      </td>
   </tr>
   <tr>
      <td>
         <font face="Verdana, Arial, Helvetica, sans-serif" size="2">

            <center>Winter 2003 real-time Pente Championship (Pente.org Tournament #4)<br>
               Two sections beginning January 14th, 2003<br>
            </center>
            <br>
            <b>Table of Contents</b><br>
            <b><a href="#sectionA">Section A</a></b><br>
            <b>&nbsp;&nbsp;&nbsp;<a href="#sectionA-Eligibility">Eligibility requirements</a></b><br>
            <b>&nbsp;&nbsp;&nbsp;<a href="#sectionA-format">Section format and pairings</a></b><br>
            <b>&nbsp;&nbsp;&nbsp;<a href="#sectionA-matches">Matches and time controls</a></b><br>
            <br>
            <b><a href="#sectionB">Section B</a></b><br>
            <b>&nbsp;&nbsp;&nbsp;<a href="#sectionB-Eligibility">Eligibility requirements</a></b><br>
            <b>&nbsp;&nbsp;&nbsp;<a href="#sectionB-format">Section format and pairings</a></b><br>
            <b>&nbsp;&nbsp;&nbsp;<a href="#sectionB-pairings">Pairings for regular rounds</a></b><br>
            <b>&nbsp;&nbsp;&nbsp;<a href="#sectionB-matches">Matches and time controls for regular rounds</a></b><br>
            <b>&nbsp;&nbsp;&nbsp;<a href="#sectionB-pairings2">Pairings for playoffs</a></b><br>
            <b>&nbsp;&nbsp;&nbsp;<a href="#sectionB-matches2">Matches and time controls for playoffs</a></b><br>
            <br>
            <b>General</b><br>
            <b>&nbsp;&nbsp;&nbsp;<a href="#general-match">Match and pairings issues (both sections)</a></b><br>
            <b>&nbsp;&nbsp;&nbsp;<a href="#general-rules">Rules and information (both sections)</a></b><br>
            <b>&nbsp;&nbsp;&nbsp;<a href="#general-tips">Tournament tips</a></b><br>
            <br><br>
            <b><a name="sectionA">Section A (Double-Elimination)</b><br>
            Director: Mark Mammel (I.D. mmammel)<br>
            Co-director: Joe King (I.D. joeking)<br>
            <br>
            <b><a name="sectionA-Eligibility">Eligibility requirements:</b><br>
            Players will be placed into section A if they are rated at least 1600 at
            Pente.org or if they do not meet the other eligibility requirements for section B.
            Optional: If a player wishes to play in section A regardless of their rating,
            they may do so at sign-up time.<br>
            <br>
            <b><a name="sectionA-format">Section format and pairings:</b><br>
            <ol>
               <li>Standard double elimination format with 2 brackets. All players
                  start out in bracket #1. When a match is lost in bracket #1, player
                  drops from bracket #1 to bracket #2. When a match is lost in bracket
                  #2, player is eliminated.
               </li>
               <li>Players only play against others in their own bracket until there
                  is only one player left in bracket #1. At that time, the brackets
                  are combined. Players are eliminated when they have lost 2 matches.
                  The rounds continue until all players except one have lost 2 matches.
                  The one player left is the section A champion.
               </li>
               <li>All players are seeded according to their current rating at Pente.org
                  before the start of the tournament. Provisional players (players
                  with less than 20 rated games played) will have their ratings
                  adjusted downward for seeding purposes by 20 points for each game
                  less than 20 games played. For instance, a player with an 1800
                  rating and 15 games played would have his rating adjusted down by
                  5 x 20 or 100 points to 1700.
               </li>
               <li>For players who are tied on rating, the following tiebreakers will
                  apply for seeding purposes:
                  <ol>
                     <li>1st tiebreaker - If both players played in the last Pente.org tournament,
                        the one who was eliminated latest will get the higher seed.
                     </li>
                     <li>2nd tiebreaker - If one or both players did not play in the
                        last Pente.org tournament or if they were eliminated in the same
                        round, the decimal part of each player�s rating will be
                        obtained. The player with the higher rating will get the
                        higher seed.
                     </li>
                  </ol>
               </li>
               <li>The highest rated player in the section would get the #1 seed, the
                  2nd highest rated, the #2 seed, etc. All seedings will remain the
                  same throughout the tournament with one exception. See number 6.
               </li>
               <li>When there is only one un-defeated player left, that player will
                  acquire the #1 seed. All others will be kept in the same order but
                  seeded beneath him. If the un-defeated player subsequently loses so
                  that all players left have one loss, then the seedings revert back to
                  the way that they were originally.
               </li>
               <li>The pairings in each round within each bracket will be the highest
                  seed vs. the lowest seed, the 2nd highest vs. 2nd lowest, etc.
                  The pairings are kept in order of the highest seeded player in each
                  pairing within the bracket. For example, if the #1 and #2 seeds are
                  not in the bracket, then #3 vs. #8 would be pairing #1, #4 vs. #7,
                  pairing #2, #5-#6, pairing #3, etc.
               </li>
               <li>If there are an odd # of players in a bracket, then the highest-seeded
                  player who has not already received one will receive a bye in that
                  round. If all players in the bracket have received a bye, then the
                  highest-seeded player who has not already had 2 byes will receive a
                  2nd bye.
               </li>
            </ol>

            <b><a name="sectionA-matches">Matches and time controls:</b><br>
            <ol>
               <li>A match consists of one or more 2-game sets. Sets consist of 2 games,
                  each player moving first in one game. The winner of the match is
                  the first player to win both games of a set. In other words, the
                  winner is the first player to lead the match by 2 games, i.e. 2-0,
                  3-1, etc.
               </li>
               <li>The time limit for each game will be 30 minutes/player, 5 seconds
                  incremental time. Incremental time is the amount of time that is
                  added to a player�s total time with each move. IMPORTANT: You will
                  need to change the site default of 20 minutes/0 seconds inc. time.
               </li>
               <li>If 6 games (3 sets) have been played and the match is still tied, if
                  BOTH players agree, the time controls can remain unchanged. But if
                  ONE player wishes OR if the players feel there will be a problem in
                  completing their match within the time period for the round, the
                  following reduced time controls will be applied:
                  <ol>
                     <li>4th set (games 7-8); 20 minutes, 3 seconds incremental time.</li>
                     <li>5th set (games 9-10); 15 minutes, 2 seconds incremental time.</li>
                     <li>6th set+ (game 11+); 10 minutes, 0 seconds incremental time.</li>
                  </ol>
               </li>
               <li>Ties are not allowed with one exception. If a match is still tied
                  after 6 sets (12 games) have been played, a tie may be declared by
                  the section director and both players will advance to the next round
                  and remain in the same bracket under the following conditions:
                  <ol>
                     <li>If it is the last 3 days of the time period AND the players
                        cannot agree on a time to complete their match AND both
                        players have made every effort to break the tie. In order
                        to claim the tie, at least one player must immediately E-mail
                        the section director and copy in his opponent after the last
                        game has been completed. The director may request that the
                        players forward all E-mails related to the coordination of
                        playing time, in order to prove that every effort has been
                        made to break the tie.
                     </li>
                     <li>If it is before the last 3 days of the time period, the players
                        must agree on a time period to continue their match. If they
                        can�t agree on a time, see number 1 under General match and
                        pairings issues.
                     </li>
                  </ol>
               </li>
            </ol>

            <b><a name="sectionB">Section B rules (Swiss system)</b><br>
            Director: Gary Barnes (I.D. progambler)<br>
            Co-director: Joe King (I.D. joeking)<br>
            <br>
            <b><a name="sectionB-Eligibility">Eligibility requirements:</b><br>
            Players will be placed into section B if they are currently rated less than 1600 at Pente.org and have one
            of the 2 following conditions:
            <ol>
               <li>Have completed at least 10 rated games vs. at least 3 different opponents at Pente.org AND have never
                  had an established rating of 1750 or higher. An established rating is obtained once a player has
                  completed 20 rated games, so this would not apply to players who have completed 10-19 rated games.
               </li>
               <li>Have not met the minimum games requirement in number 1 but have provided the directors with other
                  information regarding their playing ability. See number 2 under General rules for more details.
               </li>
            </ol>

            <b><a name="sectionB-format">Section format:</b>
            <ol>
               <li>Swiss system format. All players play matches in all rounds. The # of rounds is determined by the #
                  of players. Less than 16 players, 4 rounds, 16-31 players, 5 rounds, 32-63 players, 6 rounds, 64 or
                  greater players, 7 rounds.
               </li>
               <li>After the rounds have been played, the top 4 (if 4-5 rounds) or 8 (if 6-7 rounds) players with the
                  most total tournament GAMES won advance to a single elimination playoff. The winner of the playoffs is
                  the section B champion.
               </li>
            </ol>

            <b><a name="sectionB-pairings">Pairings for regular rounds:</b>
            <ol>
               <li>The pairings in round 1 are random. Wins by all players in their matches are noted.</li>
               <li>After round 1, in each round, players are placed (sorted) by total wins in the tournament. The wins
                  by all of each player�s opponents are also tracked. For players who are tied on total wins, the
                  tiebreaker will be the total tournament wins by all of their opponents to date. If the total of the
                  opponent�s wins is also tied, the tied players will be placed randomly amongst themselves.
               </li>
               <li>Pairings after round 1 are from the places established in number 2 and will be #1 vs. #2, #3 vs. #4,
                  #5 vs. #6, etc. Otherwise stated, players always play someone who has the same or a similar # of total
                  wins. A running total continues to be kept of total wins by each player for re-placing in each
                  subsequent round.
               </li>
               <li>Vacation time is allowed. If requested in advance of the start of the tournament, a player may
                  receive a bye in any ONE round except the FINAL round. This would be considered a HALF-bye so the
                  player will receive 2 wins, 2 losses.
               </li>
               <li>If there are an odd # of players available for play in the round, then the lowest placed player who
                  has NOT already received a bye will receive a bye. This would be considered a FULL-bye, so the player
                  will receive 4 wins, 0 losses.
               </li>
            </ol>

            <b><a name="sectionB-matches">Matches and time controls for regular rounds:</b>
            <ol>
               <li>A match consists of 4 games, each player moving first in 2 games. No additional games need to be
                  played, even if the match results in a 2-2 tie. The wins for each player are simply added to his/her
                  running tournament total.
               </li>
               <li>All 4 games should be completed in one sitting in order to avoid coordination issues. If the players
                  wish, they can play 2 games one day and 2 games another day, but that is not encouraged. No provisions
                  are made for unusual situations in the regular rounds. If players have difficulty in coordinating
                  suitable playing times either to begin their match or to continue a match, see number 1 under General
                  match and pairings issues.
               </li>
               <li>The time limit for each game will be 20 minutes/player, 0 seconds incremental time. This is the site
                  default time, so you will NOT need to change that.
               </li>
            </ol>

            <b><a name="sectionB-pairings2">Pairings for playoffs:</b>
            <ol>
               <li>After the regular rounds are complete, the top 4 (if 4-5 rounds) or top 8 (if 6-7 rounds) players
                  will advance to a single elimination playoff. The players in the playoffs will be seeded in order of
                  their total wins, so all ties amongst the top players will be broken, as follows:
                  <ol>
                     <li>1st tiebreaker - Total tournament wins by all opponents, as done for pairings in the regular
                        rounds.
                     </li>
                     <li>2nd tiebreaker - Total forfeit points, as follows:
                        <ol>
                           <li>If any opponent of the tied player forfeited a match to a player other then the tied
                              player, the tied player�s opponent received 0 wins and hence put the tied player at a
                              slight disadvantage on the 1st tiebreaker. PLUS 1 forfeit point for each opponent that
                              forfeited to someone else.
                           </li>
                           <li>If any opponent forfeited a match to the tied player, the tied player received a slight
                              advantage in total tournament wins MINUS 2 forfeit points for each opponent that forfeited
                              to him in the last round. MINUS 1 forfeit point for each opponent that forfeited to him
                              before the last round.
                           </li>
                        </ol>
                     </li>
                     <li>Final tiebreaker - preliminary playoff. If 2 players are still tied
                        after all tiebreakers have been applied, a playoff match will be
                        contested to determine the seed of the players or who advances to the
                        playoffs. The format of the match would be the same as the single
                        elimination playoff match format shown below. In the extremely rare
                        situation that 3 or more players are still tied after all tiebreakers
                        have been applied, the section director will make a decision based on
                        the situation that will be as fair as possible to the most players,
                        since it�s not possible to cover all scenarios.
                     </li>
                  </ol>
               </li>
               <li>The pairings for the playoff rounds will be the highest remaining seed vs. the lowest remaining seed,
                  the 2nd highest vs. 2nd lowest, etc.
               </li>
            </ol>

            <b><a name="sectionB-matches2">Matches and time controls for playoffs:</b>
            <ol>
               <li>A match consists of one or more 2-game sets. Sets consist of 2 games, each player moving first in one
                  game. The winner of the match is the first player to win both games of a set. In other words, the
                  winner is the first player to lead the match by 2 games, i.e. 2-0, 3-1, etc. The winner advances to
                  the next playoff round, the loser is eliminated. Playoffs continue until 1 player remains.
               </li>
               <li>For match coordination issues, see nos. 1-3 under General match and pairings issues.</li>
               <li>The time limit for each game will be the same as the regular rounds, 20 minutes.</li>
               <li>If 6 games (3 sets) have been played and the match is still tied, if BOTH players agree, the time
                  control can remain unchanged. But if ONE player wishes OR if the players feel there will be a problem
                  in completing their match within the time period for the round, the following reduced time controls
                  will be applied:
                  <ol>
                     <li>4th set (games 7-8), 15 minutes.</li>
                     <li>5th set+ (games 9+), 10 minutes.</li>
                  </ol>
                  <b>IMPORTANT</b>: For reduced time controls, you will need to change the site default time of 20
                  minutes.
               </li>
               <li>The time period to complete matches for the playoff rounds will be the same as for regular rounds.
                  Because of the requirement that a match be won by 2 games, if a match is still tied after 6 sets (12
                  games), the section director may allow an extension of one week to the round. In order for the
                  extension to be granted, at least one player must immediately E-mail the section director and copy in
                  his opponent after the last game has been completed. The following conditions must also apply:
                  <ol>
                     <li>The last game of the match was completed in the last 3 days of the time period.</li>
                     <li>The players cannot agree on a time to complete their match.</li>
                     <li>Both players have made every effort to break the tie. The section director may request that the
                        players forward all E-mails related to the coordination of playing time, in order to prove that
                        every effort has been made.
                     </li>
                  </ol>
               </li>
            </ol>

            <b><a name="general-match">General match and pairings issues (both sections)</b>
            <ol>
               <li>Players must arrange with their opponents a time to meet and play their matches. A player who is not
                  able to meet at any time during the time period for the round will forfeit. If a suitable playing time
                  cannot be agreed upon by the beginning of the match, at least one of the players should E-mail the
                  section director and copy in his opponent. The director will assign two different times and days for
                  the match to be held. If the players can't agree to either, he will assign one time. Either player not
                  able to make that time will forfeit.
               </li>
               <li>For elimination matches that must be won by 2 games (Applicable to ALL of section A and playoff
                  rounds ONLY for section B), if 2 sets (4 games) have been played all in one sitting and they are both
                  tied, the completion of the match may be adjourned until another day, but ONLY if BOTH players wish to
                  do so. If a player (player A) cannot continue even though the other (player B) wishes to do so, then
                  the following will apply:
                  <ol>
                     <li>If it is the last 2 days of the time period for the round, player A will automatically forfeit
                        unless the players can find a suitable time that is still within the time period, either later
                        that day or the next day, to finish their match. Player B is not obligated to continue the
                        match, but can do so if he wishes.
                     </li>
                     <li>If it is before the last 2 days of the time period for the round, player A can avoid a forfeit
                        ONLY if he agrees to a time that is most convenient for player B to continue the match within
                        the time period. Player B should request 2 reasonable times at least 18 hours apart that are
                        most convenient for him. Player A must accept one of them or forfeit the match.
                     </li>
                     <li>In order to claim that player A should forfeit the match, player B should immediately E-mail
                        the section director and copy player A in on it. Failure to do this will result in a double
                        forfeit. In situation 2.2, if player A feels that player B is requesting un-reasonable playing
                        times [such as 3AM in his locale], he can dispute the forfeit and the director will ask player B
                        to give more reasonable playing times.
                     </li>
                  </ol>
               </li>
               <li>For elimination matches that must be won by 2 games (Applicable to ALL of section A and playoff
                  rounds ONLY for section B), if at least 3 sets (6 games) have been played all in one sitting and they
                  are all tied, the completion of the match may be adjourned until another day if there is time and at
                  least ONE player wishes to do so. The following will apply:
                  <ol>
                     <li>If only one player (player A) wishes to adjourn AND it is the last 2 days of the time period
                        for the round, he could potentially forfeit the match. Rules 2.1 and 2.3 above will apply.
                     </li>
                     <li>If BOTH players wish to adjourn AND it is the last 2 days of the time period for the round, the
                        players must come up with a suitable time that is still within the time period, either later
                        that day or the next day. If the players cannot agree on a time, then BOTH players will forfeit.
                     </li>
                     <li>If it is before the last 2 days of the time period for the round, there is no penalty for
                        player A requesting an adjournment when player B did not want to. Unless the time frame is
                        tight, no one will be obligated to play more than 6 games in one sitting, but can do so if they
                        wish. Like starting a round, the players should agree on a time to continue their match. If they
                        can�t agree on a time, see number 1 under General match and pairings issues.
                     </li>
                  </ol>
               </li>
               <li>Repeat pairings (players who have already played) are avoided until the latest possible time. If the
                  normal pairing process (seeding process in section A) puts 2 players against one another for the 2nd
                  time, the following will occur with the lower paired player (player A) within the pairing:
                  <ol>
                     <li>Player A will be swapped with the lower paired player that is down 1 pairing.</li>
                     <li>If still a repeat pairing, player A will be swapped with the lower paired player that is up 1
                        pairing.
                     </li>
                     <li>If still a repeat pairing, player A will replace the lower paired player that is down 2
                        pairings (player B). Player B will be moved up one pairing and replace the lower paired player
                        in that pairing (player C). Player C will be moved up to the original pairing and replace player
                        A.
                     </li>
                     <li>If there is still a repeat pairing, player A will replace the lower paired player that is up 2
                        pairings. The other lower paired players within each pairing will move down in exactly the
                        opposite fashion in rule 4.3.
                     </li>
                     <li>If there is still a repeat pairing, rules 4.3 and 4.4 will be repeated for a move down of 3
                        pairings, then a move up of 3 pairings, then down and up of 4 pairings, etc.
                     </li>
                     <li>If all moves result in a repeat pairing, there will be no change to any of the pairings. This
                        would generally only happen in the last 2-3 rounds of the section A.
                     </li>
                  </ol>
               </li>
            </ol>

            <b><a name="general-rules">General rules and information (both sections)</b>

            <ol>
               <li>The directors will place players into the appropriate sections as specified in the rules above. All
                  player�s ratings are taken at a specific time before the tournament starts as determined by the
                  directors. The ratings collected at that time are what will be used to determine which section that
                  players will play in and how players will be seeded in section A.
               </li>
               <li>If a player wishes to play in section B and his rating qualifies him to do so, he must have completed
                  at least 10 rated games vs. at least 3 different opponents at Pente.org or be able to provide
                  additional information on current ability. If he has not met this requirement, he can still play in
                  section B, but the following instructions should be followed:
                  <ol>
                     <li>Sign-up using the normal process.</li>
                     <li>After sign-up, he should send an E-mail to the directors and provide a user I.D. at another
                        site in which he has played Pente such as IYT at www.itsyourturn.com or PBEM at www.gamerz.net.
                     </li>
                     <li>If a player has never played Pente anywhere else or the information in b. cannot be provided,
                        he must complete the minimum games requirement shown above before the end of the sign-up period,
                        otherwise he will be entered in section A.
                     </li>
                  </ol>
               </li>
               <li>The tournament directors and co-directors will be playing in the tournament. If there is a dispute
                  with one of the directors, it should be sent to the corresponding section co-director. If there is a
                  dispute with one of the co-directors, it should be sent to the corresponding section director.
               </li>
               <li>Rounds will start every 2 weeks. The time period to play matches in each round will be about 10-11
                  days, starting on a Thursday or Friday and ending on a Monday. During the weeks of Thanksgiving,
                  Christmas, and potentially at other times at the discretion of the directors, the time period will be
                  extended by one week.
               </li>
               <li>A break for the players between games of a match of 5-10 minutes is suggested as a guideline. Players
                  can negotiate this between themselves but should not pressure their opponents to start the next game
                  immediately if they are not ready.
               </li>
               <li>All games should be played as rated games and the time controls should be changed if necessary as
                  specified in the rules. If a game has already started and it is discovered that the game was started
                  with the incorrect time controls, the following guidelines are suggested but not required:
                  <ol>
                     <li>If 7 or less moves have been played and if both players agree, the game can be cancelled and
                        the time controls corrected. The new game can be started over from the beginning or it can be
                        replayed to the position when the error was discovered.
                     </li>
                     <li>If more than 7 moves have been played, the game should continue in order to prevent matches
                        from taking too long.
                     </li>
                  </ol>
               </li>
               <li>If Pente.org site is down or not available for play at any time, the time period for the round may be
                  extended. The following will apply:
                  <ol>
                     <li>If the site is unavailable for less than 6 hours, no extension will occur.</li>
                     <li>If the site is unavailable for at least 6 but less than 30 hours AND it is not available for at
                        least 3 of the hours between 6 PM and midnight Eastern Time U.S., then a 1-day extension
                        (Tuesday) will occur. This will make for a tight time frame before the next round, so players
                        are requested to still complete their matches by Monday, if possible.
                     </li>
                     <li>If the site is unavailable for 30 or more hours, then the time period for the round will be
                        extended by one week.
                     </li>
                  </ol>
               </li>
               <li>If a player is bumped off of Pente.org while playing, the site will automatically give him 5 minutes
                  to return. If he does not return within 5 minutes, the site will automatically give his opponent the
                  option of canceling the game or forcing a forfeit. If his opponent wishes to continue waiting, he
                  should NOT choose either option. If the player returns, then the options will automatically go away
                  and the game will resume. It is completely at the discretion of his opponent what action that he wants
                  to take. If it is an isolated occurrence, it is suggested that players be sporting and continue to
                  wait for a few more minutes. But if it is an ongoing problem in the match or if the player does not
                  return within 10 minutes or more, forcing a forfeit is appropriate in order to prevent abuse of this
                  allowance.
               </li>
               <li>No player may enter the tournament under two or more user I.D.�s.</li>
               <li>No assistance from any other person, computer software, notes, books, game transcripts, or databases
                  is permitted.
               </li>
               <li>Players are not allowed to move pieces (stones) around on any physical or computer-based Pente board
                  in order to determine their moves. If a player has difficulty in seeing the stones on the Pente.org
                  board, he may keep the game position on any other board of his choosing while the game is progressing,
                  but must not vary the position from the actual game position in order to determine possible outcomes
                  of moves.
               </li>
               <li>The use of the UNDO request is permitted, but an opponent is NOT obligated to ACCEPT the request,
                  regardless of how ridiculous the placement of the stone is. In a tournament, accidentally dropping a
                  stone is like taking a hand off after a move while playing in person. The opponent can ALLOW the
                  player to take back the move, but is not obligated to do so.
               </li>
               <li>At the completion of a MATCH, the winner of the match should E-mail the section director and copy in
                  his opponent with the following: the game score (i.e. 2-0, 3-1, 2-2, etc.), the player�s names, and
                  the date(s) played. As an extra precaution against disputes, at the completion of each GAME, a player
                  may press the �Email history� button. This will automatically send the moves of a game to his E-mail
                  address.
               </li>
               <li>All disputes must be brought to the attention of the section director immediately.</li>
               <li>Any player showing poor conduct or sportsmanship can be removed from the tournament at the discretion
                  of the director(s) and/or co-director.
               </li>
               <li>Anyone is allowed to watch games in progress. Comments should be avoided at the table where a
                  tournament game is being played. The following will apply:
                  <ol>
                     <li>If a person who is watching a game isn�t sure if it is a tournament game, he can ask the
                        players.
                     </li>
                     <li>If a person who is watching makes general conversion, the players should politely state �quite
                        please, tournament game�. If the person isn�t aware of the rule, refer him to the tournament
                        rules.
                     </li>
                     <li>In between games of a match, anyone can make general conversion or sporting comments about the
                        game. However once the next game starts, talking should cease once again.
                     </li>
                     <li>See number 15 if someone refuses to refrain from comments during the game. If the player isn�t
                        in the tournament, then other action will be taken.
                     </li>
                  </ol>
               </li>
            </ol>

            <b><a name="general-tips">General tournament tips</b>
            <ol>
               <li>Allow at least 4 hours (section A) or 3 hours (section B) of time for matches. It is not possible to
                  know in advance how much time that games will last or how many games that a match will last (section
                  A). Games could last about one hour (section A) or 40 minutes (section B) each if both players use
                  most of their time.
               </li>
               <li>Before a match begins, players should inform family members and friends that they will be playing a
                  tournament match and if possible, can�t be disturbed. A 5-10 minute break time is suggested and
                  allowed between games. (See number 5 under General rules.)
               </li>
               <li>It is recommended that players play at a table that is completely empty of players before beginning
                  their match. The reason for this is that the player who arrives at a table first before any other
                  players will be �owner� of that table. Only the owner of a table can change the rated status or time
                  controls of the table if needed as specified in the rules (section A only). If ANY other person is at
                  the table first, even if it is one who is only there to watch and is not currently playing, the
                  players who are set to play their match will not be able to change the rated status or time controls
                  of the game until that person leaves the table.
               </li>
               <li>If possible, players should not wait until the last 2 days of the 10-11 day time period to play their
                  matches for the following reasons:
                  <ol>
                     <li>Something could come up that prevents one of the players from playing in a match, resulting in
                        a forfeit.
                     </li>
                     <li>The site could experience problems making it difficult to play or complete a match. (See number
                        7 under General rules.)
                     </li>
                     <li>If the games go longer than expected, one of the players could end up with not enough time to
                        continue that day and/or one of them may not be available to complete the match on a different
                        day within the allotted time period. This would result in a forfeit for one or both players.
                        (See specifics under general match and pairings issues.)
                     </li>
                  </ol>
               </li>
               <li>When coordinating a playing time with an opponent, players should indicate what time zone that they
                  are in. Players from different countries may want to express times in Greenwich Mean Time (GMT).
               </li>
               <li>Always keep all correspondence regarding the coordination of playing time with opponents until the
                  end of each round. This may be needed in case of disputes or unusual situations, which are covered
                  above.
               </li>
            </ol>
         </font>
      </td>
   </tr>
</table>

<%@ include file="../../end.jsp" %>
