<%
/**
 *    $RCSfile: cache.jsp,v $
 *    $Revision: 1.4 $
 *    $Date: 2002/10/31 20:56:46 $
 */
%>

<%@ page import="java.util.*,
                 java.text.*,
                 com.jivesoftware.util.*,
                 com.jivesoftware.forum.*,
                 com.jivesoftware.forum.database.*,
                 com.jivesoftware.forum.util.*"
%>

<%@ include file="global.jsp" %>

<%@ include file="cacheUtils.jsp" %>

<%    // Permission check
    if (!isSystemAdmin) {
        throw new UnauthorizedException("You don't have admin privileges to perform this operation.");
    }

    // get parameters
    boolean cacheEnabled = ParamUtils.getBooleanParameter(request,"cacheEnabled");
    boolean doCache = ParamUtils.getBooleanParameter(request,"doCache");
    boolean clearCache = request.getParameter("clearCache") != null;
    boolean doWarmup = request.getParameter("warmupCache") != null;
    boolean editSizes = request.getParameter("editSizes") != null;
    boolean setClusteringEnabled = request.getParameter("setClusteringEnabled") != null;
    boolean clusteringEnabled = ParamUtils.getBooleanParameter(request,"clusteringEnabled");
    String clusterUID = ParamUtils.getParameter(request,"clusterUID");
    int[] cacheIDs = ParamUtils.getIntParameters(request,"cacheID",-1);
    boolean promptForRestart = "restart".equals(request.getParameter("prompt"));
    boolean stqcEnabled = ParamUtils.getBooleanParameter(request,"stqcEnabled");
    boolean setStqcEnabled = request.getParameter("setStqcEnabled") != null;
    long stqcLifetime = ParamUtils.getLongParameter(request,"stqcLifetime",-1L);
    boolean saveLifetime = request.getParameter("saveLifetime") != null;

    // Redirect to edit cache sizes
    if (editSizes) {
        response.sendRedirect("editCache.jsp");
        return;
    }

    // dbForumFactory and cacheManager are defined in cacheUtils.jsp

    // Turn the cache on or off
    if (doCache) {
        cacheManager.setCacheEnabled(cacheEnabled);
        response.sendRedirect("cache.jsp");
        return;
    }

    // Warmup cache if requested
    boolean indicateWarmup = false;
    if (doWarmup) {
        ForumCategory rootCat = dbForumFactory.getRootForumCategory();
        for (Iterator iter=rootCat.getRecursiveForums(); iter.hasNext();) {
            Forum forum = (Forum)iter.next();
            Runnable r = new ForumCacheWarmupTask(forum);
            TaskEngine.addTask(r);
            indicateWarmup = true;
        }
    }

    // Clear one or multiple caches if requested.
    if (clearCache) {
        for (int i=0; i<cacheIDs.length; i++) {
            if (cacheIDs[i] != -1) {
                Cache cache = caches[cacheIDs[i]];
                cache.clear();
            }
        }
        // Done so redirect
        response.sendRedirect("cache.jsp");
        return;
    }

    if (setClusteringEnabled && Version.EDITION == Version.Edition.ENTERPRISE) {
        com.jivesoftware.util.CacheFactory.setClusteringEnabled(clusteringEnabled);
        // Since we've changed the cacheing policy, we need to re-init
        // the caches:
        DatabaseCacheManager dbCacheManager = dbForumFactory.getCacheManager();
        //dbCacheManager.initCache();
        // Done, so redirect
        response.sendRedirect("cache.jsp?prompt=restart");
        return;
    }

    if (setStqcEnabled && Version.EDITION == Version.Edition.ENTERPRISE) {
        DatabaseCacheManager dbCacheManager = dbForumFactory.getCacheManager();
        dbCacheManager.setShortTermQueryCacheEnabled(stqcEnabled);
        // Done, so redirect:
        response.sendRedirect("cache.jsp");
        return;
    }

    if (saveLifetime && Version.EDITION == Version.Edition.ENTERPRISE) {
        if (stqcLifetime != -1L) {
            cacheManager.shortTermQueryCache.setMaxLifetime(stqcLifetime);
            JiveGlobals.setJiveProperty("cache.shortTermQueryCache.time",
                    String.valueOf(stqcLifetime));
            // Done, so redirect:
            response.sendRedirect("cache.jsp");
            return;
        }
    }

    // Reset variables for this page:
    cacheEnabled = cacheManager.isCacheEnabled();
    if (Version.EDITION == Version.Edition.ENTERPRISE) {
        stqcEnabled = cacheManager.isShortTermQueryCacheEnabled();
        stqcLifetime = 5000L;
        if (cacheManager.shortTermQueryCache != null) {
            stqcLifetime = cacheManager.shortTermQueryCache.getMaxLifetime();
        }
    }

    // Number of forums in the system
    int numForums = dbForumFactory.getForumCount();

    // Determine if clustering is enabled:
    clusteringEnabled = com.jivesoftware.util.CacheFactory.isClusteringEnabled();

    Set clusters = null;
    Map cacheStats = null;
    if (clusteringEnabled) {
        clusters = com.tangosol.net.CacheFactory.ensureCluster().getMemberSet();
        cacheStats = com.tangosol.net.CacheFactory.getOptimisticCache(
            "$cacheStats", com.tangosol.net.CacheFactory.class.getClassLoader());
    }
