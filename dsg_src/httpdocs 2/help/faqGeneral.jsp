<font face="Verdana, Arial, Helvetica, sans-serif" size="2">
<h2>Frequently Asked Questions - General</h2>

If you are having trouble getting the game room applet to work, please refer
to the <a href="helpWindow.jsp?file=faqAppletTroubleShooting">Applet TroubleShooting FAQ</a><br>
<br>
<ul>
  <li><a href="#buy"><b>Where can I buy Pente?</b></a></li>
  <li><a href="#ratings"><b>How are the ratings calculated?</b></a></li>
  <li><a href="#provisional"><b>What does provisional mean?</b></a></li>
  <li><a href="#ratingsdrop"><b>I won but my ratings went down!?!?</b></a></li>
  <li><a href="#busiest"><b>When in the day is the server busiest?</b></a></li>
  <li><a href="#namecolor"><b>How do I change the color of my name in the game room?</b></a></li>
  <li><a href="#picture"><b>How do I add a picture to my profile?</b></a></li>
</ul>
<br>
<a name="buy"><u>Where can I buy Pente?</u></a><br>
Pente is now being manufactured again by a company called Winning Moves.
You can purchase the game through their web site, click the Pente board
on the main page at Pente.org to order!<br>
<br>
<a name="ratings"><u>How are the ratings calculated?</u></a><br>
Ratings are calculated with two different formulas, one for provisional
players, and one for established players. Here is the formula used
for established players.
<pre><font face="Verdana, Arial, Helvetica, sans-serif" size="2"><strong>                                                     1

     r1 + K * ( w - ( ------------------------ ) )

                                 1 + 10 ^ ((r2-r1)/400))
</font></strong></pre>
Where <strong>r1</strong> is your rating, and <strong>r2</strong> is
your opponents rating.<br>
<strong>w</strong> is 1 for a win, and 0 for a loss.<br>
<strong>K </strong>is the largest amount your rating can change
for any game, this value is set to 32 when 2 established players
are playing. When playing against a provisional player, <strong>K</strong>
is scaled by <strong>n</strong> / 20, where <strong>n</strong> is
equal to the number of games the provisional player has played.<br>
The '^' symbol means to the power of.</p>
<p>For provisional players, ratings can jump about dramatically. Every
game a provisional player plays first has a value calculated.
This value is equal
to</p>
<pre>
    <strong><font face="Verdana, Arial, Helvetica, sans-serif" size="2">
      value = ( r1 + r2 ) / 2 + w * 200 + e * 200</font></strong>
</pre>
Where <strong>r1</strong> and <strong>r2</strong> are the same as defined above.<br>
<strong>w</strong> is 1 for a win and -1 for a loss.<br>
<strong>e</strong> is 0 if your opponent is established, otherwise it equals w.<br>
Then that value is incorporated into your new provisional rating by the following formula.<br>
<pre>
    <strong><font face="Verdana, Arial, Helvetica, sans-serif" size="2">
      rating = (value + (rating * total1)) / total2
    </font></strong>
</pre>
Where <b>total1</b> is the total games played <b>excluding</b> this game and
<b>total2</b> is the total games played <b>including</b> this game.
<br>
<a name="provisional"><u>What does provisional mean?</u></a><br>
When a player first starts playing, the player is provisional until
he/she has completed 20 games. The purpose of having a provisional
status is for ratings. Ratings can fluctuate wildly while
provisional so a players expected 'average' rating can be quickly
determined. It also protects established players ratings from
varying too much when playing provisional opponents.<br>
<br>
<a name="ratingsdrop"><u>I won but my ratings went down!?!?</u></a><br>
There are a few times when as a provisional player you can win the game and
have your rating DECREASE (or lose a game and have your rating INCREASE).  This
is a strange feature of the ratings formula that only occurs when you are a
provisional player and you play your 1st game or you play an opponent whose
ratings is much different than yours.  Because this only
happens while you're provisional you shouldn't be too worried about it.  If
the formula wasn't setup this way a player could become established with a
very high rating by playing 20 games against players that are much weaker.<br>
<br>
<a name="busiest"><u>When in the day is the server busiest?</u></a><br>
The busiest time of day is usually after people get off work, so from
6-12 EST there is a good chance of playing a few games. But if you
come on and there is no one in the room, don't leave right away.
Stay for a few minutes, someone else will probably show up.
Lunchtime is probably the second best time to play.&nbsp; A good
thing to do if no one is on is to check the <b>Audio Alert</b>
checkbox, and then go do something else on the web.&nbsp; When
someone joins the server you will here a sound.<br>
<br>
<a name="namecolor"><u>How do I change the color of my name in the game room?</u></a><br>
Changing the color of your name is a special feature only available
to players who make a donation to Pente.org. Once you make a donation you can change
the color of your name through My Profile.<br>
<br>
<a name="picture"><u>How do I add a picture to my profile?</u></a><br>
Uploading a picture to your profile is also a special feature only available
to players who make a donation to Pente.org. Once you make a donation you can upload
a picture through My Profile.<br>
<br>
</font>