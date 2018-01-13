<%
/**
 * $RCSfile: viewforum.jsp,v $
 * $Revision: 1.33 $
 * $Date: 2003/01/08 22:51:24 $
 */
%>

<%@ page import="com.jivesoftware.forum.action.ForumAction,
                 com.jivesoftware.forum.Forum,
                 com.jivesoftware.forum.ForumMessage,
                 com.jivesoftware.forum.ForumPermissions,
                 com.jivesoftware.forum.util.SkinUtils,
                 java.util.Iterator,
                 com.jivesoftware.forum.ForumThread,
                 com.jivesoftware.forum.action.util.Page,
                 com.jivesoftware.webwork.util.ValueStack,
                 com.jivesoftware.forum.action.util.Guest,
                 java.text.*"
%>

<%@ include file="global.jsp" %>

<%@ taglib uri="webwork" prefix="ww" %>
<%@ taglib uri="jivetags" prefix="jive" %>

<%! private static final NumberFormat nf = NumberFormat.getNumberInstance(); %>
<%  // Get the action for this view.
    ForumAction action = (ForumAction)getAction(request);
    Forum forum = action.getForum();
%>

<jsp:include page="header.jsp" flush="true" />

<table cellpadding="0" cellspacing="0" border="0" width="100%">
<tr valign="top"> 
    <td width="98%">

        <%-- Breadcrumbs (customizable via the admin tool) --%>

        <jsp:include page="breadcrumbs.jsp" flush="true" />

        <%-- Forum name and brief info about the forum --%>

        <p>
        <span class="jive-page-title">
        <%-- Forum: [forum name] --%>
        <jive:i18n key="global.forum" /><jive:i18n key="global.colon" />
        <%= forum.getName() %>
        </span>
        <br>
        <%-- Messages: [message count] --%>
        <jive:i18n key="global.messages" /><jive:i18n key="global.colon" />
            <%= action.getNumberFormat().format(forum.getMessageCount()) %> &nbsp;
        <%-- Topics: [topic count] --%>
        <jive:i18n key="global.topics" /><jive:i18n key="global.colon" />
            <%= action.getNumberFormat().format(forum.getThreadCount()) %> &nbsp;

        <%-- show last post info if there are posts in this forum --%>
        <%  if (forum.getMessageCount() > 0) { %>

            <%-- Last Post: [forum last modified date] --%>
            <jive:i18n key="global.last_post" /><jive:i18n key="global.colon" />
                <%= action.getDateFormat().format(forum.getModificationDate()) %>,

            <%-- by: [username, linked to the last post] --%>
            <%  ForumMessage lastPost = SkinUtils.getLastPost(forum); %>

            <%  if (lastPost != null) { %>

                <jive:i18n key="global.by" /><jive:i18n key="global.colon" />
                <%  if (lastPost.getUser() != null) { %>

                    <a href="thread.jspa?forumID=<%= lastPost.getForumThread().getForum().getID() %>&threadID=<%= lastPost.getForumThread().getID() %>&messageID=<%= lastPost.getID() %>#<%= lastPost.getID() %>"
                     ><%= lastPost.getUser().getUsername() %> &raquo;</a>

                <%  } else {
                        Guest guest = new Guest();
                        guest.setMessage(lastPost);
                %>
                    <span class="jive-guest">
                    <a href="thread.jspa?forumID=<%= lastPost.getForumThread().getForum().getID() %>&threadID=<%= lastPost.getForumThread().getID() %>&messageID=<%= lastPost.getID() %>#<%= lastPost.getID() %>"
                     ><%= guest.getDisplay() %> &raquo;</a>
                    </span>

                <%  } %>

            <%  } %>

        <%  } %>
        </p>

        <%-- [forum description] --%>
        <%  if (forum.getDescription() != null) { %>
            <span class="jive-description">
            <%= forum.getDescription() %>
            </span>
        <%  } %>

    </td>
    <td width="1%"><img src="images/blank.gif" width="10" height="1" border="0"></td>
    <td width="1%">

        <%@ include file="accountbox.jsp" %>

		<span class="jive-account-box">
		<table class="jive-box" cellpadding="3" cellspacing="0" border="0" width="200">
		  <tr>
		    <td width="1%"><a href="/rss.xml?forumID=<%= forum.getID() %>"><img src="/gameServer/images/feed.png" border="0" width="16" height="16"></a></td>
            <td width="99%">
             <a href="/rss.xml?forumID=<%= forum.getID() %>">RSS Feed</a>
            </td>
          </tr>
		</table>
		</span>
        
    </td>