%>

<%@ include file="header.jsp" %>

<p>

<%  // Title of this page and breadcrumbs
    String title = "Cache Settings";
    String[][] breadcrumbs = {
        {"Main", "main.jsp"},
        {"Cache Settings", "cache.jsp"}
    };
%>
<%@ include file="title.jsp" %>

<script language="JavaScript" type="text/javascript">
    <%  if (promptForRestart) { %>
        alert("Because you changed your caching policy, you MUST restart your appserver or Jive will not work correctly.");
    <%  } else if (indicateWarmup) { %>
        alert("A cache warmup task has been started in the background.");
    <%  } else { %>

    <%  } %>
</script>

<font size="-1">
Jive Forums relies on its cache to process forum data efficiently. Use this panel
to monitor and modify your cache settings. There are very few circumstances where
you should entirely disable cache. However, you can edit the cache sizes to tune for
minimal memory use or maximum performance.
</font>
<p>

<%  if (cacheEnabled && clusteringEnabled && Version.EDITION == Version.Edition.ENTERPRISE) { %>
<font size="-1"><b>Cluster Overview</b></font>
<ul>
    <font size="-1">
    Below is an overview of your Jive Forums cluster. You have
    <%= clusters.size() %> node<%= (clusters.size()==1?"":"s") %> running
    and you are licensed to
    <%= LicenseManager.getNumClusterMembers() %>
    node<%= (LicenseManager.getNumClusterMembers()==1?"":"s") %>
    in this cluster.
    </font><p>
    <font>
</ul>

<table cellpadding="0" cellspacing="0" border="0" width="100%">
<tr>
    <td width="1%"><img src="images/blank.gif" width="40" height="1" border="0"></td>
    <td width="1%" valign="top">

    <table cellpadding="0" cellspacing="0" border="0" width="1%">
    <tr>
    <%      // Build a list of the members in the cluster:
            java.util.List memberList = new java.util.LinkedList();
            for (Iterator iter=clusters.iterator(); iter.hasNext();) {
                memberList.add((com.tangosol.coherence.component.net.member.ClusterMember)iter.next());
            }

            for (int i=0; i<memberList.size(); i++) {
                com.tangosol.coherence.component.net.member.ClusterMember member
                        = (com.tangosol.coherence.component.net.member.ClusterMember)memberList.get(i);
    %>
        <td>
            <table cellpadding="0" cellspacing="0" border="0">
            <tr>
                <%  if (clusters.size() == 1) { %>

                    <td width="49%"><img src="images/blank.gif" width="100%" height="55" border="0"></td>
                    <td><a href="member.jsp?uid=<%= member.getUid().toString() %>"
                        title="Click for more details"
                        ><img src="images/cache_none.gif" width="29" height="55" border="0"></a></td>
                    <td width="49%"><img src="images/blank.gif" width="100%" height="55" border="0"></td>

                <%  } else if (i == 0) { %>

                    <td width="49%"><img src="images/blank.gif" width="100%" height="55" border="0"></td>
                    <td><a href="member.jsp?uid=<%= member.getUid().toString() %>"
                        title="Click for more details"
                        ><img src="images/cache_left.gif" width="40" height="55" border="0"></a></td>
                    <td width="49%"><img src="images/cache_stretch.gif" width="100%" height="55" border="0"></td>

                <%  } else if ((i+1) >= clusters.size()) { %>

                    <td width="49%"><img src="images/cache_stretch.gif" width="100%" height="55" border="0"></td>
                    <td><a href="member.jsp?uid=<%= member.getUid().toString() %>"
                        title="Click for more details"
                        ><img src="images/cache_right.gif" width="40" height="55" border="0"></a></td>
                    <td width="49%"><img src="images/blank.gif" width="100%" height="55" border="0"></td>

                <%  } else { %>

                    <td width="49%"><img src="images/cache_stretch.gif" width="100%" height="55" border="0"></td>
                    <td><a href="member.jsp?uid=<%= member.getUid().toString() %>"
                        title="Click for more details"
                        ><img src="images/cache_center.gif" width="41" height="55" border="0"></a></td>
                    <td width="49%"><img src="images/cache_stretch.gif" width="100%" height="55" border="0"></td>

                <%  } %>
            </tr>
            </table>
        </td>
    <%      } %>
    </tr>
    <tr>
    <%      for (int i=0; i<memberList.size(); i++) {
                com.tangosol.coherence.component.net.member.ClusterMember member
                        = (com.tangosol.coherence.component.net.member.ClusterMember)memberList.get(i);
                boolean isLocalMember = false;
                com.tangosol.net.Cluster cluster =
                        ((com.tangosol.net.Cluster)com.tangosol.net.CacheFactory.ensureCluster());
                String thisUID = cluster.getLocalMember().getUid().toString();
                isLocalMember = thisUID.equals(member.getUid().toString());
    %>

        <td nowrap>
            <table cellpadding="5" cellspacing="0" border="0">
            <tr><td nowrap align="center">
                <font size="-2" face="verdana">
                [<a href="member.jsp?uid=<%= member.getUid().toString() %>"
                <%  if (isLocalMember) { %>
                    ><b><%= member.getAddress().getHostName() %></b></a>]
                <%  } else { %>
                    ><%= member.getAddress().getHostName() %></a>]
                <%  } %>
                <br>
                Joined: <%= SkinUtils.dateToText(request,pageUser,new Date(member.getTimestamp())) %>
                </font>
            </td></tr>
            </table>
        </td>
<%          } // end for each node %>
    </tr>
    </table>

    </td>
    <td width="98%" align="right" valign="top">
        <img src="images/tangosolbutton.gif" width="96" height="42" border="0"
         hspace="10"
         title="Clustering powered by Tangosol's Coherence">
    </td>
