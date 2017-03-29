<%@ page contentType="text/html; charset=UTF-8" %>

<% pageContext.setAttribute("current", "Features"); %>
<% pageContext.setAttribute("title", "Features"); %>
<%@ include file="top.jsp" %>

<style type="text/css">
body,div,dl,dt,dd,ul,ol,li,h1,h2,h3,h4,h5,
h6,pre,form,fieldset,input,p,blockquote,table,
th,td {margin:0;padding:0;}
fieldset,img,abbr {border:0;}
address,caption,code,dfn,h1,h2,h3,
h4,h5,h6,th,var {font-style:normal;font-weight:normal;}
caption,th {text-align:left;}
q:before,q:after {content:'';}
a {text-decoration:none;}

#features h3 {
    font-size:16px;
    font-weight:bold;
    margin:0;
    line-height:1.4em;
}
#features h3#features-digit {
    font-weight:normal;
    font-size:18px;
    margin-left:12px;
    margin-bottom:25px;
}
#features p {
    margin:0;
    color:#777;
    font-size:13px;
}
#features p.lead-in {
    font-size:18px;
}
#features img {
    z-index:0;
}
#features-livegame,#features-database,#features-tbgame,#features-ai,#features-tutorials,
#features-puzzles,#features-stats, #features-apps, #features-competition {
    border-top:1px solid #dfdfdf;
    padding-top:25px;
}
#features-livegame,#features-ai,#features-database {
    padding-left:0px;
}

#features-tbgame, #features-database {
    width:500px;
    float:left;
    padding-left:22px;
    margin-top:25px;
    padding-bottom:25px;
}
#features-tbgame p, #features-database p {
    padding-right:12px;
}
#features-livegame, #features-stats {
    margin-top:25px;
    padding-bottom:25px;
    border-right:1px solid #dfdfdf;
}
#features-livegame, #features-stats {
    width:350px;
    float:left;
    padding-bottom:25px;
}
#features-livegame p, #features-stats p {
    padding-right:12px;
}

#features-database, #features-stats {
    margin-top: 0px;
}


#features-ai{
    width:250px;
    float:left;
    padding-right:18px;
}

#features-tutorials {
    width:250px;
    float:left;
    padding-left:20px;
    padding-right:20px;
    padding-bottom:25px;
    border-left:1px solid #dfdfdf;
    border-right:1px solid #dfdfdf;
}
#features-puzzles {
    width:250px;
    padding-left:22px;
    padding-right:18px;
    float:left;
}


#features-ai, #features-tutorials, #features-puzzles, #features-apps, #features-competition {
    padding-top: 5px;
}

</style>

<div id="features" class="pagebody">

<h2>Features</h2>
	       
    <div id="features-livegame">
        <h3>Play live games</h3><p>The main point of the site is to be able to play live Pente games with
        anyone in the world!</p>
        <a href="/res/live.png"><img src="/res/live-sm.png"></a>


    </div>
    <div id="features-tbgame">
        <h3>Play turn-based games</h3><p>If you don't have a block of time for 
        playing live games, try turn-based games where you have up to a <b>week</b>
        to make a single move!</p><br>
        <img src="/res/tb.png"></a>

    </div>
    
    <div style="clear:both">
    <div id="features-ai">
        <h3>Computer opponent</h3>
        <p>You can also play against a world-class computer
        opponent in the live game room, it's a great way to improve your skills.
        </p>
    </div>
    <div id="features-apps">
        <h3>Mobile Apps</a></h3>
        <p> Not always in front of your computer? Play from anywhere, any time with out mobile apps for iOS and Android. Our apps support all website features: turn-based play, live play, access the game database, play 2 different computer players.
        <br>
        <br>
        <center>
        <a href="https://itunes.apple.com/us/app/pente-live/id595426592?ls=1&mt=8" target="mobileApp"><img src="gameServer/images/app_store.png"></a>
        <a href="https://play.google.com/store/apps/details?id=be.submanifold.pentelive" target="mobileApp"><img src="gameServer/images/google_play.png"></a>
        </center>

        </p>
    </div>
    <div id="features-competition">
        <h3>Competitions</a></h3>
        <p> We have regular tournaments where you can battle for eternal glory and a crown. And King of the Hill where you fight your way to the top of the hill for a well-earned crown.
        <br>

        </p>
    </div>
    </div>
    
    <div style="clear:both">
    <div id="features-stats">
        <h3>Stats and Rankings</h3><p>We keep track of your ratings over time
        so you can see your improvement.</p><br>
        <a href="/res/stats.png"><img src="/res/stats.png" width="325"></a><br>
        <p>You can also see how you <a href="/gameServer/statsMain.jsp">rank</a> against other players on the site.</p>
        <a href="/gameServer/statsMain.jsp"><img src="/res/rank.png"></a>

    </div>
    <div id="features-database">
        <h3>Game Database</h3><p>Want to really improve your game? Study past
        games by the best players in the world!  With the game database you
        can find games with the same openings in seconds.</p>
        <img src="/res/db-sm.png"><br>

    </div>
    </div>
    <div id="features-tutorials">
        <h3>Tutorials</a></h3>
        <p>Learn basic strategy of the game with our Pente <a href="/gameServer/strategy.jsp">tutorials</a>,
        designed for the beginner player.        
        </p>
    </div>
    <div id="features-puzzles">
        <h3>Puzzles</a></h3>
        <p>Once you've got the hang of the game, try the <a href="/gameServer/puzzles.jsp">puzzles</a> for a good
        challenge!        
        </p>
    </div>
</div>

<%@ include file="bottom.jsp" %>