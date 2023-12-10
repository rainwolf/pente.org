<%
   /**
    *	$RCSfile: forumContent.jsp,v $
    *	$Revision: 1.5 $
    *	$Date: 2002/11/22 22:35:08 $
    */
%>

<%@ page import="java.util.*,
                 java.text.SimpleDateFormat,
                 com.jivesoftware.forum.*,
                 com.jivesoftware.forum.util.*,
                 com.jivesoftware.util.*"
         errorPage="error.jsp"
%>

<%! // Global variables, methods, etc

   // More methods at the bottom of the page

   // default range & starting point for the thread iterators
   private final static int START = 0;
   private final static int RANGE = 20;
%>

<%@ include file="global.jsp" %>

<% // Permission check
   if (!isSystemAdmin && !isForumAdmin && !isModerator) {
      throw new UnauthorizedException("You don't have admin privileges to perform this operation.");
   }

   // Get parameters
   boolean move = request.getParameter("move") != null;
   boolean delete = request.getParameter("delete") != null;
   boolean lock = request.getParameter("lock") != null;
   long[] threadIDs = ParamUtils.getLongParameters(request, "thread", -1L);
   long forumID = ParamUtils.getLongParameter(request, "forum", -1L);
   long threadID = ParamUtils.getLongParameter(request, "thread", -1L);
   boolean deleteThread = ParamUtils.getBooleanParameter(request, "deleteThread");
   int start = ParamUtils.getIntParameter(request, "start", 0);
   int range = ParamUtils.getIntParameter(request, "range", RANGE);

   // Move threads:
   if (move || delete || lock) {
      StringBuffer buf = new StringBuffer();
      if (move) {
         buf.append("forumContent_move.jsp?");
      } else if (lock) {
         buf.append("forumContent_lock.jsp?");
      } else { // else if delete
         buf.append("forumContent_delete.jsp?");
      }
      buf.append("forum=").append(forumID);
      for (int i = 0; i < threadIDs.length; i++) {
         buf.append("&thread=").append(threadIDs[i]);
      }
      response.sendRedirect(buf.toString());
      return;
   }

   // Indicates if we're showing a listing of the forums or of the threads
   boolean showForumList = (forumID == -1L);
   boolean showThreadList = !showForumList;

   if (showThreadList) {
      // If start was not passed in, check the session for the start value.
      if (request.getParameter("start") == null) {
         try {
            start = ((Integer) session.getAttribute("admin.forumContent." + forumID + ".start")).intValue();
         } catch (Exception ignored) {
         }
      } else {
         // Put the start value in the session
         session.setAttribute("admin.forumContent." + forumID + ".start", Integer.valueOf(start));
      }
   }

   if (showThreadList) {
      // Put the forum in the session (is needed by the sidebar)
      session.setAttribute("admin.sidebar.forums.currentForumID", "" + forumID);
   } else {
      // Remove the forum in the session (if we come to this page, the sidebar
      // shouldn't show the specific forum options).
      session.removeAttribute("admin.sidebar.forums.currentForumID");
   }

   // Load the forum we're working with
   Forum forum = null;
   if (forumID != -1L) {
      forum = forumFactory.getForum(forumID);
   }

   // Optionally load the thread we're working with
   ForumThread thread = null;
   if (threadID != -1L) {
      thread = forum.getThread(threadID);
   }

   // An iterator of forums in the system
   Iterator forums = null;
   if (showForumList) {
      forums = forumFactory.getRootForumCategory().getRecursiveForums();
   }

   // An iterator of threads in this forum
   Iterator threads = null;
   if (showThreadList) {
      ResultFilter filter = new ResultFilter();
      filter.setStartIndex(start);
      filter.setNumResults(range);
      threads = forum.getThreads(filter);
   }

   // Get a user manager to get user objects
   UserManager manager = forumFactory.getUserManager();

   // Delete a thread if necessary
   if (deleteThread && forum != null) {
      forum.deleteThread(thread);
      response.sendRedirect("forumContent.jsp?forum=" + forumID);
      return;
   }