</tr>
</table>

<br>

<%-- Print info messages if they exist --%>
<ww:if test="infoMessages/hasNext == true">
    <%@ include file="info-messages.jsp" %>
    <br><br>
</ww:if>

<jive:property if="watches.enabled">

    <ww:if test="pageUser">

        <%  if (action.getForumFactory().getWatchManager().isWatched(action.getPageUser(), forum)) { %>

            <table class="jive-info-message" cellpadding="3" cellspacing="0" border="0" width="100%">
            <tr valign="top">
                <td width="1%"><img src="images/info-16x16.gif" width="16" height="16" border="0"></td>
                <td width="99%">

                    <span class="jive-info-text">

                    <%-- You are watching this forum. To remove this watch, click "Stop Watching Forum" below. --%>
                    <jive:i18n key="forum.watching_forum" />

                    <%-- More watch options --%>
                    <a href="editwatches!default.jspa"><jive:i18n key="global.watch_options" /></a>

                    </span>

                </td>
            </tr>
            </table>
            <br>

        <%  } %>

    </ww:if>

</jive:property>

<span class="jive-button">
<table cellpadding="0" cellspacing="0" border="0">
<tr>
    <% if (forum.isAuthorized(ForumPermissions.CREATE_MESSAGE)) { %>
    <td>
        <table cellpadding="3" cellspacing="0" border="0">
        <tr>
            <td><a href="post!default.jspa?forumID=<%= forum.getID() %>"><img src="images/post-16x16.gif" width="16" height="16" border="0"></a></td>
            <td>
                <span class="jive-button-label">
                <%-- Post New Topic --%>
                <a href="post!default.jspa?forumID=<%= forum.getID() %>"
                 ><jive:i18n key="global.post_new_topic" /></a>
                </span>
            </td>
        </tr>
        </table>
    </td>
	<% } %>
    <td>
        <table cellpadding="3" cellspacing="2" border="0">
        <tr>
            <td><a href="search!default.jspa?forumID=<%= forum.getID() %>"><img src="images/search-16x16.gif" width="16" height="16" border="0"></a></td>
            <td>
                <span class="jive-button-label">
                <%-- Search Forum --%>
                <a href="search!default.jspa?forumID=<%= forum.getID() %>"
                 ><jive:i18n key="global.search_forum" /></a>
                </span>
            </td>
        </tr>
        </table>
    </td>
    <ww:if test="pageUser">
        <jive:property if="watches.enabled">
            <td>
                <table cellpadding="3" cellspacing="2" border="0">
                <tr>
                    <td><a href="watches.jspa?forumID=<%= forum.getID() %>"><img src="images/watch-16x16.gif" width="16" height="16" border="0"></a></td>
                    <td>
                        <span class="jive-button-label">

                        <%  if (action.getForumFactory().getWatchManager().isWatched(action.getPageUser(), forum)) { %>

                            <%-- stop watching forum --%>
                            <a href="watches!remove.jspa?forumID=<%= forum.getID() %>"
                             ><jive:i18n key="forum.stop_watch" /></a>

                        <%  } else { %>

                            <%-- Watch Forum --%>
                            <a href="watches!add.jspa?forumID=<%= forum.getID() %>"
                             ><jive:i18n key="global.watch_forum" /></a>

                        <%  } %>
                        </span>
                    </td>
                </tr>
                </table>
            </td>
        </jive:property>
    </ww:if>
    <td>
        <table cellpadding="3" cellspacing="2" border="0">
        <tr>
            <td><a href="index.jspa"><img src="images/back-to-16x16.gif" width="16" height="16" border="0"></a></td>
            <td>
                <span class="jive-button-label">
                <%-- Go Back to Forum List --%>
                <a href="index.jspa"
                 ><jive:i18n key="global.go_back_to_forum_list" /></a>
                </span>
            </td>
        </tr>
        </table>
    </td>
