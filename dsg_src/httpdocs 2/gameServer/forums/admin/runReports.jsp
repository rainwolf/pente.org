<%
/**
 *	$RCSfile: runReports.jsp,v $
 *	$Revision: 1.3 $
 *	$Date: 2002/10/28 01:47:00 $
 */
%>

<%@ page import="java.io.*,
                 com.jivesoftware.forum.*,
                 com.jivesoftware.forum.util.*,
                 com.jivesoftware.forum.stats.*,
                 java.awt.*,
                 java.awt.image.*,
                 com.jivesoftware.util.ParamUtils"
%>

<%@ include file="global.jsp" %>

<%	// Permission check
    if (!isSystemAdmin) {
        throw new UnauthorizedException("You don't have admin privileges to perform this operation.");
    }
    
    // get parameters
    boolean runReports = ParamUtils.getBooleanParameter(request,"runReports");

    ReportManager reportManager = ReportManager.getInstance();

    if (runReports && !reportManager.isReportRunning()) {
        String outputDir = JiveGlobals.getJiveProperty("stats.outputDir");
        if (outputDir != null && !outputDir.equals("")) {
            File dir = new File(outputDir);
            reportManager.setOutputDir(dir);
        }
        reportManager.generateStats();
        response.sendRedirect("runReports.jsp");
    }
%>

<%@ include file="header.jsp" %>

<%  // If reports are running, refresh this page every 3 seconds
    if (reportManager.isReportRunning()) {
%>
<meta http-equiv="refresh" content="3; URL=runReports.jsp">
<%  } %>

<p>

<%  // Title of this page and breadcrumbs
    String title = "Run Reports";
    String[][] breadcrumbs = {
        {"Main", "main.jsp"},
        {title, "runReports.jsp"}
    };
%>
<%@ include file="title.jsp" %>

