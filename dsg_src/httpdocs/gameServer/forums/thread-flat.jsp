<%
/**
 * $RCSfile: thread-flat.jsp,v $
 * $Revision: 1.27.4.1 $
 * $Date: 2003/02/04 00:17:50 $
 */
%>

<%@ page import="com.jivesoftware.forum.action.ForumThreadAction,
                 com.jivesoftware.forum.util.SkinUtils,
                 java.util.Iterator,
                 com.jivesoftware.base.User,
                 com.jivesoftware.forum.action.util.Page,
                 com.jivesoftware.forum.*,
                 com.jivesoftware.forum.action.util.Guest,
                 com.jivesoftware.util.ByteFormat,
                 java.text.*,
		 org.pente.gameServer.client.web.*,
                  org.pente.gameServer.core.*,
		 org.pente.gameServer.server.*"
%>
<%@ page import="java.util.List" %>

<%@ include file="global.jsp" %>

<%@ taglib uri="webwork" prefix="ww" %>
<%@ taglib uri="jivetags" prefix="jive" %>

<%! private static final NumberFormat nf = NumberFormat.getNumberInstance(); %>
<%  // Get the action and other variables for this view.
    ForumThreadAction action = (ForumThreadAction)getAction(request);
    ForumFactory forumFactory = action.getForumFactory();
    Forum forum = action.getForum();
    ForumThread thread = action.getThread();

%>

<jsp:include page="header.jsp" flush="true" />

<%
int views = 0;
{
	// update the views using dweebo's id to avoid unauthorizedexception
	AuthToken auth = new DSGAuthToken(22000000000002L);
	ForumFactory ff = ForumFactory.getInstance(auth);
	ForumThread t = ff.getForum(forum.getID()).getThread(thread.getID());
	String viewsStr = t.getProperty("views");
	if (viewsStr != null) {
	    try { views = Integer.parseInt(viewsStr); } catch (NumberFormatException n) {}
	}
	
	if (request.getAttribute("spider") == null) {
	    views++;
	    t.setProperty("views", Integer.toString(views));
	}
}
%>

<table cellpadding="0" cellspacing="0" border="0" width="100%">
<tr valign="top">
    <td width="98%">

        <%-- Breadcrumbs (customizable via the admin tool) --%>

        <jsp:include page="breadcrumbs.jsp" flush="true" />

        <%-- Forum name and brief info about the forum --%>

        <p>
        <span class="jive-page-title">
        <%-- Topic: [topic name] --%>
        <jive:i18n key="global.topic" /><jive:i18n key="global.colon" />
        <%= thread.getName() %>
        </span>
        <br>
        <%-- Replies: [reply count] --%>
        <jive:i18n key="global.replies" /><jive:i18n key="global.colon" />
        <%= action.getNumberFormat().format(thread.getMessageCount()-1) %> &nbsp;

        Views: <%= nf.format(views) %> &nbsp;
        
        <%-- Pages: [page count] --%>
        <jive:i18n key="global.pages" /><jive:i18n key="global.colon" />
        <%= action.getNumberFormat().format(action.getNumPages()) %> &nbsp;

        <%-- Last Post --%>
        <%  if (thread.getMessageCount() > 1) {
                ForumMessage lastPost = SkinUtils.getLastPost(thread);
                if (lastPost != null) {
        %>
                <%-- Last Post: --%>
                <jive:i18n key="global.last_post" /><jive:i18n key="global.colon" />
                <%= action.getDateFormat().format(thread.getModificationDate()) %>
    
                <%-- by: [username, linked to the last post] --%>
                <jive:i18n key="global.by" /><jive:i18n key="global.colon" />
    
                <%  if (lastPost.getUser() != null) { %>
    
                    <a href="thread.jspa?forumID=<%= forum.getID() %>&threadID=<%= thread.getID() %>&messageID=<%= lastPost.getID() %>#<%= lastPost.getID() %>"
                     ><%= lastPost.getUser().getUsername() %></a>
    
                <%  } else {
                        Guest guest = new Guest();
                        guest.setMessage(lastPost);
                %>
                    <span class="jive-guest">
                <nobr>
                    <a href="thread.jspa?forumID=<%= forum.getID() %>&threadID=<%= thread.getID() %>&messageID=<%= lastPost.getID() %>#<%= lastPost.getID() %>"
                     ><%= guest.getDisplay() %> &raquo;</a>
                </nobr>
                    </span>
    
        <%          }
                }
            }
        %>
        </p>

        <%-- print out a message if this thread is archived --%>
        <%  if (action.isArchived()) { %>

            <table class="jive-info-message" cellpadding="3" cellspacing="0" border="0" width="350">
            <tr valign="top">
                <td width="1%"><img src="images/archived-16x16.gif" width="16" height="16" border="0"></td>
                <td width="99%">
                    <span class="jive-info-text">
                    <jive:i18n key="thread.topic_archived_description" />
                    </span>
                </td>
            </tr>
            </table>
            <br><br>

        <%  } %>

        <%-- print out a message if this thread is locked --%>
        <%  if (action.isLocked()) { %>

            <table class="jive-info-message" cellpadding="3" cellspacing="0" border="0" width="350">
            <tr valign="top">
                <td width="1%"><img src="images/lock-16x16.gif" width="16" height="16" border="0"></td>
                <td width="99%">
                    <span class="jive-info-text">
                    <jive:i18n key="thread.topic_locked_description" />
                    </span>
                </td>
            </tr>
            </table>
            <br><br>

        <%  } %>

    </td>
    <td width="1%"><img src="images/blank.gif" width="10" height="1" border="0"></td>
    <td width="1%">

        <%@ include file="accountbox.jsp" %>

    </td>
