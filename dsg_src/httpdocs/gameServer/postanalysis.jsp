<%@ page import="java.util.*" %>

<% pageContext.setAttribute("title", "Post Analysis Games in the Forums"); %>
<%@ include file="begin.jsp" %>


<h3>How To Post Analysis Games in the Forums</h3>

<p>You may have noticed game images or game boards within a forum thread, this
page describes how you can post your own games or positions for discussion in
the <a href="/gameServer/forums">pente.org forums</a>.

There are a few different ways right now to post a message in the forums with a
game board.

<ol>
 <li>Post an image of the final move in the position</li>
 <li>Post an applet that can display the sequence of moves in a game</li>
</ol>

<h4>Post an image of the final move in the position</h4>

<div>
 
 <div style="width:700px;margin-top:10px;padding:10px;padding-left:20px;"> 
	 <div style="float:left;width:300px;padding-right:50px;">
	   Here is a simple example you could type into a forum post.<br>
	   <br>
	   <p style="font-family:courier">
            [board]g=Pente m=K10,L11[/board]
       </p>
       <br>
	   In this case the text <b>g=Pente</b> specifies that we want to display
	   a Pente game, and the text <b>m=K10,L11</b> specifies the coordinates of each move in the 
	   game in order.<br>
	   <br>If the game caused any captures to occur they will be displayed
	   correctly.
	 </div>
	 
	 <div style="float:left;width:300px;">

		<img src=/gameServer/board?g=Pente&m=K10,L11&w=280&h=260>
	 </div>
     <div style="clear:both"></div>
 </div>
 
 <div style="float:left;width:650px;clear:both;border-top:1px solid gray;margin-top:10px;padding-top:10px;padding-bottom:10px;">
You might want to setup a position on the board that is not from a game, 
but instead highlights a particular position for analysis purposes only.
That is hard to do if you have to enter in alternating moves for player 1 and
player 2 since you might want 10 white moves and only 3 black moves. <br>
 </div>
 
  <div style="width:700px;margin-top:10px;padding:10px;padding-left:20px;"> 
     <div style="float:left;width:300px;padding-right:50px;">
       <p style="font-family:courier">
         [board]g=Pente bm=K10,K12,K14 wm=K11 bc=2 wc=4[/board]
       </p>
       <br>
       In this case the text <b>bm=K10,K12,K14</b> specifies the coordinates of each
       black stone on the board, and the text <b>wm=K11,K13</b> specifies the coordinates of
       each white stone.<br>
       <br>
       The text <b>bc=2</b> tells the board to show two black stones captured and
       the text <b>wc=4</b> tells the board to show four white stones captured.
     </div>
     
     <div style="float:left;width:300px;">
        <img src=/gameServer/board?g=Pente&bm=K10,K13,K15&wm=K11&bc=2&wc=4&w=280&h=260>
     </div>
     <div style="clear:both"></div>
 </div>
 
 <div style="float:left;width:650px;clear:both;border-top:1px solid gray;margin-top:10px;padding-top:10px;padding-bottom:10px;">
If you want to display a game that was either played here at pente.org or is in
the <a href="/gameServer/controller/search?quick_start=1">pente game database</a> you
can just specify the unique game ID.
 </div>
 
   <div style="width:700px;margin-top:10px;padding:10px;padding-left:20px;"> 
     <div style="float:left;width:300px;padding-right:50px;">
       <p style="font-family:courier">
         [board]gid=34194139852955[/board]
       </p>
       In this case the text <b>gid=34194139852955</b> specifies the game ID and
       that is all you need!<br>
       <br>
       To find the game ID for a game in a player's completed games look at the URL
       in your browser when viewing the game, it should look like this<br>
       http://pente.org/gameServer/viewLiveGame?g=<font color="red">34194139852955</font>
       <br>
       <br>
       To find the game ID for a game in the game database click the "TXT" link next 
       to the game in the game results list, that will open up a page with a URL like this<br>
       http://pente.org/gameServer/pgn.jsp?g=<font color="red">34194139852955</font>
       <br><br>
       Not intuitive I realize but that works until something better is implemented.
       
     </div>
     
     <div style="float:left;width:300px;">
        <img src=/gameServer/board?gid=34194139852955&w=280&h=260>
     </div>
     <div style="clear:both"></div>
 </div>
 
 <div style="float:left;width:650px;clear:both;border-top:1px solid gray;margin-top:10px;padding-top:10px;padding-bottom:10px;">
