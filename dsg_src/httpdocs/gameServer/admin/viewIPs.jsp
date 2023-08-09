<%@ page import="org.pente.admin.*,
                 org.pente.gameServer.server.*,
                 org.pente.database.*,
                 org.apache.log4j.*,
                 java.io.*,
                 java.text.*,
                 java.util.*,
                 java.sql.*" %>

<%! private static Category log4j =
   Category.getInstance("org.pente.gameServer.web.client.jsp"); %>

<%! private static final DateFormat df = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss"); %>

<% Resources resources = (Resources) application.getAttribute(
   Resources.class.getName());
   DBHandler dbHandler = resources.getDbHandler();

   String name = request.getParameter("name");
   String ip = request.getParameter("ip");
   String error = null;
   if ((name == null || name.equals("")) &&
      (ip == null || ip.equals(""))) {
      error = "Please specify name or IP to search on.";
   }
   boolean vn = name != null && !name.equals("");

   class Data implements Comparable {
      String text;
      java.util.Date access;

      public Data(String t, java.util.Date a) {
         text = t;
         access = a;
      }

      public int compareTo(Object o) {
         return ((Data) o).text.compareTo(text);
      }

      public boolean equals(Object o) {
         return ((Data) o).text.equals(text);
      }

      public int hashCode() {
         return text.hashCode();
      }
   }
   List<Data> allData = new ArrayList<Data>();
   Set<Data> uniqData = new HashSet<Data>();
   List<Data> uniqDataSorted = null;

   Connection con = null;
   PreparedStatement stmt = null;
   ResultSet result = null;
   try {
      con = dbHandler.getConnection();
      if (name != null && !name.equals("")) {
         stmt = con.prepareStatement("select i.ip, i.access_time " +
            "from dsg_ip i, player p " +
            "where i.pid = p.pid " +
            "and p.name = ? " +
            "order by i.ip, i.access_time desc");
         stmt.setString(1, name);
         result = stmt.executeQuery();
         while (result.next()) {
            Data d = new Data(result.getString(1),
               new java.util.Date(result.getTimestamp(2).getTime()));
            allData.add(d);
            uniqData.add(d);
         }
      } else if (ip != null && !ip.equals("")) {
         stmt = con.prepareStatement("select p.name, i.access_time " +
            "from dsg_ip i, player p " +
            "where i.pid = p.pid " +
            "and i.ip = ? " +
            "order by p.name, i.access_time desc");
         stmt.setString(1, ip);
         result = stmt.executeQuery();
         while (result.next()) {
            Data d = new Data(result.getString(1),
               new java.util.Date(result.getTimestamp(2).getTime()));
            allData.add(d);
            uniqData.add(d);
         }
      }

      uniqDataSorted = new ArrayList<Data>(uniqData);
      Collections.sort(uniqDataSorted, new Comparator() {
         public int compare(Object o1, Object o2) {
            return -((Data) o1).access.compareTo(((Data) o2).access);
         }
      });


   } catch (Throwable t) {
      log4j.error("Error viewing IPs", t);
   } finally {
      if (con != null) dbHandler.freeConnection(con);
   }

%>
<html>
<head>
   <title>View IPs</title>
</head>
<body>
<%
   if (error != null) { %>

<b><font color="red"><%= error %>
</font></b><br>
<a href="checkIPs.jsp">Try again</a>.
<% } else { %>

<h3>Viewing <%= vn ? "IPs" : "Names" %> for <%= vn ? name : ip %>
</h3>
<table width="700">
   <tr>
      <td>Keep in mind that IPs change frequently for some players, and some IPs
         will show as shared by a bunch of players. It DOES NOT prove that the
         players are actually the same person. Considering the access time will
         help sometimes in seeing if there is cheating.
      </td>
   </tr>
</table>
<br>

<b>Uniq results</b> (sorted by last access)<br>
<table>
   <tr>
      <th><% if (vn) { %>IP<% } else { %>Name<% } %></th>
      <th>Last Access Time</th>
      <% if (!vn) { %>
      <th>View Profile</th>
      <% } %>
   </tr>

   <% for (Data d : uniqDataSorted) { %>
   <tr>
      <td>
         <% if (vn) { %>
         <a href="viewIPs.jsp?ip=<%= d.text %>"><%= d.text %>
         </a><%
      } else { %>
         <a href="viewIPs.jsp?name=<%= d.text %>"><%= d.text %>
         </a>
         <% } %>
      </td>
      <td><%= df.format(d.access) %>
      </td>
      <% if (!vn) { %>
      <td><a href="/gameServer/profile?viewName=<%= d.text %>">Profile</a></td>
      <% } %>
   </tr>
   <% } %>
</table>

<br><br>
<b>All results</b> (sorted by <%= vn ? "IP" : "Name" %>)<br>
<table>
   <tr>
      <th><% if (vn) { %>IP<% } else { %>Name<% } %></th>
      <th>Access time</th>
      <% if (!vn) { %>
      <th>View Profile</th>
      <% } %>
   </tr>

   <% for (Data d : allData) { %>
   <tr>
      <td>
         <% if (vn) { %>
         <a href="viewIPs.jsp?ip=<%= d.text %>"><%= d.text %>
         </a><%
      } else { %>
         <a href="viewIPs.jsp?name=<%= d.text %>"><%= d.text %>
         </a>
         <% } %>
      </td>
      <td><%= df.format(d.access) %>
      </td>
      <% if (!vn) { %>
      <td><a href="/gameServer/profile?viewName=<%= d.text %>">Profile</a></td>
      <% } %>
   </tr>
   <% } %>
</table>
<% } %>
</body>
</html>