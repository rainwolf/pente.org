
<%
/**
 *	$RCSfile: searchSettings.jsp,v $
 *	$Revision: 1.4.4.1 $
 *	$Date: 2003/01/28 16:08:28 $
 */
%>

<%@ page import="java.util.*,
	             java.text.*,
                 com.jivesoftware.forum.*,
                 com.jivesoftware.forum.database.*,
                 com.jivesoftware.forum.util.*,
                 com.jivesoftware.util.ParamUtils,
                 java.io.File"
	errorPage="error.jsp"
%>

<%@ include file="global.jsp" %>

<%	// Permission check
    if (!isSystemAdmin) {
        throw new UnauthorizedException("You don't have admin privileges to perform this operation.");
    }

    // get parameters

    boolean searchEnabled = ParamUtils.getBooleanParameter(request,"searchEnabled");
    boolean setSearchEnabled = ParamUtils.getBooleanParameter(request,"setSearchEnabled");
	boolean setAutoIndexEnabled = ParamUtils.getBooleanParameter(request,"setAutoIndexEnabled");
	boolean autoIndexEnabled = ParamUtils.getBooleanParameter(request,"autoIndexEnabled");
    int updateInterval = ParamUtils.getIntParameter(request,"updateInterval",5);
	boolean doUpdateIndex = ParamUtils.getBooleanParameter(request,"doUpdateIndex");
	boolean doRebuildIndex = ParamUtils.getBooleanParameter(request,"doRebuildIndex");
    String indexType = ParamUtils.getParameter(request,"indexType");
    if (indexType == null) {
        indexType = "standard";
    }

    // Get the search manager
	SearchManager searchManager = forumFactory.getSearchManager();

    // Types of indexers
    String[][] indexers = {
        { "standard", "Standard" },
        { "german", "German" },
        { "bidi", "Double-Byte (Asian Languages)" }
    };

    // enable or disable search
    if (setSearchEnabled) {
        searchManager.setSearchEnabled(searchEnabled);
        response.sendRedirect("searchSettings.jsp");
        return;
    }

    // enable or disable auto indexing
    if (setAutoIndexEnabled) {
        searchManager.setAutoIndexEnabled(autoIndexEnabled);
        searchManager.setAutoIndexInterval(updateInterval);
        if ("standard".equals(indexType)) {
            DbSearchManager.setAnalyzer(DbSearchManager.STANDARD_ANALYZER);
        }
        else if ("german".equals(indexType)) {
            DbSearchManager.setAnalyzer(DbSearchManager.GERMAN_ANALYZER);
        }
        else if ("bidi".equals(indexType)) {
            DbSearchManager.setAnalyzer(DbSearchManager.DOUBLE_BYTE_ANALYZER);
        }
        response.sendRedirect("searchSettings.jsp");
        return;
    }

    // update index if requested
    if (doUpdateIndex) {
        searchManager.updateIndex();
        response.sendRedirect("searchSettings.jsp");
        return;
    }

    // rebuild index if requested
    if (doRebuildIndex) {
        searchManager.rebuildIndex();
        response.sendRedirect("searchSettings.jsp");
        return;
    }

	autoIndexEnabled = searchManager.isAutoIndexEnabled();
    searchEnabled = searchManager.isSearchEnabled();
    updateInterval = searchManager.getAutoIndexInterval();
    if (DbSearchManager.STANDARD_ANALYZER.equals(DbSearchManager.getAnalyzer())) {
        indexType = "standard";
    }
    else if (DbSearchManager.GERMAN_ANALYZER.equals(DbSearchManager.getAnalyzer())) {
        indexType = "german";
    }
    else if (DbSearchManager.DOUBLE_BYTE_ANALYZER.equals(DbSearchManager.getAnalyzer())) {
        indexType = "bidi";
    }
%>

<%@ include file="header.jsp" %>