If if you want to make the board appear larger or smaller you can control the width
and height in pixels by specifying additional parameters like this.
       <p style="font-family:courier">
         [board]gid=34194139852955 <font color="red">w=500 h=500</font>[/board]
       </p>
 </div>
</div>

<h4 style="clear:both;border-top:1px solid gray;padding-top:20px;">Post an applet that can display the sequence of moves in a game</h4>

<div style="width:700px;margin-top:10px;padding:10px;padding-top:0px;padding-left:20px;"> 
     <div style="float:left;width:700px;padding-right:50px;">

       <p style="font-family:courier">
            [game]g=Pente m=K10,L11 wn=peter bn=dweebo w=480 h=260[/game]
       </p>
       <br>
       In this case the text <b>g=Pente</b> specifies that we want to display
       a Pente game, and the text <b>m=K10,L11</b> specifies the coordinates of each move in the 
       game in order.<br>
       <br>Optionally you can specify the white player's name with <b>wn=peter</b>
       and the black player's name with <b>bn=dweebo</b><br>
       <br>Optionally you can specify a width and height in pixels with <b>w=480</b> for the width
       and <b>h=260</b> for the height.<br>
       <br>If the game caused any captures to occur they will be displayed
       correctly.
     </div>
     
     <div style="float:left;width:770px;">
        <iframe src="/gameServer/viewGameEmbed.jsp?g=Pente&m=K10,L11&bn=dweebo&wn=peter&w=700&h=260" width=770 height=600 frameborder=0 marginheight=0 marginwidth=0 scrolling=no></iframe>
     </div>
     <div style="clear:both"></div>
 </div>
 
<div style="float:left;width:800px;clear:both;border-top:1px solid gray;margin-top:10px;padding-top:10px;padding-bottom:10px;">
If you want to display a game that was either played here at pente.org or is in
the <a href="/gameServer/controller/search?quick_start=1">pente game database</a> you
can just specify the unique game ID.
 </div>
 
   <div style="width:770px;margin-top:10px;padding:10px;padding-left:20px;"> 
     <div style="float:left;width:700px;padding-right:50px;">
       <p style="font-family:courier">
         [game]34194139852955[/game]
       </p>
       In this case the text <b>34194139852955</b> specifies the game ID and
       that is all you need!<br>
       <br>
       To find the game ID for a game in a player's completed games look at the URL
       in your browser when viewing the game, it should look like this<br>
       http://pente.org/gameServer/viewLiveGame?g=<font color="red">34194139852955</font>
       <br>
       <br>
       To find the game ID for a game in the game database click the "TXT" link next 
       to the game in the game results list, that will open up a page with a URL like this<br>
       http://pente.org/gameServer/pgn.jsp?g=<font color="red">34194139852955</font>
       <br><br>
       Not intuitive I realize but that works until something better is implemented.
       
     </div>
   </div>
   <div style="width:770px;margin-top:10px;padding:10px;padding-left:20px;"> 
     <div style="float:left;width:770px;">
        <iframe src="/gameServer/viewLiveGame?g=34194139852955&e=1&w=770&h=360" width=770 height=600 frameborder=0 marginheight=0 marginwidth=0 scrolling=no></iframe>
     </div>
   </div>

<div style="float:left;width:800px;clear:both;border-top:1px solid gray;margin-top:10px;padding-top:10px;padding-bottom:10px;">
Eventually I would like to provide an easier way for users to create the codes
above with an applet that lets you enter in moves on a board.<br>
<br>
<br>
 </div>
</div>
<%@ include file="end.jsp" %>