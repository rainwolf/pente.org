<font face="Verdana, Arial, Helvetica, sans-serif" size="2">
<h2>Game Database</h2>

        There are 4 main sections of interest in the Game Database tool.
        <ul>
         <li><a href="#board">Game Board</a></li>
         <li><a href="#moves">Search Result Moves</a></li>
         <li><a href="#games">Search Result Games</a></li>
         <li><a href="#filter">Filter Options</a></li>
        </ul>
        <br>
        <h3><a name="board">1. Game Board</a></h3>
        The first will be familiar to you, its a game board! On it will appear the moves of the game you've entered 
  as white and black pieces. The search results will be displayed as green pieces. 
  The database works by entering a position you're interested in and clicking 
  the <b>Search</b> button. This will return a set of moves that have been 
  made in other games that at one point had the same position you searched for. 
  The moves are possible next moves. To setup a position you simply click your 
  mouse once over the position you want. Continue doing so until you reach your 
  desired position. You can also navigate back and forth through the moves by 
  using the <b>&lt;&lt;</b> <b>&lt;</b>, <b>&gt;</b>, and <b>&gt;&gt;</b> 
  buttons. The <b>Clear</b> button should be self-explanatory. The <b>Reset</b> 
  button will return the board to the position you searched for.<br>
  <br>
  <h3><a name="moves">2. Search Result Moves</a></h3>
  The second section is a 
  table containing the coordinates of the search result moves. For each move, 
  the number of games that have made this move is listed, and also the percentage 
  of those games that ended in a win for the player that made the move. You can 
  sort these results by the coordinates, number of games, or percentages 
  by clicking the headings on the table.<br>
  <br>
  The first and second sections 
  are tied together. If you move your mouse over a move on the board, an indicator 
  will light up next to the stats for that move. If you move your mouse over a 
  coordinate in the stats table, a black or white piece will show up on the board 
  for that stat. This makes it easier to view the statistics.<br>
  <br>
  <h3><a name="games">3. Search Result Games</a></h3>
  The third section is a table 
  containing a listing of recent games that match the position searched for. Various 
  statistics are shown in the table. For a complete listing of information about 
  the game, click on the <b>Txt</b> link for the game. To view the game on 
  the board, click the <b>Load</b> link for the game. Then you can navigate 
  through the game just as if you had entered it yourself. If you wish to view 
  more than the default number of games you can use the select box to view up 
  to 25 games at a time. To view more games you can use the <b>&lt;&lt;</b> 
  and <b>&gt;&gt;</b> links at the bottom of the table.<br><br>Below the most recent games in the table
  is a subtable containing links to download games that matched the search in a zip file.  The format
  of the game files in the zip file are the same as if you clicked on the <b>Txt</b> link.
  This is a useful feature if you wish to have a local copy of a set of games, of if you wish to
  use the game files in another program.<br>
  <br>
  <h3><a name="filter">4. Filter Options</a></h3>
  The fourth section is the 
  filtering options table. This form allows you to filter the games that are searched. 
  Currently you can filter games by site, event, round, section, players and date. 
<blockquote> 
  <h4>4.1 Venue filtering</h4>
    You can select a site, 
    event, round, and section from a drop-down list. Initially the board is set 
    to filter "All Sites", "All Events", "All Rounds", "All Sections". Once you 
    select a site to filter, the event drop-down list is updated with all the 
    events that have occurred at the selected site. If you select an event to 
    filter, the round drop-down list is updated with all the rounds for the selected 
    event and finally if you select a round to filter, the section drop-down list 
    is updated with all the sections for the selected round. Some events don't 
    have any rounds (like the event "Non-Tournament"), in this case the only round 
    and section you can select is "-". One other thing to note with this strategy 
    is that it is impossible to filter by eg. "Round = 3" without first selecting 
    a site and an event. So it is currently impossible to filter for all games 
    in "Round 3" played at any site in any event. Perhaps a more sophisticated 
    client will handle this problem differently in a future release.<br>
  <br>
  <h4>4.2 Player filtering</h4>
    You can also filter by 
    the players in a game. If you enter &quot;dweebo&quot; for player 1 and click 
    &quot;Search&quot; you'll only view games in which &quot;dweebo&quot; was 
    player 1. If you also enter &quot;mmammel&quot; for player 2, you'll only 
    view games in which &quot;dweebo&quot; was player 1 and &quot;mmammel&quot; 
    was player 2 and so on.  Also note that names are searched case-insenitively, so a
    search for "DWEEBO" will match on "dweebo".<br>
  <br>
  <h4>4.3 Date filtering</h4>
    You can also filter by 
    the date games were played. This makes it possible to perform a search like 
    this &quot;show all games played by dweebo in the past month&quot;. You can 
    accomplish this search by entering in dates into the &quot;After date&quot; 
    and &quot;Before date&quot; fields in the filter options table. Dates must 
    be entered in this form (MM/DD/YYYY). If you enter in just a before date, 
    you'll be searching for all games from the beginning of time up to the date 
    you enter. If you enter just an after date, you'll be searching for all games 
    from the day after the day you entered until today. If you enter in both a 
    before and after date, you'll be searching for all games between the two dates. 
    For example, say today is 07/21/2001. If you wanted to search for all games 
    played today enter in 07/20/2001 for the &quot;After date&quot;, and 07/22/2001 
    for the &quot;Before date&quot;.<br>
    <br>
  <h4>4.4 Winner filtering</h4>
    Finally, you can filter 
    by winner, that is search for games won by player 1, or search for games won 
    by player 2.<br>
</blockquote>
<br>
<h3>Other notes</h3>
  Other things you might find 
  useful. The positions you search for will also search for games which are mirrored 
  or rotated. Or, if a game starts differently than the game you entered, but 
  ends up with the same position, it will also match. So when you search, keep 
  in mind that you're not looking for games that moved at &quot;K10&quot;, then 
  &quot;L11&quot;...a capture at &quot;M10&quot;... but games that have a white 
  piece at &quot;K10&quot;, a black piece at &quot;L11&quot; and &quot;1&quot; 
  capture by black...<br>
<br>
<b><a href="helpWindow.jsp?file=rankings">&lt;&lt; Player Rankings</a>&nbsp;&nbsp;&nbsp;
<b><a href="helpWindow.jsp?file=forums">&gt;&gt; Forums</a><br>
</font>