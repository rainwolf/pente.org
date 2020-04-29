<%@ page contentType="text/html; charset=UTF-8" %>
<%@ page import="java.text.*,
                 java.util.*,
                 org.pente.gameServer.server.*,
                 org.pente.gameServer.client.web.*" %>

<%! private static final String staticTabs[][] = new String[][] {
		{ "/", "Home" },
		{ "/join.jsp", "Join" },
		{ "/features.jsp", "Features" },
		{ "/help/helpWindow.jsp?file=gettingStarted", "Help" },
		{ "/help/helpWindow.jsp?file=faq", "FAQ" },
		{ "/gameServer/forums", "Forums" }
	};
%>
<% String current = (String) pageContext.getAttribute("current"); 
   String style = "style2.css";
   String topTabs[][] = (String[][]) pageContext.getAttribute("tabs");
   if (topTabs == null) {
	   topTabs = staticTabs;
   }
   
   String topTitle = (String) pageContext.getAttribute("title");
%>
<!DOCTYPE HTML PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html dir="ltr" xmlns="http://www.w3.org/1999/xhtml" lang="en"><head>

<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
  <title>Pente.org » <%= topTitle %></title>
  <meta name="description" content="Play Free Online Multiplayer Pente, Gomoku, Keryo-Pente, D-Pente Games With Other Players Around the World, or Play Against a Tough Pente Computer Opponent.">
  <meta name="keywords" content="play free pente game online multiplayer gomoku keryo d-pente g-pente poof-pente dweebo tournaments forums rankings ratings live five in row game database pente rules pente strategy">
<!-- <meta name="viewport" content="width=device-width"> -->

<script type="text/javascript">
<!--
    function sensePage() {
        if (!document.getElementById('aswift_0')) {
            s = '<center><p class="senseText" style="border: 1px solid red; background: #cf9; padding: 1em; margin: 0; text-align:left; font-style:italic;">'+
            'Adblocker activated? Please note that <b>pente.org</b> balances its '+
            'expenses with ads. Perhaps you can consider making an exception for this site <b>or</b> <a href="/gameServer/subscriptions">subscribe</a> instead?'+ '</p>'+ '</center>';  
            document.getElementById('senseReplace').innerHTML = s;
            document.getElementById('bannerAd').setAttribute("style","width:1px; height:1px;");
            document.getElementById('senseReplace').setAttribute("style","width:728px;");
        } else {
            document.getElementById('senseReplace').setAttribute("style","width:1px; height:1px;");
            document.getElementById('bannerAd').setAttribute("style","width:728px; height:90px;");
        }
    }
//-->
</script>

<script type="text/javascript">
/* <![CDATA[ */
function addLoadEvent(func) {
	var oldonload = window.onload;
	if (typeof window.onload != 'function') {
		window.onload = func;
	} else {
		window.onload = function() { oldonload(); func(); }
	}
}
/* ]]> */
</script>

<link href="/res/<%= style %>" media="all" rel="Stylesheet" type="text/css">
<link href="/res/hack.css" media="all" rel="Stylesheet" type="text/css">


    <link rel="stylesheet" type="text/css" href="//cdnjs.cloudflare.com/ajax/libs/cookieconsent2/3.0.3/cookieconsent.min.css" />
    <script src="//cdnjs.cloudflare.com/ajax/libs/cookieconsent2/3.0.3/cookieconsent.min.js"></script>
    <script>
        window.addEventListener("load", function(){
            window.cookieconsent.initialise({
                "palette": {
                    "popup": {
                        "background": "#000"
                    },
                    "button": {
                        "background": "#f1d600"
                    }
                },
                "theme": "classic",
                "content": {
                    "message": "Pente.org uses cookies to ensure you don't have to log in to every page. In addition, Google uses cookies for anonymized advertising (for free users). \n Kindly do not proceed if you do not consent to their use. ",
                    "href": "/help/helpWindow.jsp?file=privacyPolicy"
                }
            })});
    </script>

</head>
<body>
<div id="wrapper">
<div id="header">

	<h1><a href="/index.jsp">Pente.org</a></h1>

	<form method="get" action="https://www.google.com/custom" target="_top" id="search">
		<input type="hidden" name="domains" value="pente.org"></input>
		<input type="hidden" name="sitesearch" value="pente.org"></input>
		<input type="hidden" name="client" value="pub-3840122611088382"></input>
		<input type="hidden" name="forid" value="1"></input>
		<input type="hidden" name="ie" value="ISO-8859-1"></input>
		<input type="hidden" name="oe" value="ISO-8859-1"></input>
		<input type="hidden" name="cof" value="GALT:#008000;GL:1;DIV:#336699;VLC:663399;AH:center;BGC:FFFFFF;LBGC:336699;ALC:0000FF;LC:0000FF;T:000000;GFNT:0000FF;GIMP:0000FF;LH:50;LW:150;L:http://www.pente.org/gameServer/images/logo.gif;S:http://;LP:1;FORID:1;"></input>
		<input type="hidden" name="hl" value="en"></input>
		<input type="text" name="q" size="15" maxlength="255" style="font-size:8pt;" value="Search pente.org"
		  onfocus="this.value=(this.value=='Search pente.org') ? '' : this.value;" onblur="this.value=(this.value=='') ? 'Search pente.org' : this.value;"></input>
		<input type="submit" name="sa" value="Go"></input>
	</form>

	<ul id="topnav">
<% for (int i = topTabs.length - 1; i >= 0; i--) { 
    String[] tab = topTabs[i]; %>
	<li><a<%= (tab[1].equals(current) ? " class=\"current\"" : "") %> href="<%= tab[0] %>"><%= tab[1] %></a></li>
<% } %>
	</ul>
</div>