</tr>
</table>
</span>

<br>

<% if (forum.getID() == 27) { %>

 <div style="font-family:Verdana, Arial, Helvetica, sans-serif;
     margin-top:10px;margin-bottom:10px;
     background:#fffbcc;
     border:1px solid #e6db55;
     padding:5px;
     font-weight:bold;
     width:500px;">

    Learn how to <a href="/gameServer/postanalysis.jsp">post games for analysis</a> in the forums.

    </div>


<% } %>

<jive:cache id="paginator">

    <table cellpadding="3" cellspacing="0" border="0" width="100%">
    <tr valign="top">
        <td>

            <%-- Pages: --%>
            <jive:i18n key="global.pages" /><jive:i18n key="global.colon" />
            <%= action.getNumPages() %>

            <%  if (action.getNumPages() > 1) { %>

                <span class="jive-paginator">
                [
                <%  if (action.getPreviousPage()) { %>

                    <%-- Previous --%>
                    <a href="forum.jspa?forumID=<%= forum.getID() %>&start=<%= action.getPreviousPageStart() %>"
                     ><jive:i18n key="global.previous" /></a> |

                <%  } %>

                <%  Page[] pages = action.getPages();
                    for (int i=0; i<pages.length; i++) {
                %>
                    <%  if (pages[i] == null) { %>

                        <jive:i18n key="global.elipse" />

                    <%  } else { %>

                        <a href="forum.jspa?forumID=<%= forum.getID() %>&start=<%= pages[i].getStart() %>"
                         class="<%= ((action.getStart()==pages[i].getStart())?"jive-current":"") %>"
                         ><%= pages[i].getNumber() %></a>

                     <% } %>

                <%  } %>

                <%  if (action.getNextPage()) { %>

                    <%-- Next --%>
                    | <a href="forum.jspa?forumID=<%= forum.getID() %>&start=<%= action.getNextPageStart() %>"
                     ><jive:i18n key="global.next" /></a>

                <%  } %>
                ]
                </span>

            <%  } %>

        </td>
    </tr>
    </table>

</jive:cache>

<span id="jive-topic-list">
<table class="jive-list" cellpadding="3" cellspacing="2" width="100%">
<tr>
    <th class="jive-forum-name" colspan="2">
        <%-- Topic --%>
        <jive:i18n key="global.topic" />
    </th>
    <th class="jive-author">
        <%-- Author --%>
        <jive:i18n key="global.author" />
    </th>
    <th class="jive-counts">
        <%-- Replies --%>
        <jive:i18n key="global.replies" />
    </th>
    <th class="jive-counts">
        Views
    </th>
    <th class="jive-date" nowrap>
        <%-- Last Post --%>
        <jive:i18n key="global.last_post" />
    </th>
</tr>

<%-- Print all forums in the current category --%>

