<font face="Verdana, Arial, Helvetica, sans-serif" size="2">
<h2>Playing - Game Rules</h2>

The rules for the games at Pente.org are pretty simple, yet the games themselves
and the strategy behind the games is hard to master.  So here are the rules,
for help on strategy try the 
<a href="helpWindow.jsp?file=tutorials">Tutorials</a>.  The tutorials
also cover the rules of Pente with some helpful examples.<br>

<ul>
  <li><a href="#common"><b>Common Rules</b></a></li>
  <li><a href="#gomoku"><b>Gomoku Rules</b></a></li>
  <li><a href="#pente"><b>Pente Rules</b></a></li>
  <li><a href="#keryo"><b>Keryo-Pente Rules</b></a></li>
  <li><a href="#g-pente"><b>G-Pente Rules</b></a></li>
  <li><a href="#d-pente"><b>D-Pente Rules</b></a></li>
  <li><a href="#poof-pente"><b>Poof-Pente Rules</b></a></li>
  <li><a href="#boat-pente"><b>Boat-Pente Rules</b></a></li>
  <li><a href="#connect6"><b>Connect6 Rules</b></a></li>
  <li><a href="#dkeryo"><b>DK-Pente Rules</b></a></li>
  <li><a href="#speed"><b>Speed Games</b></a></li>
</ul>
<br>

<a name="common"><u>Common Rules</u></a><br>
All games are played on a 19x19 board of intersecting lines.  Each player places
stones on the intersecting lines (not in between the lines).  Once a stone is
played it can't be moved again (except if captured in certain games).  All games
start with player 1's first move in the middle of the board.  Play then continues
by alternating turns until one player wins.<br>
<br>
<a name="gomoku"><u>Gomoku</u></a><br>
Gomoku is the simplest of the games.  To win, place 5 of your stones in a
straight continuous line (either horizontally, vertically or diagonally).
Whoever does this first wins the game.  One note, you must get exactly 5 in a
row to win. 6 or more of your stones in a row is called an "overline" and does
not count as a win, play continues.<br>
<br>
<a name="pente"><u>Pente</u></a><br>
Pente is like Gomoku but with a twist.  You can win at Pente just like in Gomoku,
by getting 5 of your stones in a straight continuous line.  (Also in Pente you
can win by getting more than 5 in a row, unlike Gomoku).<br>
<br>
The twist to Pente is that you can capture your opponents stones, removing them
from the board. Capturing occurs if you place one of your stones on both sides
of a pair of your opponents stones.  For example, if the stones are like this
XOO and you place your stone so it becomes XOOX, then your opponents stones are
removed from the board, leaving X__X.  Capturing is good to slow your opponent
from getting 5 in a row, but you can also win at Pente by capturing.  If you
capture 10 or more of your opponents stones you win (5 or more captures).<br>
<br>
Another thing to realize is you can't play into a capture. If the board position
is like this XO_X, and you place your stone such that the board is like XOOX,
then your stones are NOT captured.  (Note that <a href="#poof-pente">Poof-Pente</a>
is a variation where you CAN play into captures)<br>
<br>
One final rule of Pente is called the "Tournament Rule".  If you play rated
games at Pente.org, the tournament rule is used.  The tournament rule was proposed by
Tom Braunlich to make the game more fair for player 2. It restricts player 1's
second move so that player 1's second move must be at least 3 intersections
away from the center of the board.<br>
<br>
<a name="keryo"><u>Keryo-Pente</u></a><br>
Keryo-Pente is a variation of Pente proposed in 1983 by World Pente Champion
Rollie Tesh.  Again you can win by placing 5 in a row.
The variation has to do with the captures, you can still capture 2 stones as
in Pente, but you can also capture 3 stones in the same manner.  In order to win
by capturing you must now capture 15 or more stones.  Although technically only
a variation of Pente, games turn out to be very different.<br>
<br>
<a name="g-pente"><u>G-Pente</u></a><br>
G-Pente is a variation of Pente proposed by Gary Barnes.  Again the reason for the variation is to make the game more fair
for player 2.  This variation restricts player 1's second move, just like the
tournament rule.  It additionally prohibits playing on the 4th and 5th
intersections away from the center of the board that are one the same horizontal
or vertical line as player 1's first move.  (These moves are the most common
second moves for white).<br>
<br>
<a name="d-pente"><u>D-Pente</u></a><br>
D-Pente is a variation of Pente proposed by Don Banks. Again the reason
for the variation is to make the game more fair for player 2. This variation is
very different from the others however.<br>
<br>
Play starts with white's first move at
the middle as always.  After that move however, player 1 continues to be in
control and places the next stone for his/her opponent.  Player 1 continues to
be in control and places the second move for each player. (So player 1 makes the
first 4 moves of the game, but the stone colors still alternate).  At this point
in the game player 2 gets control.  Player 2 decides to continue playing as
player 2, or decides to <b>swap</b> seats and take over as player 1!  After that
decision is made, play continues just as in Pente with whoever is now player 1
making the next move.<br>
<br>
Hopefully you can see how this makes the game more balanced for player 2, in
fact player 2 now has the advantage because player 2 decides after move 4 which
side to play.  So player 1 has to come up with a set of first 4 moves that are
balanced, because if the position isn't balanced, player 2 will probably win
(because they can swap!)<br>
<br>
<a name="poof-pente"><u>Poof-Pente</u></a><br>
Poof-Pente is a variation of Pente proposed by Tom Cooley.
The main difference is that you can play into a capture!  (e.g., from XO_X
player plays XOOX) then the stones are "poofed" (removed from the board and
counted as captures). Normal captures are still allowed and the number of
captures to win remains at 10 stones. Note that more than 2 stones could be
poofed in one move, specifically up to 5 at once! Also note that my current
implementation performs the following actions in sequence after a move:
<ol>
  <li>Check if move captured any opponents stones, if so remove stones from the
   board.
  <li>Check if move creates a poof position, if so remove stones from the board.
  <li>Check if player has a 5-in-a-row, if so the player wins.