%>

<% // special onload command to load the sidebar
   onload = " onload=\"parent.frames['sidebar'].location.href='sidebar.jsp?sidebar=forum';\"";
%>
<%@ include file="header.jsp" %>

<p>

      <%  // Title of this page and breadcrumbs
    String title = "Manage Content";
    String[][] breadcrumbs = null;
    if (showForumList) {
        breadcrumbs = new String[][] {
            {"Main", "main.jsp"},
            {"Forums", "forums.jsp"},
            {"Manage Content", "forumContent.jsp"}
        };
    }
    else {
        breadcrumbs = new String[][] {
            {"Main", "main.jsp"},
            {"Forums", "forums.jsp"},
            {"Edit Forum", "editForum.jsp?forum="+forumID},
            {"Manage Content", "forumContent.jsp?forum="+forumID}
        };
    }
%>
   <%@ include file="title.jsp" %>

      <%  if (showForumList) { %>

   <font size="-1">
      Manage a forum's content by clicking on the name of a forum below.
   </font>
<p>

<table bgcolor="<%= tblBorderColor %>" cellpadding="0" cellspacing="0" border="0" width="100%">
   <tr>
      <td>
         <table bgcolor="<%= tblBorderColor %>" cellpadding="3" cellspacing="1" border="0" width="100%">
            <tr bgcolor="#eeeeee">
               <td align="center"><font size="-2" face="verdana"><b>FORUM</b></font></td>
               <td align="center"><font size="-2" face="verdana"><b>CATEGORY</b></font></td>
               <td align="center"><font size="-2" face="verdana"><b>THREADS</b></font></td>
               <td align="center"><font size="-2" face="verdana"><b>MESSAGES</b></font></td>
               <td align="center"><font size="-2" face="verdana"><b>LAST MODIFIED</b></font></td>
            </tr>
            <% while (forums.hasNext()) {
               Forum f = (Forum) forums.next();
               String description = f.getDescription();
               int threadCount = f.getThreadCount();
               int messageCount = f.getMessageCount();
               boolean canEdit = f.isAuthorized(Permissions.SYSTEM_ADMIN | ForumPermissions.FORUM_ADMIN | ForumPermissions.MODERATOR);
            %>
            <tr bgcolor="#ffffff">
               <td width="96%">
                  <% if (canEdit) { %>
                  <font size="-1"><b><a href="forumContent.jsp?forum=<%= f.getID() %>"><%= f.getName() %>
                  </a></b></font>
                  <% } else { %>
                  <font size="-1"><b><%= f.getName() %>
                  </b></font>
                  <% } %>

                  <% if (description != null) { %>
                  <font size="-2"><br><%= f.getDescription() %>
                  </font>
                  <% } %>
               </td>
               <td width="1%" align="center"><font size="-2"><%= f.getForumCategory().getName() %>
               </font></td>
               <td width="1%" align="center"><font
                  size="-1"><%= LocaleUtils.getLocalizedNumber(threadCount, JiveGlobals.getLocale()) %>
               </font></td>
               <td width="1%" align="center"><font
                  size="-1"><%= LocaleUtils.getLocalizedNumber(messageCount, JiveGlobals.getLocale()) %>
               </font></td>
               <td width="1%" nowrap><font size="-1"><%= JiveGlobals.formatDateTime(f.getModificationDate()) %>
               </font></td>
            </tr>
            <% } %>
         </table>
      </td>
   </tr>
</table>

<% } else { // showThreadList %>

<font size="-1">
   <a href="forumContent.jsp"><b>Forum List</b></a> <b>&raquo;</b> <b><%= forum.getName() %>
</b>
</font>
<p>
      <%  if (forum.getThreadCount() == 0 && forum.getMessageCount() == 0) { %>

   <font size="-1">
      There are no messages or threads in this forum to edit.
   </font>

      <%  } else { %>

   <font size="-1">
      <%= LocaleUtils.getLocalizedNumber(forum.getThreadCount(), JiveGlobals.getLocale()) %> total threads,
      <%= LocaleUtils.getLocalizedNumber(forum.getMessageCount(), JiveGlobals.getLocale()) %> total messages.
   </font>
<p>

<form action="forumContent_thread.jsp">
   <input type="hidden" name="forum" value="<%= forumID %>">
   <font size="-1">Jump to thread: (enter ID)</font>
   <input type="text" name="thread" value="" size="6" maxlength="10">
   <input type="submit" value="Go">
</form>
<p>

<form action="forumContent.jsp">
   <input type="hidden" name="forum" value="<%= forumID %>">

      <%  if (forum.getThreadCount()/range > 0) { %>
   <font size="-1">
      <%= getForumPaginator(forum.getID(), forum.getThreadCount(), (forum.getThreadCount() / range) + 1, start, range) %>
   </font>
   <br>
      <%  } %>

   <table bgcolor="<%= tblBorderColor %>" cellpadding="0" cellspacing="0" border="0" width="100%">
      <tr>
         <td>
            <table bgcolor="<%= tblBorderColor %>" cellpadding="3" cellspacing="1" border="0" width="100%">
               <tr bgcolor="#eeeeee">
                  <td align="center"><font size="-2" face="verdana"><b>THREAD</b></font></td>
                  <td align="center"><font size="-2" face="verdana"><b>REPLIES</b></font></td>
                  <td align="center"><font size="-2" face="verdana"><b>AUTHOR</b></font></td>
                  <td align="center"><font size="-2" face="verdana"><b>LAST MODIFIED</b></font></td>
                  <td align="center"><font size="-2" face="verdana"><b>ACTION</b></font></td>
               </tr>
               <% int id = 0;
                  while (threads.hasNext()) {
                     id++;
                     ForumThread theThread = (ForumThread) threads.next();
                     User author = theThread.getRootMessage().getUser();
                     String name = null;
                     if (author != null) {
                        name = author.getName();
                     }
                     boolean locked = "true".equals(theThread.getProperty("locked"));
               %>
               <tr bgcolor="#ffffff" id="r<%= id %>">
                  <td>
                     <% if (locked) { %>
                     <img src="images/lock.gif" width="9" height="12" border="0">
                     <% } %>
                     <font size="-1"><b><a
                        href="forumContent_thread.jsp?tstart=<%= start %>&trange=<%= range %>&forum=<%=forumID%>&thread=<%= theThread.getID() %>"><%= theThread.getName() %>
                     </a></b></font></td>
                  <td align="center"><font size="-1">
                     <%= LocaleUtils.getLocalizedNumber(theThread.getMessageCount() - 1, JiveGlobals.getLocale()) %>
                  </font>
                  </td>
                  <td>
                     <% if (author != null) { %>
                     <font size="-1"><a href="userProfile.jsp?user=<%= author.getID() %>"
                                        title="<%= (name!=null)?name:"" %>"><%= author.getUsername() %>
                     </a></font>
                     <% } else { %>
                     <font size="-1"><i>Guest</i></font>
                     <% } %>
                  </td>
                  <td nowrap><font size="-1"><%= JiveGlobals.formatDateTime(theThread.getModificationDate()) %>
                  </font></td>
                  <td align="center"
                      onmouseover="document.all.r<%= id %>.bgColor='#eeeeee';"
                      onmouseout="document.all.r<%= id %>.bgColor='#ffffff';">
                     <input type="checkbox" name="thread" value="<%= theThread.getID() %>">
                  </td>
               </tr>
               <% } %>
               <tr bgcolor="#ffffff">
                  <td colspan="4"><font size="-1">&nbsp;</font></td>
                  <td align="center">
                     <input type="submit" name="move" value=" Move.. " style="width:100%;"><br><input
                     type="submit" name="lock" value=" Lock.. " style="width:100%;"><br><input
                     type="submit" name="delete" value=" Delete.. " style="width:100%;">
                  </td>
                  <!--
                  <td align="center"><input type="submit" name="delete" value="Lock.."></td>
                  <td align="center"><input type="submit" name="delete" value="Delete.."></td>
                  -->
               </tr>
            </table>
         </td>
      </tr>
   </table>
   <p>

         <%  } // end if this forum has threads %>

         <%  } // end if showForumList %>


      <%@ include file="footer.jsp" %>

         <%! // Global methods

	// Prints out a group of links to paginate through thread listings, ie:
	// "X page(s) [ 1 2 3 4 5 ... 30 | > ]"
	private static String getForumPaginator(long forumID, int topicCount,
			int numPages, int start, int range)
	{
		StringBuffer buf = new StringBuffer();

		// "X page(s) in this forum":
		buf.append("<b>").append(numPages).append("</b> page").append((numPages!=1)?"s":"");
        buf.append(" in this forum, ");
        buf.append("<b>").append(range).append("</b>").append(" threads per page.");

		// "["
		buf.append(" [ ");

		// Print out a left arrow if necessary
		if (start > 0) {
			buf.append("<a href=\"forumContent.jsp?forum=");
			buf.append(forumID);
			buf.append("&start=");
			buf.append((start-range));
			buf.append("&range=");
			buf.append(range);
			buf.append("\" class=\"forum\" title=\"Previous page\">");
            buf.append("<img src=\"images/prev.gif\" width=\"10\" height=\"10\" hspace=\"2\" border=\"0\">");
            buf.append("</a>");
			//buf.append(" | ");
            buf.append("<img src=\"images/blank.gif\" width=\"5\" height=\"1\" border=\"0\">");
		}

		// Calculate the starting point & end points (the range of pages to display)
		int currentPage = (start/range)+1;
		int lo = currentPage - 5;
		if (lo <= 0) {
			lo = 1;
		}
		int hi = currentPage + 5;

		// print out a link to the first page if we're beyond that page
		if (lo > 2) {
			buf.append("<a href=\"forumContent.jsp?forum=");
			buf.append(forumID);
            buf.append("&start=0");
			buf.append("\" class=\"forum\" title=\"Go to the first topic page\"><b>1</b></a> ... ");
		}

		// Print the page numbers before the current page
		while (lo < currentPage) {
			buf.append("<a href=\"forumContent.jsp?forum=");
			buf.append(forumID);
			buf.append("&start=");
			buf.append(((lo-1)*range));
			buf.append("&range=");
			buf.append(range);
			buf.append("\" class=\"forum\"><b>");
			buf.append(lo);
			buf.append("</b></a>&nbsp;");
			lo++;
		}

		// Print the current page
		buf.append("<b><span style=\"background-color:");
        buf.append(JiveGlobals.getJiveProperty("skin.default.tableRowColor1"));
        buf.append(";color:").append(JiveGlobals.getJiveProperty("skin.default.textColor")).append(";\">");
		buf.append(currentPage);
		buf.append("</span></b>");

		currentPage++;

		// Print page numbers after the current page
		while ((currentPage <= hi) && (currentPage<=numPages)) {
			buf.append("&nbsp;<a href=\"forumContent.jsp?forum=");
			buf.append(forumID);
			buf.append("&start=");
			buf.append(((currentPage-1)*range));
			buf.append("&range=");
			buf.append(range);
			buf.append("\" class=\"forum\"><b>");
			buf.append(currentPage);
			buf.append("</b></a>");
			currentPage++;
		}

		// Show a next arrow if necesary
		if (topicCount > (start+range)) {
			int numRemaining = (int)(topicCount-(start+range));
			//buf.append(" | ");
            buf.append("<img src=\"images/blank.gif\" width=\"5\" height=\"1\" border=\"0\">");
			buf.append("<a href=\"forumContent.jsp?forum=");
			buf.append(forumID);
			buf.append("&start=");
			buf.append((start+range));
			buf.append("&range=");
			buf.append(range);
			buf.append("\" class=\"forum\" title=\"Next page\">");
            buf.append("<img src=\"images/next.gif\" width=\"10\" height=\"10\" hspace=\"2\" border=\"0\">");
            buf.append("</a>");
		}

		// "]"
		buf.append(" ]");
		return buf.toString();
	}
%>
