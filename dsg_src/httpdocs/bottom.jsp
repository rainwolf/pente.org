
<%! private static int year; 
static {
   Calendar now = Calendar.getInstance();
   year = now.get(Calendar.YEAR);
}
%>
	<div id="footer">
		<div id="footer-left">
			Copyright &copy; 1999-<%= year %> Pente.org. All rights reserved. |
			<a href="/help/helpWindow.jsp?file=privacyPolicy">Privacy</a> |
			<a href="/help/helpWindow.jsp?file=ratedPolicy"> Terms of Service</a> |
			<a href="/gameServer/links.jsp">Links</a> |
            <a href="/gameServer/about.jsp">About</a><br>
			Created by: <a href="http://www.hewittsoft.com/">Hewitt Software Development</a>
		</div>
		<div id="footer-right">
		</div>
	</div>
</div>

<script>
  (function(i,s,o,g,r,a,m){i['GoogleAnalyticsObject']=r;i[r]=i[r]||function(){
  (i[r].q=i[r].q||[]).push(arguments)},i[r].l=1*new Date();a=s.createElement(o),
  m=s.getElementsByTagName(o)[0];a.async=1;a.src=g;m.parentNode.insertBefore(a,m)
  })(window,document,'script','//www.google-analytics.com/analytics.js','ga');

  ga('create', 'UA-20529582-2', 'pente.org');
  ga('send', 'pageview');

</script>

</body>
</html>
