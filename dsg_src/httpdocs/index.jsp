<%@ page contentType="text/html; charset=UTF-8" %>

<%! private static final String lTabs[][] = new String[][] {
       		{ "/", "Home" },
       		{ "/gameServer/index.jsp", "Dashboard" },
		{ "/join.jsp", "Join" },
                { "/features.jsp", "Features" },
                { "/help/helpWindow.jsp?file=gettingStarted", "Help" },
                { "/help/helpWindow.jsp?file=faq", "FAQ" },
                { "/gameServer/forums", "Forums" }
   };
%>

<% if (request.getAttribute("name") != null && request.getAttribute("spider") == null && request.getParameter("s") == null) {
	response.sendRedirect("/gameServer/index.jsp");
   }
   else if (request.getAttribute("name") != null) {
       pageContext.setAttribute("tabs", lTabs);
   }
   %>

<% pageContext.setAttribute("current", "Home"); %>
<% pageContext.setAttribute("title", "Play Pente Here"); %>
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
</style>

<div class="pagebody">
<div style="float:left;width:160px;height:600px;margin-right:10px;">

<%--<script type="text/javascript"--%>
<%--src="http://pagead2.googlesyndication.com/pagead/show_ads.js">--%>
<%--</script>--%>

<%--<script type="text/javascript"><!----%>
<%--google_ad_client = "ca-pub-3326997956703582";--%>
<%--/* penteORG1 */--%>
<%--google_ad_slot = "5736319846";--%>
<%--google_ad_width = 160;--%>
<%--google_ad_height = 600;--%>
<%--//-->--%>
<%--</script>--%>
<%--<script type="text/javascript"--%>
<%--src="http://pagead2.googlesyndication.com/pagead/show_ads.js">--%>
<%--</script>--%>

<%--<script>--%>
<%--  (function(i,s,o,g,r,a,m){i['GoogleAnalyticsObject']=r;i[r]=i[r]||function(){--%>
<%--  (i[r].q=i[r].q||[]).push(arguments)},i[r].l=1*new Date();a=s.createElement(o),--%>
<%--  m=s.getElementsByTagName(o)[0];a.async=1;a.src=g;m.parentNode.insertBefore(a,m)--%>
<%--  })(window,document,'script','//www.google-analytics.com/analytics.js','ga');--%>

<%--  ga('create', 'UA-20529582-2', 'pente.org');--%>
<%--  ga('set', 'anonymizeIp', true);--%>
<%--  ga('send', 'pageview');--%>

<%--</script>--%>

</div>
<div style="float:left;width:760px">

	<div id="signupnow">

		<div id="signupnow-text">
			<h2>Play Pente, Improve your Game</h2>
			<h4><a href="features.jsp">See our features →</a></h4>
		</div>

		<div id="signupnow-button">
			<a href="join.jsp">Start Playing!</a>
		</div>

	</div>

    <%@ include file="statsbox.jsp" %>

	<div id="text">
	  <img src="/res/board.png" style="float:left; margin: 0 10px 0 0; display: inline;">
	  Pente.org is a place to play Pente for <b>free</b>. You can play and chat <b>live</b> with other players, or
      play against a powerful computer opponent, learn strategy, play in tournaments and more.  See our <b><a href="/features.jsp">features</a></b>.<br>
      <br>
	  Pente is a fun board game that is easy to learn.  Anyone can figure it out and play a game in five minutes at pente.org.<br>
	  <br style="clear:both"><br>
	  Pente is also a complex game that top players are still learning to master after many years of play!<br>
	  <br>
	  
	  <a href="join.jsp">Start Playing Pente</a><br>
	  <br>
    </div>

	<div id="right">
    <%@ include file="loginbox.jsp" %>
    <br>
         <div style="padding:10px;border: 1px solid gray">
	   <b style="font-size:14px;color:red">New!</b>
	   <p style="font-size:14px">Play Pente from your mobile phone! <br/><br/><a href="https://itunes.apple.com/us/app/pente-live/id595426592?mt=8">Learn how</a>.</p>
	 </div>
	</div>

<script type="text/javascript">addLoadEvent(function(){var a=document.getElementById('name2');if(a){a.focus();}else{alert('no');}});</script>

 <br style="clear:both">
 </div>
  <br style="clear:both">

  </div>

<%@ include file="bottom.jsp" %>
