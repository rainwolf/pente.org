<%
   int numThreads = Thread.activeCount() + 5;
   Thread threads[] = new Thread[numThreads];
   numThreads = Thread.enumerate(threads);
%>

<html>
<body>
<table border="1" cellspacing="2" cellpadding="2">
   <tr>
      <td>Name</td>
      <td>Priority</td>
   </tr>

   <% for (int i = 0; i < numThreads; i++) { %>
   <tr>
      <td><%= threads[i].getName() %>
      </td>
      <td><%= threads[i].getPriority() %>
      </td>
   </tr>

   <% } %>

</table>
</body>
</html>