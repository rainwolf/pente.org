<%! private static int counter = 0; %>

<% counter++;
   if (counter % 4 == 0) { %>
<img src="http://www.myaffiliateprogram.com/u/wmoves/showban.asp?id=1005&img=pente1_468x60.jpg" border=0>
<A target="_blank" href="http://www.winning-moves.com/products/pente.htm?kbid=1005&img=pente1_468x60.jpg">
   <img src="http://www.winning-moves.com/affiliates/pente1_468x60.jpg" border=0></a>
<% } else if (counter % 4 == 1) { %>
<img src="http://www.myaffiliateprogram.com/u/wmoves/showban.asp?id=1005&img=mixbanner2.jpg" border=0>
<A target="_blank" href="http://www.winning-moves.com/products?kbid=1005&img=mixbanner2.jpg">
   <img src="http://www.winning-moves.com/affiliates/mixbanner2.jpg" border=0></a>
<% } else if (counter % 4 == 2) { %>
<img src="http://www.myaffiliateprogram.com/u/wmoves/showban.asp?id=1005&img=rubiks_cube_120x60.jpg" border=0>
<A href="http://www.winning-moves.com/products/rubiks?kbid=1005&img=rubiks_cube_120x60.jpg">
   <img src="http://www.winning-moves.com/affiliates/rubiks_cube_120x60.jpg" border=0></a>
&nbsp;&nbsp;&nbsp;
<A target="_blank" href="http://www.winning-moves.com/products/rubiks?kbid=1005&img=rings_special_234x60.gif">
   <img src="http://www.winning-moves.com/affiliates/rings_special_234x60.gif" border=0></a>
<img src="http://www.myaffiliateprogram.com/u/wmoves/showban.asp?id=1005&img=rings_special_234x60.gif" border=0>
<% } else { %>
<%@ include file="amazonHeaderPlay.jsp" %>
<% } %>
