<%
   /**
    *	$RCSfile: datasource.jsp,v $
    *	$Revision: 1.2 $
    *	$Date: 2002/11/11 05:37:09 $
    */
%>

<%@ page
   import="java.sql.*,
           com.jivesoftware.forum.*,
           com.jivesoftware.forum.util.*,
           com.jivesoftware.forum.database.*,
           java.text.NumberFormat,
           com.jivesoftware.util.ParamUtils,
           com.jivesoftware.base.database.ConnectionManager" %>

<%@ include file="global.jsp" %>

<% // Permission check
   if (!isSystemAdmin) {
      throw new UnauthorizedException("You don't have admin privileges to perform this operation.");
   }
%>

<%@ include file="header.jsp" %>

<p>

      <%  // Title of this page and breadcrumbs
    String title = "Database Settings";
    String[][] breadcrumbs = {
        {"Main", "main.jsp"},
        {"Database Settings", "datasource.jsp"}
    };
%>
   <%@ include file="title.jsp" %>

   <font size="-1">
      Below is a summary of your database settings. If you need to change them,
      you'll need to edit your jive_config.xml file.
   </font>

<p>

      <%  // Get metadata about the database
	Connection con = ConnectionManager.getConnection();
	DatabaseMetaData metaData = con.getMetaData();
%>

   <font size="-1"><b>Database Properties</b></font>

<ul>

   <table bgcolor="<%= tblBorderColor %>" cellpadding="0" cellspacing="0" border="0" width="300">
      <tr>
         <td>
            <table bgcolor="<%= tblBorderColor %>" cellpadding="3" cellspacing="1" border="0" width="100%">
               <tr bgcolor="#ffffff">
                  <td><font size="-1">Name:</font></td>
                  <td><font size="-1"><%= metaData.getDatabaseProductName() %>
                  </font></td>
               </tr>
               <tr bgcolor="#ffffff">
                  <td><font size="-1">Version:</font></td>
                  <td><font size="-1"><%= metaData.getDatabaseProductVersion() %>
                  </font></td>
               </tr>
            </table>
         </td>
      </tr>
   </table>

</ul>

<font size="-1"><b>JDBC Driver Properties</b></font>

<ul>

   <table bgcolor="<%= tblBorderColor %>" cellpadding="0" cellspacing="0" border="0" width="300">
      <tr>
         <td>
            <table bgcolor="<%= tblBorderColor %>" cellpadding="3" cellspacing="1" border="0" width="100%">
               <tr bgcolor="#ffffff">
                  <td><font size="-1">Driver:</font></td>
                  <td><font size="-1"><%= metaData.getDriverName() %>, version <%= metaData.getDriverVersion() %>
                  </font></td>
               </tr>
               <tr bgcolor="#ffffff">
                  <td><font size="-1">Connection URL:</font></td>
                  <td><font size="-1"><%= metaData.getURL() %>
                  </font></td>
               </tr>
               <tr bgcolor="#ffffff">
                  <td><font size="-1">Connection username:</font></td>
                  <td><font size="-1"><%= metaData.getUserName() %>
                  </font></td>
               </tr>
            </table>
         </td>
      </tr>
   </table>

</ul>

<font size="-1"><b>Database Capabilities</b></font>

<ul>

   <table bgcolor="<%= tblBorderColor %>" cellpadding="0" cellspacing="0" border="0" width="300">
      <tr>
         <td>
            <table bgcolor="<%= tblBorderColor %>" cellpadding="3" cellspacing="1" border="0" width="100%">
               <tr bgcolor="#ffffff">
                  <td><font size="-1">Supports transactions?</font></td>
                  <td><font size="-1"><%= (metaData.supportsTransactions()) ? "Yes" : "No" %>
                  </font></td>
               </tr>
               <% if (metaData.supportsTransactions()) { %>
               <tr bgcolor="#ffffff">
                  <td><font size="-1">Transaction Isolation Level</font></td>
                  <td><font size="-1">
                     <% if (con.getTransactionIsolation() == Connection.TRANSACTION_NONE) { %>
                     TRANSACTION_NONE
                     <% } else if (con.getTransactionIsolation() == Connection.TRANSACTION_READ_COMMITTED) { %>
                     TRANSACTION_READ_COMMITTED
                     <% } else if (con.getTransactionIsolation() == Connection.TRANSACTION_READ_UNCOMMITTED) { %>
                     TRANSACTION_READ_UNCOMMITTED
                     <% } else if (con.getTransactionIsolation() == Connection.TRANSACTION_REPEATABLE_READ) { %>
                     TRANSACTION_REPEATABLE_READ
                     <% } else if (con.getTransactionIsolation() == Connection.TRANSACTION_SERIALIZABLE) { %>
                     TRANSACTION_SERIALIZABLE
                     <% } %>
                  </font></td>
               </tr>
               <% } %>
               <tr bgcolor="#ffffff">
                  <td><font size="-1">Supports multiple connections <br>open at once?</font></td>
                  <td><font size="-1"><%= (metaData.supportsMultipleTransactions()) ? "Yes" : "No" %>
                  </font></td>
               </tr>
               <tr bgcolor="#ffffff">
                  <td><font size="-1">Is in read-only mode?</font></td>
                  <td><font size="-1"><%= (metaData.isReadOnly()) ? "Yes" : "No" %>
                  </font></td>
               </tr>
            </table>
         </td>
      </tr>
   </table>

   <% // Close the connection:
      try {
         con.close();
      } catch (Exception e) {
      }
   %>

</ul>

</body>
</html>