</tr>
</table>

<jive:property if="watches.enabled">

    <ww:if test="pageUser">

        <%  if (action.getForumFactory().getWatchManager().isWatched(action.getPageUser(), thread)) { %>

            <table class="jive-info-message" cellpadding="3" cellspacing="0" border="0" width="100%">
            <tr valign="top">
                <td width="1%"><img src="images/info-16x16.gif" width="16" height="16" border="0"></td>
                <td width="99%">

                    <span class="jive-info-text">

                    <%--
                        You are watching this topic. To remove this watch, click "Stop Watching Topic"
                        below.
                    --%>
                    <jive:i18n key="thread.watch_description" />

                    <%-- Watch Options --%>
                    (<a href="editwatches!default.jspa"><jive:i18n key="global.watch_options" /></a>)

                    </span>

                </td>
            </tr>
            </table>
            <br>

        <%  } %>

    </ww:if>

</jive:property>

<%@ include file="thread-buttons.jsp" %>

<br>

<table cellpadding="3" cellspacing="2" border="0" width="100%">
<tr>
    <td width="1%" nowrap>
        <a href="forum.jspa?forumID=<%= forum.getID() %>"
         ><img src="images/back-to-16x16.gif" width="16" height="16" border="0"></a>
    </td>
    <td width="1%" nowrap>
        <%-- Back to Topic List --%>
        <span class="jive-button-label">
        <a href="forum.jspa?forumID=<%= forum.getID() %>"
         ><jive:i18n key="thread.back_to_topic" /></a>
        </span>
    </td>
    <td align="right" width="98%">

        <jive:cache id="nextPrev">

            <%  if (action.getHasPreviousThread() || action.getHasNextThread()) { %>

                <%-- Topics: --%>
                <jive:i18n key="global.topics" /><jive:i18n key="global.colon" />
                [

                <%  if (action.getHasPreviousThread()) { %>

                    <%-- Previous --%>
                    <a href="thread.jspa?forumID=<%= action.getPreviousThread().getForum().getID() %>&threadID=<%= action.getPreviousThread().getID() %>&tstart=<%= action.getPrevTstart() %>"
                     ><jive:i18n key="global.previous" /></a>

                <%  } else { %>

                    <%-- Previous --%>
                    <jive:i18n key="global.previous" />

                <%  } %>

                |

                <%  if (action.getHasNextThread()) { %>

                    <%-- Next --%>
                    <a href="thread.jspa?forumID=<%= action.getNextThread().getForum().getID() %>&threadID=<%= action.getNextThread().getID() %>&tstart=<%= action.getNextTstart() %>"
                     ><jive:i18n key="global.next" /></a>

                <%  } else { %>

                    <%-- Next --%>
                    <jive:i18n key="global.next" />

                <%  } %>

                ]

            <%  } %>

        </jive:cache>

    </td>
</tr>
</table>

