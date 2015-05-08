<%
/**
 *    $RCSfile: member.jsp,v $
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
                 com.tangosol.net.*,
                 com.tangosol.net.Member"
%>

<%@ include file="global.jsp" %>

<%@ include file="cacheUtils.jsp" %>

<%
    // dbForumFactory and cacheManager are defined in cacheUtils.jsp

    // Is cacheing enabled?
    boolean cacheEnabled = cacheManager.isCacheEnabled();

    // Is clustering enabled?
    boolean clusteringEnabled = com.jivesoftware.util.CacheFactory.isClusteringEnabled();

    // If clustering is not enabled, just return to the cache page.
    if (!clusteringEnabled) {
        response.sendRedirect("cache.jsp");
        return;
    }

    // Get the passed in UID
    String UID = ParamUtils.getParameter(request,"uid");

    // Load the member based on the UID
    Cluster cluster = com.tangosol.net.CacheFactory.ensureCluster();
    Set members = cluster.getMemberSet();
    Member member = null;
    for (Iterator iter=members.iterator(); iter.hasNext();) {
        Member m = (Member)iter.next();
        if (m.getUid().toString().equals(UID)) {
            member = m;
            break;
        }
    }

    boolean isLocalMember = cluster.getLocalMember().equals(member);

    // Get the cache stats object:
    Map cacheStats = com.tangosol.net.CacheFactory.getOptimisticCache(
            "$cacheStats", com.tangosol.net.CacheFactory.class.getClassLoader());
%>

<%@ include file="header.jsp" %>

<p>

<%  // Title of this page and breadcrumbs
    String title = "Cluster Node: " + member.getAddress().getHostName();
    if (isLocalMember) {
        title += " (Local)";
    }
    String[][] breadcrumbs = {
        {"Main", "main.jsp"},
        {"Cache Settings", "cache.jsp"},
        {"Cluster Node", "member.jsp?uid=" + UID}
    };
%>
<%@ include file="title.jsp" %>

<font size="-1">
<table border=0><tr><td>
<img src="images/cache_none.gif" width="29" height="55" border="0" hspace="10">
</td><td>
<font size="-1">
<b>Node Address:</b> <%= member.getAddress().getHostAddress() %>:<%= member.getPort() %> <br>
<b>Joined Cluster:</b> <%= SkinUtils.dateToText(request,pageUser,new Date(member.getTimestamp())) %>
 </font></td></tr></table>
<p>

Cache statistics for this cluster node appear below, and are updated every ten seconds.
</font><p>

<table bgcolor="<%= tblBorderColor %>" cellpadding="0" cellspacing="0" border="0">
<tr><td>
<table cellpadding="4" cellspacing="1" border="0" width="100%">
<tr bgcolor="#eeeeee">
<td align="center"><font size="-2" face="verdana"><b>CACHE TYPE</b></font></td>
<td align="center"><font size="-2" face="verdana"><b>SIZE</b></font></td>
<td align="center"><font size="-2" face="verdana"><b>OBJECTS</b></font></td>
<td align="center"><font size="-2" face="verdana"><b>EFFECTIVENESS</b></font></td>
</tr>
<%  Map cNames = (Map)cacheStats.get(UID);
    if (cNames == null) {
%>
<tr bgcolor="#ffffff">
<td align="center" colspan="4"><font size="-1"><i>No stats available</i></font></td>
</tr>
<%  }
    else {
        // Iterate through the cache names,
        for (Iterator iter=cNames.keySet().iterator(); iter.hasNext();) {
            //   Look for the cache name in the cache name array - if it exists,
            // continue and print the stats for this cache:
            String cacheName = (String)iter.next();
            for (int i=0; i<cacheNames.length; i++) {
                if (cacheNames[i].equals(cacheName)) {
                    long[] theStats = (long[])cNames.get(cacheName);
                    long size = theStats[0];
                    long maxSize = theStats[1];
                    long numObjects = theStats[2];

                    double memUsed = (double)size/(1024*1024);
                    double totalMem = (double)maxSize/(1024*1024);
                    double freeMem = 100 - 100*memUsed/totalMem;
                    long hits = (long)theStats[3];
                    long misses = (long)theStats[4];
                    double hitPercent = 0.0;
                    if (hits + misses == 0) {
                        hitPercent = 0.0;
                    }
                    else {
                        hitPercent = 100*(double)hits/(hits+misses);
                    }
                    boolean lowEffec = (hits > 500 && hitPercent < 85.0 && freeMem < 20.0);
%>
<tr bgcolor="#ffffff">
    <td>
        <font size="-1"><%= cacheName %></font>
    </td>
    <td align="right">
        <font size="-1">
        &nbsp;
        <%= mbFormat.format(totalMem) %> MB,
        <%= percentFormat.format(freeMem)%>% free
        &nbsp;
        </font>
    </td>
    <td align="right">
        <font size="-1">
        &nbsp;
        <%= numObjects %>
        &nbsp;
        </font>
    </td>
    <td align="right">
        <font size="-1">
        &nbsp;
        <%  if (lowEffec) { %>
        <font color="#ff0000"><b><%= percentFormat.format(hitPercent)%>%</b></font>
        <%  } else { %>
        <b><%= percentFormat.format(hitPercent)%>%</b>
        <%  } %>
        (<%= hits %> hits, <%= misses %> misses)
        &nbsp;
        </font>
    </td>
</tr>
<%                  break;
                }
            }
        }
    }
%>
</table>
</td></tr>
</table>

<%@ include file="footer.jsp" %>