<%  if (!reportManager.isReportRunning()) { %>

    <%  // Do a check to make sure reports will run:
        boolean testSuccess = false;
        try {
            BufferedImage image = new BufferedImage(10,10,BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = (Graphics2D)image.getGraphics();
            testSuccess = true;
        }
        catch (Throwable t) {
            //t.printStackTrace();
        }
        if (testSuccess) {
    %>

        <font size="-1">
        Clicking the button below will start generating reports about your
        forum content and users. Please be patient as you run the reports, as
        they may take several mintues to generate.
        </font><p>

        <font size="-1">
        Reports will be generated in the following directory:
        <%= reportManager.getOutputDir().toString() %>. To change this
        directory, please see the <a href="reports.jsp">report settings</a> page.
        </font><p></p>

        <font size="-1">
        For more information about reports, please click on the
        help icon:
        <a href="#" onclick="helpwin('reports','top');return false;"
         title="Click for help"
         ><img src="images/help-16x16.gif" width="16" height="16" border="0"></a>
        </font><p></p>

        <form action="runReports.jsp">
        <input type="hidden" name="runReports" value="true">
        <center>
        <input type="submit" value="Run Reports">
        </center>
        </form>

    <%  } else { %>

        <font size="-1" color="#ff0000"><b>!</b></font>
        <font size="-1">
        Your environment is not configured to generate statistics.
        Please refer to the documentation on the
        <a href="reports.jsp">reports configuration</a> page.
        </font>

    <%  } %>

<%  }
    // Else, a report is running so show progress
    else {

%>

    <font size="-1">
    Jive Forums is currently generating a report. Please be patient, as this
    might take several minutes.
    </font><p></p>

    <font size="-1">
    <b>Progress:</b>
    </font><p>

    <ul>
    <table cellpadding="3" cellspacing="0" border="0">
    <tr>
    <%  if (reportManager.getInitializing() == ReportManager.NOT_DONE) { %>
        <td><img src="images/x.gif" width="13" height="13" border="0" vspace="3" hspace="3"></td>
        <td><font size="-1">Initializing...</font>
        </td>
    <%  } else if (reportManager.getInitializing() == ReportManager.RUNNING) { %>
        <td><img src="images/x.gif" width="13" height="13" border="0" vspace="3" hspace="3"></td>
        <td><font size="-1"><b>Initializing...</b></font>
        </td>
    <%  } else if (reportManager.getInitializing() == ReportManager.DONE) { %>
        <td><img src="images/check.gif" width="13" height="13" border="0" vspace="3" hspace="3"></td>
        <td><font size="-1">Initializing... (done)</font>
        </td>
    <%  } %>
    </tr>
    <tr>
        <td>&nbsp;</td>
        <td>
            <font size="-1">
            Running Reports:
            </font>
        </td>
    </tr>
    <%  // Report types:
        int[] reportTypes = null;
        if (reportManager.isEnableUserReports()) {
            reportTypes = new int[] {
                ReportManager.FORUM_REPORTS,
                ReportManager.THREAD_REPORTS,
                ReportManager.MESSAGE_REPORTS,
                ReportManager.USER_REPORTS
            };
        }
        else {
            reportTypes = new int[] {
                ReportManager.FORUM_REPORTS,
                ReportManager.THREAD_REPORTS,
                ReportManager.MESSAGE_REPORTS
            };
        }
        for (int i=0; i<reportTypes.length; i++) {
    %>
    <tr>
        <%  if (reportManager.getRunning(reportTypes[i]) == ReportManager.NOT_DONE) { %>
            <td><img src="images/x.gif" width="13" height="13" border="0" vspace="3" hspace="3"></td>
        <%  } else if (reportManager.getRunning(reportTypes[i]) == ReportManager.RUNNING) { %>
            <td><img src="images/x.gif" width="13" height="13" border="0" vspace="3" hspace="3"></td>
        <%  } else if (reportManager.getRunning(reportTypes[i]) == ReportManager.DONE) { %>
            <td><img src="images/check.gif" width="13" height="13" border="0" vspace="3" hspace="3"></td>
        <%  } %>
        <td>
            <font size="-1">
            <nobr>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</nobr>
            <%  if (reportManager.getRunning(reportTypes[i]) == ReportManager.RUNNING) { %>
            <b>
            <%  } %>
            Running
            <%  switch (reportTypes[i]) {
                    case ReportManager.FORUM_REPORTS:
                        out.print("Forum Reports");
                        break;
                    case ReportManager.THREAD_REPORTS:
                        out.print("Topic Reports");
                        break;
                    case ReportManager.MESSAGE_REPORTS:
                        out.print("Message Reports");
                        break;
                    case ReportManager.USER_REPORTS:
                        out.print("User Reports");
                        break;
                }
            %>
            ... (<%= reportManager.getPercentComplete(reportTypes[i]) %>% done)
            <%  if (reportManager.getRunning(reportTypes[i]) == ReportManager.RUNNING) { %>
            <b>
            <%  } %>
            </font>
        </td>
    </tr>
    <%  } // end for %>
    <tr>
    <%  if (reportManager.getGeneratingOutput() == ReportManager.NOT_DONE) { %>
        <td><img src="images/x.gif" width="13" height="13" border="0" vspace="3" hspace="3"></td>
        <td><font size="-1">Generating HTML and images ...</font>
        </td>
    <%  } else if (reportManager.getGeneratingOutput() == ReportManager.RUNNING) { %>
        <td><img src="images/x.gif" width="13" height="13" border="0" vspace="3" hspace="3"></td>
        <td><font size="-1"><b>Generating HTML and images ...</b></font>
        </td>
    <%  } else if (reportManager.getGeneratingOutput() == ReportManager.DONE) { %>
        <td><img src="images/check.gif" width="13" height="13" border="0" vspace="3" hspace="3"></td>
        <td><font size="-1">Generating HTML and images ... (done)</font>
        </td>
    <%  } %>
    </tr>
    </table>
    </ul>

<%  } %>

<%@ include file="footer.jsp" %>

