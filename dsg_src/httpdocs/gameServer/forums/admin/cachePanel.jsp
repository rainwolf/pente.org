<%
   /**
    *    $RCSfile: cachePanel.jsp,v $
    *    $Revision: 1.1 $
    *    $Date: 2002/08/16 06:52:22 $
    */
%>

<%@ page import="java.util.*,
                 java.text.*,
                 com.jivesoftware.util.*,
                 com.jivesoftware.forum.*,
                 com.jivesoftware.forum.database.*,
                 com.jivesoftware.forum.util.*,
                 com.tangosol.net.*"
%>

<html>
<head><title></title></head>
<body bgcolor="#999999">

<table cellpadding="2" cellspacing="0" border="0" height="100%">
   <tr>
      <% int numClusters = 12;
         for (int i = 0; i < numClusters; i++) { %>
      <% if (i == 0) { %>
      <td align="center"><img src="images/node2on.gif" width="40" height="39" border="0" hspace="6"></td>
      <% } else { %>
      <td align="center"><img src="images/node2.gif" width="40" height="39" border="0" hspace="6"></td>
      <% } %>
      <% if ((i + 1) < numClusters) { %>
      <td>
         <table bgcolor="#cccccc" cellpadding="0" cellspacing="0">
            <td><img src="images/blank.gif" width="15" height="2" border="0"></td>
         </table>
      </td>
      <% } %>
      <% } %>
   </tr>
   <tr>
      <% for (int i = 0; i < numClusters; i++) { %>
      <td align="center">
         <font size="-2" face="verdana" color="#ffffff">
            <% if (i == 0) { %>
            <b>Cluster <%= (i + 1) %>
            </b>
            <% } else { %>
            Cluster <%= (i + 1) %>
            <% } %>
         </font>
      </td>
      <% if ((i + 1) < numClusters) { %>
      <td><img src="images/blank.gif" width="15" height="1" border="0"></td>
      <% } %>
      <% } %>
   </tr>
</table>

</body>
</html>
