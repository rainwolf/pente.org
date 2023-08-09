<%@ page import="java.util.*, java.io.*" isErrorPage="true" %>

<html>
<head>
   <title>Pente.org Error</title>
</head>
<body>

<pre><font face="Courier" size="2" color="red">
   Stack trace:
   <% if (exception != null) {
      exception.printStackTrace(new PrintWriter(out));
   }
      if (exception.getCause() != null) {
         out.write("<br><br>\n");
         exception.getCause().printStackTrace(new PrintWriter(out));
      } %>
</font></pre>

</body>
</html>