<span class="jive-message-list">
<table cellpadding="3" cellspacing="0" border="0" width="100%">
<tr>
    <th colspan="2">

        <jive:cache id="paginator">

            <%-- Replies: --%>
            <jive:i18n key="global.replies" /><jive:i18n key="global.colon" />
            <%= action.getNumberFormat().format(thread.getMessageCount()-1) %> &nbsp;

            Views: <%= nf.format(views) %> &nbsp;

            <%-- Pages: --%>
            <jive:i18n key="global.pages" /><jive:i18n key="global.colon" />
            <%= action.getNumberFormat().format(action.getNumPages()) %> &nbsp;

            <%  if (action.getNumPages() > 1) { %>

                <span class="jive-paginator">
                [
                <%  if (action.getPreviousPage()) { %>

                    <%-- Previous --%>
                    <a href="thread.jspa?forumID=<%= forum.getID() %>&threadID=<%= thread.getID() %>&start=<%= action.getPreviousPageStart() %>&tstart=<%= action.getTstart() %>"
                     ><jive:i18n key="global.previous" /></a> |

                <%  } %>

                <%  Page[] pages = action.getPages();
                    for (int i=0; i<pages.length; i++) {
                %>
                    <%  if (pages[i] == null) { %>

                        <jive:i18n key="global.elipse" />

                    <%  } else { %>

                        <a href="thread.jspa?forumID=<%= forum.getID() %>&threadID=<%= thread.getID() %>&start=<%= pages[i].getStart() %>&tstart=<%= action.getTstart() %>"
                         class="<%= ((action.getStart()==pages[i].getStart())?"jive-current":"") %>"
                         ><%= pages[i].getNumber() %></a>

                     <% } %>

                <%  } %>

                <%  if (action.getNextPage()) { %>

                    <%-- Next --%>
                    | <a href="thread.jspa?forumID=<%= forum.getID() %>&threadID=<%= thread.getID() %>&start=<%= action.getNextPageStart() %>&tstart=<%= action.getTstart() %>"
                     ><jive:i18n key="global.next" /></a>

                <%  } %>
                ]
                </span>

            <%  } %>

        </jive:cache>

    </th>
</tr>
</table>


<%
String nm = (String) request.getAttribute("name");
DSGPlayerData data = null;
    boolean personalizeAds = false;
if (nm != null) {
Resources resources = (Resources)
   application.getAttribute(Resources.class.getName());
DSGPlayerStorer dsgPlayerStorer = resources.getDsgPlayerStorer();
  data = dsgPlayerStorer.loadPlayer(nm);
    if (data != null) {
        List prefs = dsgPlayerStorer.loadPlayerPreferences(data.getPlayerID());
        for (Iterator it = prefs.iterator(); it.hasNext();) {
            DSGPlayerPreference p = (DSGPlayerPreference) it.next();
            if (p.getName().equals("personalizeAds")) {
                personalizeAds = ((Boolean) p.getValue()).booleanValue();
            }
        }
    }
}
%>
<%  //
    // Main loop for the messages on this page
    //
    int status = 0;
    int count = 0;
    for (Iterator messages=action.getMessages(); messages.hasNext(); count++ ) {
        ForumMessage message = (ForumMessage)messages.next();
%>
    <span class="jive-message-list">

    <%@ include file="thread-messagebox.jsp" %>

    </span>

    <%-- Print an extra space if there are replies and this is the root message --%>
    <%  if (thread.getMessageCount()-1 > 0 && message.getID() == thread.getRootMessage().getID()) {
    %>
        <br>
    <%  } %>
    
<% if (count % 15 == 0 && (data == null || data.showAds())) { 
%>
    

<center>
    <script>(adsbygoogle=window.adsbygoogle||[]).requestNonPersonalizedAds=<%=(personalizeAds?"0":"1")%></script>
    <div id = "senseReplace" style="width:728px;height:90px;" top="50%"> </div>
    <%@ include file="../728x90ad.jsp" %>
    <script type="text/javascript">
        addLoadEvent(sensePage);
    </script>
    <br style="clear:both">
</center>

<% } %>

<%  } %>

<span class="jive-message-list-footer">
<table cellpadding="3" cellspacing="0" border="0" width="100%">
<tr>
    <td colspan="2">

        <table cellpadding="0" cellspacing="0" border="0" width="100%">
        <tr>
            <td width="1%" nowrap>

                <span class="jive-paginator-bottom">
                <jive:cache id="paginator" />
                </span>

            </td>
            <td width="98%" align="center">
                <table cellpadding="3" cellspacing="2" border="0">
                <tr>
                    <td>
                        <a href="forum.jspa?forumID=<%= forum.getID() %>"
                         ><img src="images/back-to-16x16.gif" width="16" height="16" border="0"></a>
                    <td>
                        <%-- Back to Topic List --%>
                        <span class="jive-button-label">
                        <a href="forum.jspa?forumID=<%= forum.getID() %>"
                         ><jive:i18n key="thread.back_to_topic" /></a>
                        </span>
                    </td>
                </tr>
                </table>
            </td>
            <td width="1%" nowrap>

                <jive:cache id="nextPrev" />

            </td>
        </tr>
        </table>

    </td>
</tr>
</table>
</span>

<jsp:include page="footer.jsp" flush="true" />
