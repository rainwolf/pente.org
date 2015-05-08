<%
/**
 *	$RCSfile: stats.jsp,v $
 *	$Revision: 1.3 $
 *	$Date: 2002/10/24 21:38:51 $
 */
%>

<%@ page import="java.text.*,
                 java.util.*,
                 com.jivesoftware.util.*,
                 com.jivesoftware.forum.*,
                 com.jivesoftware.forum.util.*"
	errorPage="error.jsp"
%>

<%@ include file="global.jsp" %>

<%! // Global vars, methods, etc

    //
    static float nDays(ResultFilter filter) {
        Date start = filter.getModificationDateRangeMin();
        Date end = filter.getModificationDateRangeMax();
        return (float)((float)(end.getTime() - start.getTime())/((float)24.0*3600.0*1000.0));
    }
%>

<%  // Permission check
    if (!isSystemAdmin) {
        throw new UnauthorizedException("You don't have admin privileges to perform this operation.");
    }

    // Get parameters
    int start = ParamUtils.getIntParameter(request,"start",0);
    int range = ParamUtils.getIntParameter(request,"range",10);

    // Get a calendar object using  Jive's locale and time zone:
    Calendar cal = Calendar.getInstance(JiveGlobals.getTimeZone(), JiveGlobals.getLocale());

    // Reset the calendar to today's 12 AM:
    cal.set(Calendar.HOUR_OF_DAY, 0);
    cal.set(Calendar.MINUTE, 0);
    cal.set(Calendar.SECOND, 0);
    cal.set(Calendar.MILLISECOND, 0);

    // result filter for today (midnight -> present time)
    ResultFilter filterToday = new ResultFilter();
    filterToday.setStartIndex(start);
    filterToday.setNumResults(range);
    filterToday.setModificationDateRangeMin(cal.getTime());
    filterToday.setModificationDateRangeMax(new Date(CacheFactory.currentTime));

    // result filter for yesterday
    ResultFilter filterYesterday = new ResultFilter();
    filterYesterday.setStartIndex(start);
    filterYesterday.setNumResults(range);
    filterYesterday.setModificationDateRangeMax(cal.getTime());
    // back the date up a day
    cal.add(Calendar.DAY_OF_YEAR, -1);
    filterYesterday.setModificationDateRangeMin(cal.getTime());

    // result filter for last week (not including today)
    ResultFilter filterWeek = new ResultFilter();
    filterWeek.setStartIndex(start);
    filterWeek.setNumResults(range);
    filterWeek.setModificationDateRangeMax(cal.getTime());
    // back the date up 6 days
    cal.add(Calendar.DAY_OF_YEAR, -6);
    filterWeek.setModificationDateRangeMin(cal.getTime());
    float nDaysWeek = nDays(filterWeek);

    // result filter for last month (not including today)
    ResultFilter filterMonth = new ResultFilter();
    filterMonth.setStartIndex(start);
    filterMonth.setNumResults(range);
    // change the date to be back to one day ago
    cal.add(Calendar.DAY_OF_YEAR, 6);
    filterMonth.setModificationDateRangeMax(cal.getTime());
    // back up the date one month
    cal.add(Calendar.MONTH, -1);
    filterMonth.setModificationDateRangeMin(cal.getTime());
    float nDaysMonth = nDays(filterMonth);
%>

<%@ include file="header.jsp" %>

<p>

<%  // Title of this page and breadcrumbs
    String title = "Basic Statistics";
    String[][] breadcrumbs = {
        {"Main", "main.jsp"},
        {"Basic Stats", "stats.jsp"}
    };
%>
<%@ include file="title.jsp" %>

<font size="-1">
Below is a simple summary of your forum activity over time.
</font><p>

<%  // Create a list of all forums in the system. Start with root forums:
    List forumList = new java.util.LinkedList();
    for (Iterator iter=forumFactory.getRootForumCategory().getRecursiveForums(); iter.hasNext();)
    {
        forumList.add(iter.next());
    }
    int fCount = forumList.size();
%>

<font size="-1"><b>New Threads and Messages</b></font>
<p>

<table bgcolor="<%= tblBorderColor %>" cellpadding="0" cellspacing="0" border="0" width="100%">
<tr><td>
<table bgcolor="<%= tblBorderColor %>" cellpadding="3" cellspacing="1" border="0" width="100%">
<tr bgcolor="#eeeeee">
    <td align="center" rowspan="2"><font size="-2" face="verdana"><b>FORUM</b></font></td>
    <td align="center" colspan="2"><font size="-2" face="verdana"><b>TODAY</b></font></td>
    <td align="center" colspan="2"><font size="-2" face="verdana"><b>YESTERDAY</b></font></td>
    <td align="center" colspan="2"><font size="-2" face="verdana"><b>PAST WEEK</b></font></td>
    <td align="center" colspan="2"><font size="-2" face="verdana"><b>PAST MONTH</b></font></td>