</ol>
There are some weird scenarios that can occur at the end of the game, for
example a player could capture and be poofed in the same move and the capture
count could end up as 10-10. My current implementation keeps the game going in
this case until one player has an advantage (i.e., gets more captured stones
or gets five-in-a-row). There are no draws right now in Poof-Pente.<br>
<br>
<a name="boat-pente"><u>Boat-Pente</u></a><br>
Boat-Pente is a variation of Pente proposed to Pente.org by player zoeyk.  Also known
as "boat rules" Pente. Boat-Pente aka "Boat Rules" Pente was originally invented in 
the mid 1980's by a man named "Jay E. Hoff". The late Jay E. Hoff, (also know at this 
website by the player name of jayehoff) was the stepfather of player zoeyk (aka Zoey King).<br>
<br>
The main difference between this game and Pente is in the end game.  
Once a Pente is made (5 stones in a row), the game can continue as long as the 
opponent is able to capture across the Pente!  So in the example game below
white hasn't won yet because black can capture on the next turn, removing the
Pente.  If after black moves the Pente is still in place then white wins.<br>
<center><img src="../gameServer/images/boat_pente_ex1.jpg"></center>
<br>
<br>
Capturing 5 pairs is still a win like in Pente and the tournament rule is used
for rated games.<br>
<br>

<a name="connect6"><u>Connect6</u></a><br>
Connect6 was introduced by Professor I-Chen Wu at the National Chiao Tung University.
The rules are similar to gomoku, the object is to be the first player to get 6
pieces in a row, either vertically, horizontally or diagonally.<br>
<br>
Player one plays one stone at the center of the board.  After that each player
gets to place <b>two</b> stones on the board at a time.<br>
More info at <a href="http://en.wikipedia.org/wiki/Connect6">http://en.wikipedia.org/wiki/Connect6</a>.<br>
<br>
<a name="dkeryo"><u>DK-Pente</u></a><br>
DK-Pente combines the rules of D-Pente and Keryo-Pente.
<br>
<a name="speed"><u>Speed Games</u></a><br>
All of the above games can be played untimed, with a "normal" time limit, or
as <b>Speed</b> games.  Speed games are played so differently that they are
really in their own category, so at Pente.org they are treated differently.  Each player 
has two ratings for each game, one for normal or untimed games, and one 
for speed games. This helps the ratings system stay more accurate since each players skills at 
speed vs. normal games can be very different.<br>
<b>Note:</b> Do not create a separate account for speed games.<br>
<br>
So how does Pente.org define a <b>Speed</b> game?<br>
A speed game = (initial time minutes * 60 + incremental time seconds * 15) <= 330<br>
A normal game = (initial time minutes * 60 + incremental time seconds * 15) > 330<br>
<br>
Some examples:<br>
Initial time/Incremental time (5/0) = Speed game<br>
5 * 60 + 0 * 15 = 300<br>
Initial time/Incremental time (6/0) = Normal game<br>
6 * 60 + 0 * 15 = 360<br>
Initial time/Incremental time (5/2) = Speed game<br>
5 * 60 + 2 * 15 = 330<br>
Initial time/Incremental time (4/6) = Speed game<br>
4 * 60 + 6 * 15 = 330<br>
Initial time/Incremental time (4/7) = Normal game<br>
4 * 60 + 7 * 15 = 345<br>
Initial time/Incremental time (1/5) = Speed game<br>
1 * 60 + 5 * 15 = 135<br>
<br>
When the time controls are updated in a game table, the above formula will be
run to determine if the game is a speed game or not, and you will know the type
of the game because the game board will display "Speed" if the game is a speed
game.<br>
<br>
<b><a href="helpWindow.jsp?file=playTb">&lt;&lt; Turn-based Games</a>&nbsp;&nbsp;&nbsp;
<b><a href="helpWindow.jsp?file=tutorials">&gt;&gt; Tutorials</a>
</font>