<%  // Title of this page and breadcrumbs
    String title = "Search Settings";
    String[][] breadcrumbs = {
        {"Main", "main.jsp"},
        {"Search Settings", "searchSettings.jsp"}
    };
%>
<%@ include file="title.jsp" %>

<%  // If we're doing any indexing operation, display a message
    if (searchManager.isBusy()) {
%>

<script language="JavaScript" type="text/javascript">
<!--
function reloadPage() {
    location.href='searchSettings.jsp';
}
setTimeout(reloadPage,4000);
//-->
</script>

<font size="-1"><b>Indexing...</b></font><p>
<ul>
    <font size="-1">
    <b><%= searchManager.getPercentComplete() %>% complete</b>
    <br>
    Jive Forums is currently updating or rebuilding the search index. This may take
    a few moments.
    </font>
</ul>

<%  } else { %>

<table cellpadding="0" cellspacing="0" border="0">
<tr><td>
    <font size="-1"><b>Search Status</b></font>
    </td>
    <td>
    <a href="#" onclick="helpwin('search','search_status');return false;"
     title="Click for help"
     ><img src="images/help-16x16.gif" width="16" height="16" border="0" hspace="8"></a>
    </td>
</tr>
</table>
<ul>
    <font size="-1">
    Turn the search feature on or off:
    </font><p>
    <form action="searchSettings.jsp">
    <input type="hidden" name="setSearchEnabled" value="true">
    <table bgcolor="<%= tblBorderColor %>" cellpadding="0" cellspacing="0" border="0" width="300">
    <td>
    <table bgcolor="<%= tblBorderColor %>" cellpadding="3" cellspacing="1" border="0" width="100%">
    <tr bgcolor="#ffffff">
	<td align="center"<%= (searchEnabled)?" bgcolor=\"#99cc99\"":"" %>>
        <font size="-1">
		<input type="radio" name="searchEnabled" value="true" id="rb01"
		 <%= (searchEnabled)?"checked":"" %>>
		<label for="rb01"><%= (searchEnabled)?"<b>On</b>":"On" %></label>
        </font>
	</td>
	<td align="center"<%= (!searchEnabled)?" bgcolor=\"#cc6666\"":"" %>>
        <font size="-1">
		<input type="radio" name="searchEnabled" value="false" id="rb02"
		 <%= (!searchEnabled)?"checked":"" %>>
		<label for="rb02"><%= (!searchEnabled)?"<b>Off</b>":"Off" %></label>
        </font>
	</td>
	<td align="center">
		<input type="submit" value="Update">
	</td>
    </tr>

    </table>
    </td>
    </table>
    </form>

    <%  // Show index info if search is enabled
        if (searchEnabled) {
            // Compute the size of the index:
            double size = 0;
            DecimalFormat megFormatter = new DecimalFormat("#,##0.00");
            File searchHome = new File(JiveGlobals.getJiveHome() + File.separator + "search"
                    + File.separator + JiveGlobals.getJiveProperty("search.directory"));
            if (searchHome.exists()) {
                File[] files = searchHome.listFiles();
                for (int i=0; i<files.length; i++) {
                    size += files[i].length();
                }
                size /= 1024.0*1024.0;
            }
        %>


    <table cellpadding="2" cellspacing="0" border="0">
    <tr>
        <td>Index Location:</td>
        <td><%= searchHome %></td>
    </tr>
    <tr>
        <td>Index Size:</td>
        <td><%= megFormatter.format(size) %> MB</td>
    </tr>
    <tr>
        <td>Index Last Updated:</td>
        <td>
        <%= JiveGlobals.formatDateTime(searchManager.getLastIndexedDate()) %>
        </td>
    </tr>
    </table>

    <%  } %>

</ul>

<%  // only show the following section if the search feature is enabled
    if (searchEnabled) {
%>

<form action="searchSettings.jsp">
<input type="hidden" name="setAutoIndexEnabled" value="true">
<table cellpadding="0" cellspacing="0" border="0">
<tr><td>
    <font size="-1"><b>Search Indexing Settings</b></font>
    </td>
    <td>
    <a href="#" onclick="helpwin('search','index_settings');return false;"
     title="Click for help"
     ><img src="images/help-16x16.gif" width="16" height="16" border="0" hspace="8"></a>
    </td>
</tr>
</table>
<ul>
    <table cellpadding="2" cellspacing="0" border="0">
    <tr>
    	<td><input type="radio" name="autoIndexEnabled" value="true" id="rb03"<%= (autoIndexEnabled)?"checked":"" %>></td>
    	<td><font size="-1"><label for="rb03">Auto-Indexing On</label></font></td>
    </tr>
    <tr>
    	<td><input type="radio" name="autoIndexEnabled" value="false" id="rb04"<%= (!autoIndexEnabled)?"checked":"" %>></td>
    	<td><font size="-1"><label for="rb04">Auto-Indexing Off</label></font></td>
    </tr>
    <%  if (!autoIndexEnabled) { %>
    <tr>
    	<td colspan="2"><input type="submit" value="Save Settings"></td>
    </tr>
    <%  } %>
    </table>
    <%  if (autoIndexEnabled) {

    %>
    <p>
    <table>

    <tr>
    	<td>
        <font size="-1">
        Automatically update the index once every
		<select size="1" name="updateInterval">
        <%      for (int i=1; i<=60;) {
            String selected = "";
            if (updateInterval == i) {
                selected = " selected";
            }
        %>
                <option value="<%= i %>"<%= selected %>><%= i %>
        <%          if (i >= 10) {
                i+=5;
            } else {
                i++;
            }
        }
        %>
        </select> minutes.<br><br>

        </font>
        </td>
    </tr>
    <tr>
    	<td>

        <font size="-1">
        Indexer Type:
        </font>
        <select size="1" name="indexType">
        <%  for (int i=0; i<indexers.length; i++) { %>
            <option value="<%= indexers[i][0] %>"<%= (indexType.equals(indexers[i][0])?" selected":"") %>><%= indexers[i][1] %>
        <%  } %>
        </select>

        </td>
    </tr>
    <tr>
    	<td><br><input type="submit" value="Save Settings"></td>
    </tr>
    </table>
    <%  } %>
</ul>
</form>

<form action="searchSettings.jsp">
<table cellpadding="0" cellspacing="0" border="0">
<tr><td>
    <font size="-1"><b>Update Index</b></font>
    </td>
    <td>
    <a href="#" onclick="helpwin('search','update_index');return false;"
     title="Click for help"
     ><img src="images/help-16x16.gif" width="16" height="16" border="0" hspace="8"></a>
    </td>
</tr>
</table>
<ul>
    <font size="-1">
    Manually update the index. This will update the search index with new content since
    it was last updated on <%= JiveGlobals.formatDateTime(searchManager.getLastIndexedDate()) %>.
    <p>
    </font>
    <input type="hidden" name="doUpdateIndex" value="true">
    <input type="submit" value="Update Index">
</ul>
</form>

<form action="searchSettings.jsp">
<table cellpadding="0" cellspacing="0" border="0">
<tr><td>
    <font size="-1"><b>Rebuild Index</b></font>
    </td>
    <td>
    <a href="#" onclick="helpwin('search','rebuild_index');return false;"
     title="Click for help"
     ><img src="images/help-16x16.gif" width="16" height="16" border="0" hspace="8"></a>
    </td>
</tr>
</table>
<ul>
    <font size="-1">
    Manually rebuild the index.
    <p>
    </font>
    <input type="hidden" name="doRebuildIndex" value="true">
    <input type="submit" value="Rebuild Index">
</ul>
</form>

<%  } // end if searchEnabled %>

<%  } // end if searchManager.isBusy() %>

<%@ include file="footer.jsp" %>