</tr>
<tr bgcolor="#eeeeee">
    <td align="center"><font size="-2" face="verdana">THREADS</font></td>
    <td align="center"><font size="-2" face="verdana">MESSAGES</font></td>
    <td align="center"><font size="-2" face="verdana">THREADS</font></td>
    <td align="center"><font size="-2" face="verdana">MESSAGES</font></td>
    <td align="center"><font size="-2" face="verdana">THREADS</font></td>
    <td align="center"><font size="-2" face="verdana">MESSAGES</font></td>
    <td align="center"><font size="-2" face="verdana">THREADS</font></td>
    <td align="center"><font size="-2" face="verdana">MESSAGES</font></td>
</tr>

<%
    Iterator itForum = forumList.iterator();

    int idx=0;
    while (idx++ < start && itForum.hasNext()) {
        itForum.next();
    }
    // show "range" number of forums
    idx=0;

    if (!itForum.hasNext()) {
%>

<tr bgcolor="#ffffff">
    <td colspan="9" align="center">
        <font size="-1">
        <p>
        <i>No forums.</i>
        <p>
        </font>
    </td>
</tr>

<%  }

    while (idx++ < range && itForum.hasNext()) {
        Forum forum = (Forum)itForum.next();
        int todayMessageCount = forum.getMessageCount(filterToday);
        int yesterdayMessageCount = forum.getMessageCount(filterYesterday);
        int weekMessageCount = forum.getMessageCount(filterWeek);
        int monthMessageCount = forum.getMessageCount(filterMonth);

        int todayThreadCount = forum.getThreadCount(filterToday);
        int yesterdayThreadCount = forum.getThreadCount(filterYesterday);
        int weekThreadCount = forum.getThreadCount(filterWeek);
        int monthThreadCount = forum.getThreadCount(filterMonth);
%>
<tr bgcolor="#ffffff">
    <td bgcolor="#ebf1f9"><font size="-1"><b><%= forum.getName() %></b></font></td>
    <td align="center"><font size="-1"><%= todayThreadCount %></font></td>
    <td align="center"><font size="-1"><%= todayMessageCount %></font></td>
    <td align="center" bgcolor="#ebf1f9"><font size="-1"><%= yesterdayThreadCount %></td>
    <td align="center" bgcolor="#ebf1f9"><font size="-1"><%= yesterdayMessageCount %></td>
    <td align="center"><font size="-1"><%= weekThreadCount %><br></font><font size="-2" color="#666666"><%= LocaleUtils.getLocalizedNumber((float)weekThreadCount/nDaysWeek) %>/day</font></td>
    <td align="center"><font size="-1"><%= weekMessageCount %><br></font><font size="-2" color="#666666"><%= LocaleUtils.getLocalizedNumber((float)weekMessageCount/nDaysWeek) %>/day</font></td>
    <td align="center" bgcolor="#ebf1f9"><font size="-1"><%= monthThreadCount %><br></font><font size="-2" color="#666666"><%= LocaleUtils.getLocalizedNumber((float)monthThreadCount/nDaysMonth) %>/day</font></td>
    <td align="center" bgcolor="#ebf1f9"><font size="-1"><%= monthMessageCount %><br></font><font size="-2" color="#666666"><%= LocaleUtils.getLocalizedNumber((float)monthMessageCount/nDaysMonth) %>/day</font></td>
</tr>
<%  } %>
</table>
</td></tr>
</table>

<p>

<table cellpadding="0" cellspacing="0" border="0" width="100%">
<tr>
<%  if (start > 0) { %>
	<td width="1%" nowrap><font size="-1"><a href="stats.jsp?start=<%= start-range %>">Stats for previous <%= range %> forums</a></font></td>
<%  } else { %>
	<td width="1%" nowrap><font size="-1">&nbsp;</font></td>
<%  } %>

<%  if (start > 0) { %>
	<td width="98%" align="center"><font size="-1"><a href="stats.jsp?start=0">Front Page</a></font></td>
<%  } else { %>
	<td width="98%" nowrap><font size="-1">&nbsp;</font></td>
<%  } %>

<%  if (start+range < fCount) { %>
	<td width="1%" nowrap><font size="-1"><a href="stats.jsp?start=<%= start+range %>">Stats for next <%= range %> forums</a></font></td>
<%  } else { %>
	<td width="1%" nowrap><font size="-1">&nbsp;</font></td>
<%  } %>
</tr>
</table>

<%@ include file="footer.jsp" %>