</tr>
</table>
<p>

<%  } %>

<%  if (cacheEnabled) { %>
<form action="cache.jsp" method="post">

<font size="-1"><b>Cache Performance Summary</b></font>
<ul>
    <font size="-1">
    Below is a summary of all caches. To adjust the
    size of each cache, click the "Edit Cache Sizes" button below. To clear
    out the contents of a cache, click the checkbox next to the cache you want
    to clear and click "Clear" below.
    <p>
    Effectiveness
    measures how well your cache is working. If the effectiveness is low, that
    usually means that the cache is too small. Caches for which this may be
    the case are flagged below.
    </font>
    <p>
    <table bgcolor="<%= tblBorderColor %>" cellpadding="0" cellspacing="0" border="0">
    <td>
    <table cellpadding="4" cellspacing="1" border="0" width="100%">
    <tr bgcolor="#eeeeee">
    <td align="center"><font size="-2" face="verdana"><b>CACHE TYPE</b></font></td>
    <td align="center"><font size="-2" face="verdana"><b>SIZE</b></font></td>
    <td align="center"><font size="-2" face="verdana"><b>OBJECTS</b></font></td>
    <td align="center"><font size="-2" face="verdana"><b>EFFECTIVENESS</b></font></td>
    <td align="center"><font size="-2" face="verdana"><b>CLEAR CACHE?</b></font></td>
    </tr>

<%    // cache variables
    double memUsed;
    double totalMem;
    double freeMem;
    double hitPercent;
    long hits;
    long misses;

    // Loop through each cache, print out its info
    for (int i=0; i<caches.length; i++) {
        Cache cache = caches[i];
        memUsed = (double)cache.getCacheSize()/(1024*1024);
        totalMem = (double)cache.getMaxCacheSize()/(1024*1024);
        freeMem = 100 - 100*memUsed/totalMem;
        hits = cache.getCacheHits();
        misses = cache.getCacheMisses();
        if (hits + misses == 0) {
            hitPercent = 0.0;
        }
        else {
            hitPercent = 100*(double)hits/(hits+misses);
        }
        boolean lowEffec = (hits > 500 && hitPercent < 85.0 && freeMem < 20.0);
%>
    <tr bgcolor="#ffffff">
        <td><font size="-1"><%= cache.getName() %></font></td>
        <td>
            <font size="-1">
            &nbsp;
            <%= mbFormat.format(totalMem) %> MB,
            <font size="-2" face="verdana">
            <%= percentFormat.format(freeMem)%>% free
            </font>
            &nbsp;
            </font>
        </td>
        <td align="center">
            <font size="-1">
            &nbsp;
            <%= LocaleUtils.getLocalizedNumber(cache.size(), JiveGlobals.getLocale()) %>
            &nbsp;
            </font>
        </td>
        <td>
            <font size="-1">
            &nbsp;
            <%  if (lowEffec) { %>
            <font color="#ff0000"><b><%= percentFormat.format(hitPercent)%>%</b></font>
            <%  } else { %>
            <b><%= percentFormat.format(hitPercent)%>%</b>
            <%  } %>
            (<%= LocaleUtils.getLocalizedNumber(hits, JiveGlobals.getLocale()) %> hits,
            <%= LocaleUtils.getLocalizedNumber(misses, JiveGlobals.getLocale()) %> misses)
            &nbsp;
            </font>
        </td>
        <td align="center">
            <input type="checkbox" name="cacheID" value="<%= i %>">
        </td>
    </tr>
<%    } %>
    <tr bgcolor="#ffffff">
        <td>&nbsp;</td>
        <td><input type="submit" name="editSizes" value="Edit Cache Sizes"></td>
        <td colspan="2">&nbsp;</td>
        <td align="center">
            <input type="submit" name="clearCache" value="Clear">
        </td>
    </tr>
    </table>
    </td>
    </table>
</ul>
</form><p>
<%  } %>