<%  int status = 0;
    for (Iterator threads=action.getThreads(); threads.hasNext(); ) {
        ForumThread thread = (ForumThread)threads.next();
%>
    <tr class="<%= ((status++%2==1)?"jive-odd":"jive-even") %>" valign="top">
        <td class="jive-bullet" width="1%">

            <%  if (action.isUnread(thread)) { %>

                <img src="images/unread.gif" width="9" height="9" border="0">

            <%  } else if (action.isUpdated(thread)) { %>

                <img src="images/updated.gif" width="9" height="9" border="0">

            <%  } else { %>

                <img src="images/read.gif" width="9" height="9" border="0">

            <%  } %>

        </td>
        <td class="jive-topic-name" width="96%">

            <a href="thread.jspa?forumID=<%= forum.getID() %>&threadID=<%= thread.getID() %>&tstart=<%= action.getStart() %>"
             ><%= thread.getName() %></a>

        </td>
        <td class="jive-author" width="1%" nowrap>

            <% if (thread.getRootMessage().getUser() != null) { %>

                <a href="<%= request.getContextPath() %>/gameServer/profile?viewName=<%= ((thread.getRootMessage().getUser().getUsername() != null) ? thread.getRootMessage().getUser().getUsername() : "Deleted user") %>"
                 ><%= ((thread.getRootMessage().getUser().getUsername() != null) ? thread.getRootMessage().getUser().getUsername() : "Deleted user") %></a>

            <%  } else {
                    Guest guest = new Guest();
                    guest.setMessage(thread.getRootMessage());
            %>
                <span class="jive-guest">
                <%  if (guest.getEmail() != null) { %>

                    <a href="mailto:<%= guest.getEmail() %>"><%= guest.getDisplay() %></a>

                <%  } else { %>

                    <%= guest.getDisplay() %>

                <%  } %>
                </span>

            <%  } %>

        </td>
        <td class="jive-counts" width="1%">

            <%= (thread.getMessageCount()-1) %>

        </td>
        <td class="jive-counts" width="1%">
        <%
            String viewsStr = thread.getProperty("views");
		    int views = 0;
		    if (viewsStr != null) {
		        try { views = Integer.parseInt(viewsStr); } catch (NumberFormatException n) {}
		    }
              %>
              <%= nf.format(views) %>

        </td>
        <td class="jive-date" width="1%" nowrap>

            <table cellpadding="0" cellspacing="0" border="0" width="100%">
            <tr>
                <td width="99%" nowrap>

                    <%-- Last modified (last post) date --%>
                    <%= action.getDateFormat().format(thread.getModificationDate()) %>

                </td>
                <td width="1%" nowrap align="right">

                    <%-- show the last post link if that feature is enabled: --%>
                    <jive:property if="skin.default.showLastPostLink">

                        <%  ForumMessage lastPost = SkinUtils.getLastPost(thread); %>

                        <% if (lastPost != null) { %>

                            <span class="jive-last-post">
                            <%-- by: {LAST_POST} --%>
                            <jive:i18n key="global.last_post_by">
                                <jive:arg>

                                    <%  if (lastPost.getUser() != null) { %>

                                        <a href="thread.jspa?forumID=<%= lastPost.getForumThread().getForum().getID() %>&threadID=<%= lastPost.getForumThread().getID() %>&messageID=<%= lastPost.getID() %>#<%= lastPost.getID() %>"
                                         ><%= ((lastPost.getUser().getUsername() != null) ? lastPost.getUser().getUsername() : "Deleted user") %> &raquo;</a>

                                    <%  } else {
                                            Guest guest = new Guest();
                                            guest.setMessage(lastPost);
                                    %>
                                        <span class="jive-guest">
                                        <a href="thread.jspa?forumID=<%= lastPost.getForumThread().getForum().getID() %>&threadID=<%= lastPost.getForumThread().getID() %>&messageID=<%= lastPost.getID() %>#<%= lastPost.getID() %>"
                                         ><%= guest.getDisplay() %> &raquo;</a>
                                        </span>

                                    <%  } %>

                                </jive:arg>
                            </jive:i18n>

                            </span>

                        <%  } %>

                    </jive:property>

                </td>
            </tr>
            </table>

        </td>
    </tr>

<%  } %>

</table>
</span>

<jive:cache id="paginator" />

<br>

<table cellpadding="3" cellspacing="0" border="0">
<tr>
    <td><img src="images/unread.gif" width="9" height="9" border="0"></td>
    <td>
        <span class="jive-description">
        <%-- Denotes unread messages since your last visit. --%>
        <jive:i18n key="global.unread_messages_explained" />
        </span>
    </td>
</tr>
<tr>
    <td><img src="images/updated.gif" width="9" height="9" border="0"></td>
    <td>
        <span class="jive-description">
        <%-- Denotes updated messages since your last visit. --%>
        <jive:i18n key="global.updated_messages_explained" />
        </span>
    </td>
</tr>
</table>

<jsp:include page="footer.jsp" flush="true" />