<%@ page import="java.util.*" %>
<%@ page import="java.text.*" %>
<%@ page import="org.pente.gameServer.core.*" %>

<% pageContext.setAttribute("title", "Puzzles"); %>

<%@ include file="begin.jsp" %>

<table width="100%">
<tr>
<td>

<h3>Pente (and variants) puzzles!</h3>

Many thanks to these briliant puzzle writers!  And thanks also to 
<a href="/gameServer/profile?viewName=mmammel">mmammel</a> for digging out 
puzzles from old USPA newsletters and to <a href="/gameServer/profile?viewName=sjustice">sjustice</a> for offering to put his puzzles up here.
Many more to come!<br>
<br>

<a href="#watsu">Watsu and up2ng Poof-Pente puzzle</a><br>
<a href="#ninthlife">Ninthlife by Scott Justice</a><br>
<a href="#oneway">One Way by Rolie Tesh (from old USPA newsletter)</a><br>
<a href="#crossbow">Crossbow by Tom Braunlich (from old USPA newsletter)</a><br>

<br><br>

<a name="watsu"></a>
<font size="4">Watsu and up2ng's Poof-Pente puzzle</font><br>
Click the button below to launch the puzzle.  If you think you have a solution, send the solution
to watsu to see, (use email player through Pente.org).<br>
<br>
In this puzzle it is blacks turn to move.  You must show a solution where
white plays optimally (prolongs the game as much as possible), and black
wins in the fewest number of moves (which watsu claims would be on move 64)!
<br>

</td>
</tr>
</table>

<applet name="puzzle"
   	    codebase="lib/"
   	    code="org.pente.gameServer.client.puzzle.PuzzleApplet.class"
   	    archive="puzzle.jar"
   	    width="200" 
   	    height="50">

</applet>

<br><br>

<a name="ninthlife"></a><font size="4">Ninthlife by Scott Justice</font><br>
Copied with permission from Scott.  This is one "he" considers easy.  White to move and win in 5 moves (that is 5 white moves).<br>
<img src="/gameServer/board?g=Pente&wm=D6,F4,G11,F12,K10&bm=E7,H6,L7,L11&wc=0&bc=8&w=550&h=500">

<br><br>

<a name="oneway"></a><font size="4">One Way by Rollie Tesh</font><br>
White to move and win in 4 moves (that is 4 white moves).<br>
<img src="/gameServer/board?g=Pente&wm=K6,M8,N8,N11,O10,P8,R7,P6,O5,N4&bm=L6,M6,N6,N7,N3,Q7,Q8&wc=6&bc=6&w=550&h=500">

<br><br>

<a name="crossbow"></a><font size="4">Crossbow by Tom Braunlich</font><br>
White to move and win in 5 moves (that is 5 white moves).  There are two different solutions to this one.<br>
<img src="/gameServer/board?g=Pente&wm=E9,F9,K7,L9,L5,M6,O9&bm=D9,F7,G8,J8,J9,K4,K9,K12,M8,M10&wc=0&bc=6&w=550&h=500">

<br><br>

<%@ include file="end.jsp" %>