<font size="-1"><b>Cache Status</b></font>
<ul>
    <font size="-1">
    You can enable or disable caching in the Jive system by using the form
    below. Disabling cache will severely degrade performance.
    </font><p>
    <form action="cache.jsp">
    <input type="hidden" name="doCache" value="true">
    <table bgcolor="<%= tblBorderColor %>" cellpadding="0" cellspacing="0" border="0" width="300">
    <td>
    <table bgcolor="<%= tblBorderColor %>" cellpadding="3" cellspacing="1" border="0" width="100%">
    <tr bgcolor="#ffffff">
    <td align="center"<%= (cacheEnabled)?" bgcolor=\"#99cc99\"":"" %>>
        <font size="-1">
        <input type="radio" name="cacheEnabled" value="true" id="rb01"
         <%= (cacheEnabled)?"checked":"" %>>
        <label for="rb01"><%= (cacheEnabled)?"<b>On</b>":"On" %></label>
        </font>
    </td>
    <td align="center"<%= (!cacheEnabled)?" bgcolor=\"#cc6666\"":"" %>>
        <font size="-1">
        <input type="radio" name="cacheEnabled" value="false" id="rb02"
         <%= (!cacheEnabled)?"checked":"" %>>
        <label for="rb02"><%= (!cacheEnabled)?"<b>Off</b>":"Off" %></label>
        </font>
    </td>
    <td align="center">
        <font size="-1"><input type="submit" value="Update"></font>
    </td>
    </tr>
    </table>
    </td>
    </table>
    </form>
