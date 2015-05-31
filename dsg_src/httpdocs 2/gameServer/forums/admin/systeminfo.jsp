<%
/**
 *	$RCSfile: systeminfo.jsp,v $
 *	$Revision: 1.2 $
 *	$Date: 2003/01/08 02:31:36 $
 */
%>

<%@ page import="java.util.*,
                 com.jivesoftware.forum.*,
                 com.jivesoftware.forum.util.*,
                 java.sql.DatabaseMetaData,
                 java.sql.Connection,
                 com.jivesoftware.base.database.ConnectionManager" %>

<%@ include file="global.jsp" %>

<%@ include file="header.jsp" %>

<%  // Title of this page and breadcrumbs
    String title = "System Info";
    String[][] breadcrumbs = null;
%>
<%@ include file="title.jsp" %>

Below is a summary of the environment Jive Forums is installed on. If you use the online support
forums at jivesoftware.com, be sure to include this information when posting questions.

<br><br>

<span class="jive-table">
<table cellpadding="3" cellspacing="2" border="0">
<tr>
    <td class="jive-label">
        Jive Forums Edition
    </td>
    <td>
        <%= Version.getEdition().getName() %>
    </td>
</tr>
<tr>
    <td class="jive-label">
        Jive Forums Version
    </td>
    <td>
        <%= Version.getVersionNumber() %>
    </td>
</tr>
<tr>
    <td class="jive-label">
        JVM Version and Vendor
    </td>
    <td>
        <%= System.getProperty("java.version") %> <%= System.getProperty("java.vendor") %>
    </td>
</tr>
<tr>
    <td class="jive-label">
        Application server
    </td>
    <td>
        <%= application.getServerInfo() %>
    </td>
</tr>
<%	// Get database info
	Connection con = ConnectionManager.getConnection();
    DatabaseMetaData metaData = con.getMetaData();
%>
<tr>
    <td class="jive-label">
        Database Name and Version
    </td>
    <td>
        <%= metaData.getDatabaseProductName() %>
        <%= metaData.getDatabaseProductVersion() %>
    </td>
</tr>
<tr>
    <td class="jive-label">
        JDBC Driver and Version
    </td>
    <td>
        <%= metaData.getDriverName() %> <%= metaData.getDriverVersion() %>
    </td>
</tr>
<%  // Close the database connection:
    try {
        con.close();
    }
    catch (Exception e) {}
%>
</table>
</span>

<%@ include file="footer.jsp" %>
