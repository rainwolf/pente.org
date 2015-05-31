<%
/**
 *	$RCSfile: reports.jsp,v $
 *	$Revision: 1.6 $
 *	$Date: 2002/11/23 02:50:11 $
 */
%>

<%@ page import="java.awt.*,
                 java.awt.image.*,
                 java.io.*,
                 java.util.*,
				 java.text.*,
				 com.jivesoftware.util.*,
                 com.jivesoftware.forum.*,
				 com.jivesoftware.forum.database.*,
                 com.jivesoftware.forum.stats.*,
				 com.jivesoftware.forum.util.*,
                 com.jivesoftware.base.stats.util.*,
                 com.jivesoftware.util.DateRange,
                 com.jivesoftware.util.RelativeDateRange"
%>

<%@ include file="global.jsp" %>
 
<%	// Permission check
    if (!isSystemAdmin) {
        throw new UnauthorizedException("You don't have admin privileges to perform this operation.");
    }

    ReportManager reportManager = ReportManager.getInstance();

    // get parameters
    boolean save = ParamUtils.getBooleanParameter(request,"save");
    boolean useDefaultOutputDir = ParamUtils.getBooleanParameter(request,"useDefaultOutputDir");
    String outputDir = ParamUtils.getParameter(request,"outputDir",true);
    String defaultOutputDir = (reportManager.getOutputDir()).toString();
    boolean testGraphics = ParamUtils.getBooleanParameter(request,"testGraphics");
    boolean createDir = ParamUtils.getBooleanParameter(request,"createDir");
    long[] forumIDs = ParamUtils.getLongParameters(request,"forum",-1L);
    long[] excludedForumIDs = ParamUtils.getLongParameters(request,"excludedForum",-1L);
    boolean addExcludedForumID = request.getParameter("addExcludedForumID") != null;
    boolean removeExcludedForumID = request.getParameter("removeExcludedForumID") != null;
    String datePresetValue = ParamUtils.getParameter(request,"datePresetValue");
    boolean setDatePresetValue = request.getParameter("setDatePresetValue") != null;
    boolean enableUserGroupReports = ParamUtils.getBooleanParameter(request,"enableUserGroupReports");
    boolean saveUserGroupReportPref = request.getParameter("saveUserGroupReportPref") != null;

    if (saveUserGroupReportPref) {
        reportManager.setEnableUserReports(enableUserGroupReports);
        response.sendRedirect("reports.jsp");
        return;
    }
    enableUserGroupReports = reportManager.isEnableUserReports();

    if (setDatePresetValue) {
        RelativeDateRange dateRange = new RelativeDateRange("", datePresetValue);
        reportManager.setGlobalDateRange(dateRange);
        response.sendRedirect("reports.jsp");
        return;
    }
    DateRange datePreset = reportManager.getGlobalDateRange();

    if (addExcludedForumID) {
        for (int i=0; i<forumIDs.length; i++) {
            if (forumIDs[i] != -1L) {
                try {
                    Forum f = forumFactory.getForum(forumIDs[i]);
                    reportManager.addExcludedForumID(forumIDs[i]);
                }
                catch (ForumNotFoundException fnfe) {}
            }
        }
        response.sendRedirect("reports.jsp");
        return;
    }

    if (removeExcludedForumID) {
        for (int i=0; i<excludedForumIDs.length; i++) {
            if (excludedForumIDs[i] != -1L) {
                try {
                    Forum f = forumFactory.getForum(excludedForumIDs[i]);
                    reportManager.removeExcludeForumID(excludedForumIDs[i]);
                }
                catch (ForumNotFoundException fnfe) {}
            }
        }
        response.sendRedirect("reports.jsp");
        return;
    }

    // Get the list of excluded forums:
    java.util.List excludedForums = reportManager.getExcludedForumIDs();

    boolean testSuccess = false;
    if (testGraphics) {
        // try to create a BufferedImage, get a graphics context
        try {
            BufferedImage image = new BufferedImage(10,10,BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = (Graphics2D)image.getGraphics();
            testSuccess = true;
        }
        catch (Throwable t) {
            //t.printStackTrace();
        }
    }
    
    boolean outputDirErrors = false;
    boolean createDirErrors = false;
    if (save) {
        if (useDefaultOutputDir) {
            outputDir = JiveGlobals.getJiveHome() + File.separator + "stats" + File.separator + "reports";
        }
        try {
            File dir = new File(outputDir);
            if (!dir.exists()) {
                if (createDir) {
                    if (!dir.mkdir()) {
                        createDirErrors = true;
                    }
                }
                else {
                    outputDirErrors = true;
                }
            }
            if (!dir.canRead()) {
                outputDirErrors = true;
            }
        } catch (Exception e) {
            outputDirErrors = true;
        }
    }
    
    if (save && !outputDirErrors && !createDirErrors) {
        // save the report output dir
        JiveGlobals.setJiveProperty("stats.useDefaultOutputDir", ""+useDefaultOutputDir);
        if (outputDir != null) {
            reportManager.setOutputDir(new File(outputDir.trim()));
            response.sendRedirect("reports.jsp");
            return;
        }
    }
    
    String currentUseDefaultOutputDir = JiveGlobals.getJiveProperty("stats.useDefaultOutputDir");
    if (currentUseDefaultOutputDir == null || "".equals(currentUseDefaultOutputDir)) {
        useDefaultOutputDir = true;
    }
    else {
        useDefaultOutputDir = "true".equals(JiveGlobals.getJiveProperty("stats.useDefaultOutputDir"));
    }
    String currentOutputDir = JiveGlobals.getJiveProperty("stats.outputDir");
    if (currentOutputDir == null || "".equals(currentOutputDir)) {
        if (!defaultOutputDir.equals(currentOutputDir)) {
            outputDir = "";
        }
        else {
            outputDir = defaultOutputDir;
        }
    }
    else {
        outputDir = currentOutputDir;
    }
%>

<%@ include file="header.jsp" %>

<p>

<%  // Title of this page and breadcrumbs
    String title = "Configure Reports";
    String[][] breadcrumbs = {
        {"Main", "main.jsp"},
        {title, "reports.jsp"}
    };
%>
<%@ include file="title.jsp" %>

<%  // Check to see if there is a report running - if so, don't display
    // any of the options below
    if (reportManager.isReportRunning()) {
%>
    <font size="-1">
    A report is currently running. Once the report is finished you will be able
    to change the report settings.
    </font><p>

    <font size="-1">
    To view the stats of the report, please see the
    <a href="runReports.jsp">report status</a> page.

<%  }
    // No report is running so show the report options
    else {
%>

    <form action="reports.jsp" method="post" name="reportsForm">
    <input type="hidden" name="testGraphics" value="true">
    <font size="-1"><b>Test Server-Side Graphics Capability</b></font>
    <a href="#" onclick="helpwin('reports','server_side_graphics');return false;"
         title="Click for help"
         ><img src="images/help-16x16.gif" width="16" height="16" border="0"></a>
    <ul>
        <font size="-1">
        The reporting package requires that your server environment support
        creating images. By default, many Unix-based servers are not configured for this
        support and require installation of the <a href="pja-install.html" target="_new">PJA Toolkit</a>.
        Use the button below to test whether your server environment is properly
        configured to support image generation.<p>
        <font color="#ff0000">Warning:</font> If you're using the IBM JDK running this
        test might crash your VM.<p>
        </font>
        <%  if (testGraphics && testSuccess) { %>
            <font size="-1" color="#009900"><b>Test Successful</b></font><br>
            <font size="-1">Your server-side environment is correctly configured
            to run the Jive Forums reporting tools.</font>
            <p>
        <%  } else if (testGraphics && !testSuccess) { %>
            <font size="-1" color="#ff0000"><b>Test Failed</b></font><br>
            <font size="-1">Your appserver is not properly configured to run the
            Jive Forums reporting tools. Please follow the
            <a href="pja-install.html" target="_new">PJA Toolkit</a> documentation,
            restart your appserver and attempt this test again.</font>
            <p>
        <%  } %>
        <input type="submit" value="Run Test">
    </ul>
    </form>

    <form action="reports.jsp" method="post" name="reportsForm">
    <input type="hidden" name="save" value="true">

    <font size="-1"><b>Reports Output Directory</b></font>
    <a href="#" onclick="helpwin('reports','output_directory');return false;"
         title="Click for help"
         ><img src="images/help-16x16.gif" width="16" height="16" border="0"></a>
    <ul>
        <font size="-1">
        Report information is saved to the directory listed below. By default,
        reports are written to your jiveHome directory so you will need to browse
        your filesystem in order to view them. You may wish to enter an
        alternative directory that is in the path of your webserver documents directory
        so that the reports are publicly viewable.
        </font><p>
        <%  if (save && outputDirErrors) { %>
            <font size="-1" color="#ff0000"><b>Error:</b></font>
            <font size="-1">The directory you submitted is not valid. Make sure it
            exists and that your appserver has permission to write to it.<p></font>
        <%  } %>
        <%  if (save && createDirErrors) { %>
            <font size="-1" color="#ff0000"><b>Error:</b></font>
            <font size="-1">Unable to create the directory
            <%= outputDir %> -- your appserver may not have permission
            to create this directory.<p></font>
        <%  } %>
        <table cellpadding="2" cellspacing="0" border="0">
        <tr>
            <td valign="top">
                <input type="radio" name="useDefaultOutputDir" value="true" id="rb01"
                <%= (useDefaultOutputDir)?" checked":"" %>>
            </td>
            <td><font size="-1">
                <label for="rb01">Use the default output directory:</label>
                </font>
                <br><tt><%= defaultOutputDir %></tt>
            </td>
        </tr>
        <tr>
            <td valign="top">
                <input type="radio" name="useDefaultOutputDir" value="false" id="rb02"
                <%= (!useDefaultOutputDir)?" checked":"" %>>
            </td>
            <td><font size="-1">
                <label for="rb02">Save generated reports to this directory:</label>
                </font><br>
                <input type="text" name="outputDir" value="<%= ((!useDefaultOutputDir)?outputDir:"") %>" size="40" maxlength="255"
                 onfocus="this.form.useDefaultOutputDir[1].checked=true;">
                <br>
                <font size="-1">
                <input type="checkbox" name="createDir" id="cb01" checked>
                <label for="cb01">Create directory if it doesn't exist</label>
                </font>
            </td>
        </tr>
        <tr>
            <td>&nbsp;</td>
            <td><input type="submit" value="Save Settings"></td>
        </tr>
        </table>

    </ul>
    </form>


    <form action="reports.jsp" method="post" name="reportsForm">

    <font size="-1"><b>Excluded Forums</b></font>
    <a href="#" onclick="helpwin('reports','excluded_forums');return false;"
         title="Click for help"
         ><img src="images/help-16x16.gif" width="16" height="16" border="0"></a>
    <ul>
        <font size="-1">
        You may wish to not run reports on some of your forums. To do so,
        add forums to the "excluded forums" list.
        </font><p>
        <table cellpadding="2" cellspacing="0" border="0">
        <tr>
            <td align="center"><font size="-2" face="verdana"><b>Included Forums</b></font></td>
            <td>&nbsp;</td>
            <td align="center"><font size="-2" face="verdana"><b>Excluded Forums</b></font></td>
        </tr>
        <tr>
            <td valign="top">
                <select size="5" name="forum" multiple>
            <%  for (Iterator i=forumFactory.getRootForumCategory().getRecursiveForums(); i.hasNext(); )
                {
                    Forum forum = (Forum)i.next();
                    if (!excludedForums.contains(new Long(forum.getID()))) {
            %>
                    <option value="<%= forum.getID() %>"><%= forum.getName() %>
            <%      }
                }
            %>
                </select>
            </td>
            <td>
                <input type="submit" value=" &gt; " name="addExcludedForumID">
                <br>
                <input type="submit" value=" &lt; " name="removeExcludedForumID">
            </td>
            <td valign="top">
                <select size="5" name="excludedForum" multiple>
            <%  for (int i=0; i<excludedForums.size(); i++) {
                    long forumID = ((Long)excludedForums.get(i)).longValue();
                    try {
                        Forum forum = forumFactory.getForum(forumID);
            %>
                    <option value="<%= forum.getID() %>"><%= forum.getName() %>
            <%      }
                    catch (ForumNotFoundException ignored) {}
                }
            %>
                </select>
            </td>
        </tr>
        </table>
    </ul>
    </form>

    <form action="reports.jsp">

    <font size="-1"><b>User and Group Reports</b></font>
    <a href="#" onclick="helpwin('reports','user_group');return false;"
         title="Click for help"
         ><img src="images/help-16x16.gif" width="16" height="16" border="0"></a>
    <ul>
        <font size="-1">
        You may wish to not run reports on users or groups, especially if you have a
        customer user or group implementation. User reports are reports that detail
        things like active users, users created over time or user domains.
        </font><p></p>
        <table cellpadding="3" cellspacing="0" border="0">
        <tr>
            <td><input type="radio" name="enableUserGroupReports" value="true" id="ug01"
                 <%= (enableUserGroupReports)?" checked":"" %>>
            </td>
            <td><font size="-1">
                <label for="ug01">
                Enable User and Group Reports
                </label>
                </font>
            </td>
        </tr>
        <tr>
            <td><input type="radio" name="enableUserGroupReports" value="false" id="ug02"
                 <%= (!enableUserGroupReports)?" checked":"" %>>
            </td>
            <td><font size="-1">
                <label for="ug02">
                Disable User and Group Reports
                </label>
                </font>
            </td>
        </tr>
        <tr>
            <td>&nbsp;</td>
            <td><input type="submit" name="saveUserGroupReportPref" value="Save Settings"></td>
        </tr>
        </table>
    </ul>

    </form>

    <form action="reports.jsp">

    <font size="-1"><b>Global Date Range</b></font>
    <a href="#" onclick="helpwin('reports','date_range');return false;"
         title="Click for help"
         ><img src="images/help-16x16.gif" width="16" height="16" border="0"></a>
    <ul>
        <font size="-1">
        You can set a global date range to restrict the size of data being examined. This is useful
        if you have a large amount of forum traffic because examining all data will take
        a long time and will increase your database load quite a bit.
        </font><p></p>
        <table bgcolor="#bbbbbb" cellspacing="0" cellpadding="0" border="0">
        <tr><td>
        <table bgcolor="#bbbbbb" cellpadding="3" cellspacing="1" border="0">
        <tr bgcolor="#eeeeee">
            <td align="center" bgcolor="#DFE6F9"><input type="radio" name="datePresetValue"
                    value="<%= RelativeDateRange.LAST_7_DAYS.toString() %>" id="d01"
                    <%= (datePreset.equals(RelativeDateRange.LAST_7_DAYS))?" checked":"" %>>
            </td>
            <td align="center" bgcolor="#C1D2FA"><input type="radio" name="datePresetValue"
                    value="<%= RelativeDateRange.LAST_30_DAYS.toString() %>" id="d02"
                    <%= (datePreset.equals(RelativeDateRange.LAST_30_DAYS))?" checked":"" %>>
            </td>
            <td align="center" bgcolor="#A6BEFB"><input type="radio" name="datePresetValue"
                    value="<%= RelativeDateRange.LAST_90_DAYS.toString() %>" id="d03"
                    <%= (datePreset.equals(RelativeDateRange.LAST_90_DAYS))?" checked":"" %>>
            </td>
            <td align="center" bgcolor="#8CADFD"><input type="radio" name="datePresetValue"
                    value="<%= RelativeDateRange.ALL.toString() %>" id="d04"
                    <%= (datePreset.equals(RelativeDateRange.ALL))?" checked":"" %>>
            </td>
        </tr>
        <tr bgcolor="#ffffff">
            <td align="center" nowrap>
                <font size="-1">
                <label for="d01">
                &nbsp; Last 7 Days &nbsp;
                </label>
                </font>
            </td>
            <td align="center" nowrap>
                <font size="-1">
                <label for="d02">
                &nbsp; Last 30 Days &nbsp;
                </label>
                </font>
            </td>
            <td align="center" nowrap>
                <font size="-1">
                <label for="d03">
                &nbsp; Last 90 Days &nbsp;
                </label>
                </font>
            </td>
            <td align="center" nowrap>
                <font size="-1">
                <label for="d04">
                &nbsp; All &nbsp;
                </label>
                </font>
                <font size="-2">
                <br>(Not recommended for<br>large communities)
                </font>
            </td>
        </tr>
        </table>
        </td></tr>
        </table>
        <p>
        <input type="submit" name="setDatePresetValue" value="Save Settings">
        </p>
    </ul>

    </form>

<%  } // end else %>

<%@ include file="footer.jsp" %>