</ul>
<p>

<%  if (cacheEnabled && Version.EDITION == Version.Edition.ENTERPRISE) { %>
<font size="-1"><b>Short-term Query Cache</b></font>
<ul>
    <font size="-1">
    Prevents cache expirations of the query cache from happening more than once every
    <%= (stqcLifetime/1000L) %> seconds.
    This is useful for sites with extreme amounts of traffic. The ramification to using the
    short-term query cache is that new content won't appear for <%= (stqcLifetime/1000L) %> seconds
    after it's posted.
    </font>
    <p></p>
    <form action="cache.jsp">
    <table bgcolor="<%= tblBorderColor %>" cellpadding="0" cellspacing="0" border="0" width="300">
    <td>
    <table bgcolor="<%= tblBorderColor %>" cellpadding="3" cellspacing="1" border="0" width="100%">
    <tr bgcolor="#ffffff">
    <td align="center"<%= (stqcEnabled)?" bgcolor=\"#99cc99\"":"" %>>
        <font size="-1">
        <input type="radio" name="stqcEnabled" value="true" id="rb03"
         <%= (stqcEnabled)?"checked":"" %>>
        <label for="rb03"><%= (stqcEnabled)?"<b>On</b>":"On" %></label>
        </font>
    </td>
    <td align="center"<%= (!stqcEnabled)?" bgcolor=\"#cc6666\"":"" %>>
        <font size="-1">
        <input type="radio" name="stqcEnabled" value="false" id="rb04"
         <%= (!stqcEnabled)?"checked":"" %>>
        <label for="rb04"><%= (!stqcEnabled)?"<b>Off</b>":"Off" %></label>
        </font>
    </td>
    <td align="center">
        <input type="submit" value="Update" name="setStqcEnabled">
    </td>
    </tr>
    </table>
    </td>
    </table>
    </form>
    <%  if (stqcEnabled && Version.EDITION == Version.Edition.ENTERPRISE) { %>
        <form action="cache.jsp">
        <table cellpadding="2" cellspacing="0" border="0">
        <tr>
            <td rowspan="3" valign="top">
                <font size="-1">
                Cache Object Lifetime:
                </font>
            </td>
            <td><input type="radio" name="stqcLifetime" value="5000"<%= ((stqcLifetime==5000)?" checked":"") %> id="st01"></td>
            <td>
                <font size="-1">
                <label for="st01">5 seconds (default)</label>
                </font>
            </td>
        </tr>
        <tr>
            <td><input type="radio" name="stqcLifetime" value="10000"<%= ((stqcLifetime==10000)?" checked":"") %> id="st02"></td>
            <td>
                <font size="-1">
                <label for="st02">10 seconds</label>
                </font>
            </td>
        </tr>
        <tr>
            <td>&nbsp;</td>
            <td>
                <input type="submit" name="saveLifetime" value="Save">
            </td>
        </tr>
        </table>
        </form>
    <%  } %>
</ul>
<%  } %>

<%  if (cacheEnabled && Version.EDITION == Version.Edition.ENTERPRISE) { %>
<font size="-1"><b>Clustering</b></font>
<ul>
    <font size="-1">
    You can enable or disable clustered caching in the Jive system by using the
    form below. (<b>Note</b>, enabling or disabling clustering requires an
    appserver restart.)
    <%  if (promptForRestart) { %>
    <p>
    <i>
    Note, because your changed your cache policy, you must restart your appserver.
    The clustering status below will be incorrect until you restart.
    </i>
    <%  } %>
    </font><p>
    <form action="cache.jsp" onsubmit="return confirm('Changing your caching policy requires an appserver restart.\n\nAre you sure you want to continue?');">
    <table bgcolor="<%= tblBorderColor %>" cellpadding="0" cellspacing="0" border="0" width="300">
    <td>
    <table bgcolor="<%= tblBorderColor %>" cellpadding="3" cellspacing="1" border="0" width="100%">
    <tr bgcolor="#ffffff">
    <td align="center"<%= (clusteringEnabled)?" bgcolor=\"#99cc99\"":"" %>>
        <font size="-1">
        <input type="radio" name="clusteringEnabled" value="true" id="rb03"
         <%= (clusteringEnabled)?"checked":"" %>>
        <label for="rb03"><%= (clusteringEnabled)?"<b>On</b>":"On" %></label>
        </font>
    </td>
    <td align="center"<%= (!clusteringEnabled)?" bgcolor=\"#cc6666\"":"" %>>
        <font size="-1">
        <input type="radio" name="clusteringEnabled" value="false" id="rb04"
         <%= (!clusteringEnabled)?"checked":"" %>>
        <label for="rb04"><%= (!clusteringEnabled)?"<b>Off</b>":"Off" %></label>
        </font>
    </td>
    <td align="center">
        <input type="submit" value="Update" name="setClusteringEnabled">
    </td>
    </tr>
    </table>
    </td>
    </table>
    </form>
    <p>
</ul>
<%  } %>

<%  if (cacheEnabled) { %>
<font size="-1"><b>Warmup Cache</b></font>
<ul>
    <font size="-1">
    The cache warmup process will load your caches with the data that is most likely to
    accessed by users. This action is useful to perform when first starting a server,
    or after flushing the cache. However, it will put a heavy load on your database.
    <p>
    <form action="cache.jsp">
    <input type="submit" name="warmupCache" value="Warmup Cache">
    </form>
    </font>
</ul>
<p>
<%  } %>

<font size="-1"><b>Java VM Memory</b></font>
<ul>

<%    // The java runtime
    Runtime runtime = Runtime.getRuntime();

    double freeMemory = (double)runtime.freeMemory()/(1024*1024);
    double totalMemory = (double)runtime.totalMemory()/(1024*1024);
    double usedMemory = totalMemory - freeMemory;
    double percentFree = ((double)freeMemory/(double)totalMemory)*100.0;
    int free = 100-(int)Math.round(percentFree);
%>
    <table border=0>
    <tr><td><font size="-1">Used Memory:</font></td>
        <td><font size="-1"><%= mbFormat.format(usedMemory) %> MB</font></td>
    </tr>
    <tr><td><font size="-1">Total Memory:</font></td>
        <td><font size="-1"><%= mbFormat.format(totalMemory) %> MB</font></td>
    </tr>
    </table>
    <br>
    <table border=0><td>
    <table bgcolor="#000000" cellpadding="1" cellspacing="0" border="0" width="200" align=left>
    <td>
    <table bgcolor="#000000" cellpadding="1" cellspacing="1" border="0" width="100%">
<%    for (int i=0; i<NUM_BLOCKS; i++) {
        if ((i*(100/NUM_BLOCKS)) < free) {
    %>
        <td bgcolor="#00ff00" width="<%= (100/NUM_BLOCKS) %>%"><img src="images/blank.gif" width="1" height="15" border="0"></td>
<%        } else { %>
        <td bgcolor="#006600" width="<%= (100/NUM_BLOCKS) %>%"><img src="images/blank.gif" width="1" height="15" border="0"></td>
<%        }
    }
%>
    </table>
    </td>
    </table></td><td>
        <font size="-1">
        &nbsp;<b><%= percentFormat.format(percentFree) %>% free</b>
        </font>
    </td></table>
</ul>

<%    // Destroy the runtime reference
    runtime = null;
%>

<%@ include file="footer.jsp" %>

