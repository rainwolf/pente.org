<%
   /**
    *	$RCSfile: forumContent_thread.jsp,v $
    *	$Revision: 1.2 $
    *	$Date: 2002/10/02 01:20:37 $
    */
%>

<%@ page import="java.net.*,
                 java.util.*,
                 java.text.*,
                 com.jivesoftware.forum.*,
                 com.jivesoftware.forum.util.*,
                 com.jivesoftware.util.*"
         errorPage="error.jsp"
%>

<%@ include file="global.jsp" %>

<%! // Global variables, methods, etc

   // More methods at the bottom of the page

   // default range & starting point for the thread iterators
   private final static int START = 0;
   private final static int RANGE = 15;
%>

<% // Permission check
   if (!isSystemAdmin && !isForumAdmin && !isModerator) {
      throw new UnauthorizedException("You don't have admin privileges to perform this operation.");
   }

   // Get parameters
   long forumID = ParamUtils.getLongParameter(request, "forum", -1L);
   long threadID = ParamUtils.getLongParameter(request, "thread", -1L);
   long messageID = ParamUtils.getLongParameter(request, "message", -1L);
   int start = ParamUtils.getIntParameter(request, "start", START);
   int range = ParamUtils.getIntParameter(request, "range", RANGE);
   boolean delete = ParamUtils.getBooleanParameter(request, "delete");

   // "nav" indicates if we show the next/prev links
   boolean nav = false;
   // Only show nav if the thread starting point and thread range are passed in:
   int threadStart = ParamUtils.getIntParameter(request, "tstart", -1);
   int threadRange = ParamUtils.getIntParameter(request, "trange", -1);
   if (threadStart > -1 && threadRange > -1) {
      nav = true;
      // If the starting point is not zero, back it up one (because the last thread
      // is likely the one before it)
      if (threadStart > 0) {
         threadStart--;
      }
      // Always increment the range by one since we don't know
      // if the last thread on the thread list page was clicked:
      threadRange++;
   }

   // Load the forum we're working with
   Forum forum = forumFactory.getForum(forumID);

   // Optionally load the thread we're working with
   ForumThread thread = null;
   if (threadID != -1L) {
      thread = forum.getThread(threadID);
   }

   // Optionally load the thread we're working with
   ForumMessage message = null;
   if (messageID != -1L) {
      message = thread.getMessage(messageID);
   }

   // Number of replies to this thread
   int numMessages = thread.getMessageCount();
   int numReplies = numMessages - 1; // subtract 1 because the root message is counted
   int numPages = (int) Math.ceil((double) numMessages / (double) range);

   // Get a thread iterator (for the next/previous thread feature)
   ForumThreadIterator threadIterator = null;
   if (nav) {
      ResultFilter threadFilter = new ResultFilter();
      threadFilter.setStartIndex(threadStart);
      threadFilter.setNumResults(threadRange);
      threadIterator = forum.getThreads(threadFilter);
      threadIterator.setIndex(thread);
   }
%>

<% // special onload command to load the sidebar
   onload = " onload=\"parent.frames['sidebar'].location.href='sidebar.jsp?sidebar=forum';\"";
%>
<%@ include file="header.jsp" %>

<p>

      <%  // Title of this page and breadcrumbs
    String title = "Manage Content: Edit Thread";
    String[][] breadcrumbs = {
        {"Main", "main.jsp"},
        {"Forums", "forums.jsp"},
        {"Edit Forum", "editForum.jsp?forum="+forumID},
        {"Manage Content", "forumContent.jsp?forum="+forumID},
        {"Edit Thread", "forumContent_thread.jsp?forum="+forumID+"&thread="+threadID+"&message="+messageID}
    };
%>
   <%@ include file="title.jsp" %>

   <b><%= thread.getName() %>
   </b></font>
   <br>
   <font size="-1">
      Created: <%= SkinUtils.formatDate(request, pageUser, thread.getCreationDate()) %>,
      Modified: <%= SkinUtils.formatDate(request, pageUser, thread.getModificationDate()) %>
   </font>
<table cellpadding="2" cellspacing="0" border="0">
   <tr>
      <td>
         <% String redirect = "forumContent_thread.jsp?forum=" + forumID + "&thread=" + threadID; %>
         <a href="editThreadProps.jsp?forum=<%= forumID %>&thread=<%= threadID %>&redirect=<%= URLEncoder.encode(redirect) %>"
            title="Click to edit extended properties of this thread"
         ><img src="images/button_edit.gif" width="17" height="17" border="0"
         ></a>
      </td>
      <td>
         <font size="-1">
            <a href="editThreadProps.jsp?forum=<%= forumID %>&thread=<%= threadID %>&redirect=<%= URLEncoder.encode(redirect) %>"
               title="Click to edit extending properties of this thread"
            >Edit Thread Properties</a>
         </font>
      </td>
      <td>&nbsp;</td>
      <td>
         <a href="forumContent_delete.jsp?forum=<%= forumID %>&thread=<%= threadID %>"
            title="Click to delete this message and its replies..."
         ><img src="images/button_delete.gif" width="17" height="17" border="0"
         ></a>
      </td>
      <td>
         <font size="-1">
            <a href="forumContent_delete.jsp?forum=<%= forumID %>&thread=<%= threadID %>"
               title="Click to delete this message and its replies..."
            >Delete This Thread</a>
         </font>
      </td>
   </tr>
</table>


<br>

<table cellpadding="0" cellspacing="2" border="0" width="100%" align="center">
   <tr>
      <td colspan="3"><img src="images/blank.gif" width="1" height="5" border="0"></td>
   </tr>
   <tr>
      <td width="1%" nowrap>
         <% if (threadIterator != null && threadIterator.hasPrevious()) {
            ForumThread prevThread = (ForumThread) threadIterator.previous();
            // advance the iterator pointer back to the original index
            threadIterator.next();
            String subj = prevThread.getRootMessage().getSubject();
            // Replace any " in the subject
            subj = StringUtils.replace(subj, "\"", "&quot;");
         %>
         <a href="forumContent_thread.jsp?forum=<%= forumID %>&thread=<%= prevThread.getID() %>"
            title="Thread: <%= subj %>"><img src="images/prev.gif" width="10" height="10" hspace="2" border="0"></a>
         <font size="-1">
            <a href="forumContent_thread.jsp?forum=<%= forumID %>&thread=<%= prevThread.getID() %>"
               title="Thread: <%= subj %>">Previous Thread</a>
         </font>
         <% } else { %>
         &nbsp;
         <% } %>
      </td>
      <td width="98%" align="center">
         <font size="-1"><a href="forumContent.jsp?forum=<%= forumID %>">Back To Thread List</a></font>
      </td>
      <td width="1%" nowrap>
         <% if (threadIterator != null && threadIterator.hasNext()) {
            ForumThread nextThread = (ForumThread) threadIterator.next();
            String subj = nextThread.getRootMessage().getSubject();
         %>
         <font size="-1">
            <a href="forumContent_thread.jsp?forum=<%= forumID %>&thread=<%= nextThread.getID() %>"
               title="Thread: <%= subj %>">Next Thread</a>
         </font>
         <a href="forumContent_thread.jsp?forum=<%= forumID %>&thread=<%= nextThread.getID() %>"
            title="Thread: <%= subj %>"><img src="images/next.gif" width="10" height="10" hspace="2" border="0"></a>
         <% } else { %>
         &nbsp;
         <% } %>
      </td>
   </tr>
</table>

<p>

      <%  if (numPages > 1) { %>
   <font size="-1">
      <%= getThreadPaginator(forumID, threadID, numMessages, numReplies, numPages, start, range) %>
   </font>
      <%  } %>

      <%  // loop through all threads in the forum
    ResultFilter filter = new ResultFilter();
    filter.setStartIndex(start);
    filter.setNumResults(range);
    filter.setSortOrder(ResultFilter.ASCENDING);
    filter.setSortField(JiveConstants.CREATION_DATE);
    Iterator messages = thread.getMessages(filter);
    while (messages.hasNext()) {
        ForumMessage theMessage = (ForumMessage)messages.next();
        User author = theMessage.getUser();
        boolean isRootMessage = (theMessage.getID() == thread.getRootMessage().getID());
        String subject = theMessage.getSubject();
%>
<table bgcolor="<%= tblBorderColor %>" cellpadding="0" cellspacing="0" border="0" width="100%">
   <tr>
      <td>
         <table bgcolor="<%= tblBorderColor %>" cellpadding="3" cellspacing="1" border="0" width="100%">
            <tr>
               <td bgcolor="<%= (isRootMessage)?"#dddddd":"#eeeeee" %>">
                  <table cellspacing="2" cellpadding="0" border="0" width="100%">
                     <tr>
                        <td width="99%">
                           <% if (isRootMessage) { %>
                           <b><%= (subject != null) ? subject : "" %>
                           </b>
                           <br>
                           <% } else { %>
                           <font size="-1">
                              <b><%= (subject != null) ? subject : "" %>
                              </b>
                              <br>
                           </font>
                           <% } %>

                           <font size="-1">
                              <% if (author != null) { %>
                              Posted By: <a href="userProfile.jsp?user=<%= author.getID() %>"
                           ><%= author.getUsername() %>
                           </a>
                              <% } else { %>
                              Posted By: <i>Guest</i>
                              <% } %>
                              on <%= JiveGlobals.formatDateTime(theMessage.getCreationDate()) %>
                           </font>
                        </td>
                        <td width="1%" nowrap>
                           <table cellpadding="2" cellspacing="0" border="0">
                              <tr>
                                 <td>
                                    <a href="forumContent_edit.jsp?forum=<%= forumID %>&thread=<%= theMessage.getForumThread().getID() %>&message=<%= theMessage.getID() %>"
                                       title="Click to edit the contents of this message"
                                    ><img src="images/button_edit.gif" width="17" height="17" border="0"
                                    ></a>
                                 </td>
                                 <td>
                                    <font size="-1">
                                       <a href="forumContent_edit.jsp?forum=<%= forumID %>&thread=<%= theMessage.getForumThread().getID() %>&message=<%= theMessage.getID() %>"
                                          title="Click to edit the contents of this message"
                                       >Edit</a>
                                    </font>
                                 </td>
                                 <td>&nbsp;</td>
                                 <td>
                                    <a href="forumContent_delete.jsp?forum=<%= forumID %>&thread=<%= theMessage.getForumThread().getID() %>&message=<%= theMessage.getID() %>"
                                       title="Click to delete this message and its replies..."
                                    ><img src="images/button_delete.gif" width="17" height="17" border="0"
                                    ></a>
                                 </td>
                                 <td>
                                    <font size="-1">
                                       <a href="forumContent_delete.jsp?forum=<%= forumID %>&thread=<%= theMessage.getForumThread().getID() %>&message=<%= theMessage.getID() %>"
                                          title="Click to delete this message and its replies..."
                                       >Delete</a>
                                    </font>
                                 </td>
                              </tr>
                           </table>
                        </td>
                     </tr>
                  </table>
               </td>
            </tr>
            <tr bgcolor="#ffffff">
               <td>
                  <font size="-1">
                     <%= theMessage.getBody() %>
                  </font>
               </td>
            </tr>
         </table>
      </td>
   </tr>
</table>
<br>
<% if (isRootMessage && numReplies > 0) { %>
<font size="-1"><b>Replies:</b> (<%= numReplies %> total)</font>
<p>
      <%      } %>

      <%  } %>

      <%  if (numPages > 1) { %>
   <font size="-1">
      <%= getThreadPaginator(forumID, threadID, numMessages, numReplies, numPages, start, range) %>
   </font>
      <%  } %>

   <br>

<table cellpadding="0" cellspacing="2" border="0" width="100%" align="center">
   <tr>
      <td colspan="3"><img src="images/blank.gif" width="1" height="5" border="0"></td>
   </tr>
   <tr>
      <td width="1%" nowrap>
         <% threadIterator = forum.getThreads();
            threadIterator.setIndex(thread);
            if (threadIterator.hasPrevious()) {
               ForumThread prevThread = (ForumThread) threadIterator.previous();
               // advance the iterator pointer back to the original index
               threadIterator.next();
               String subj = prevThread.getRootMessage().getSubject();
               // Replace any " in the subject
               subj = StringUtils.replace(subj, "\"", "&quot;");
         %>
         <a href="forumContent_thread.jsp?forum=<%= forumID %>&thread=<%= prevThread.getID() %>"
            title="<%= subj %>"><img src="images/prev.gif" width="10" height="10" hspace="2" alt="Thread: <%= subj %>"
                                     border="0"></a>
         <font size="-1">
            <a href="forumContent_thread.jsp?forum=<%= forumID %>&thread=<%= prevThread.getID() %>"
               title="Topic: <%= subj %>">Previous Thread</a>
         </font>
         <% } else { %>
         &nbsp;
         <% } %>
      </td>
      <td width="98%" align="center">
         <font size="-1"><a href="forumContent.jsp?forum=<%= forumID %>">Back To Thread List</a></font>
      </td>
      <td width="1%" nowrap>
         <% if (threadIterator.hasNext()) {
            ForumThread nextThread = (ForumThread) threadIterator.next();
            String subj = nextThread.getRootMessage().getSubject();
         %>
         <font size="-1">
            <a href="forumContent_thread.jsp?forum=<%= forumID %>&thread=<%= nextThread.getID() %>"
               title="<%= subj %>">Next Thread</a>
         </font>
         <a href="forumContent_thread.jsp?forum=<%= forumID %>&thread=<%= nextThread.getID() %>"
            title="<%= subj %>"><img src="images/next.gif" width="10" height="10" hspace="2" alt="Thread: <%= subj %>"
                                     border="0"></a>
         <% } else { %>
         &nbsp;
         <% } %>
      </td>
   </tr>
</table>

<p>

   <%@ include file="footer.jsp" %>

      <%! // Global methods
    
    // Prints out a group of links to paginate through message listings, ie:
    // "This topic has X replies on Y pages [ 1 .. 7 8 9 .. 33 | > ]"
    private static String getThreadPaginator(long forumID, long threadID,
            int numMessages, int numReplies, int numPages, int start, int range)
    {   
        StringBuffer buf = new StringBuffer();
        
        buf.append("<b>").append(range).append("</b> messages per page, ");
        buf.append("<b>").append(numPages).append("</b> ");
        if (numPages == 1) {
            buf.append("page ");
        }
        else {
            buf.append("pages ");
        }
        buf.append("in this thread.");
        
        // Only show the pages if there is greater than one page
        if (numPages > 1) {
            
            // "["
            buf.append(" [ ");
            
            // Print out a "<<" if necessary
            if (start > 0) {
                buf.append("<a href=\"forumContent_thread.jsp?forum=").append(forumID);
                buf.append("&thread=").append(threadID);
                buf.append("&start=").append(start-range);
                buf.append("&range=").append(range);
                buf.append("\" title=\"Previous Page\"><img src=\"images/prev.gif\" width=\"10\" height=\"10\" hspace=\"2\" border=\"0\"></a> ");
            }
            
            int currentPage = (start/range)+1;
    	    int lo = currentPage - 3;
    	    if (lo <= 0) {
	    	    lo = 1;
    	    }
	        int hi = currentPage + 5;
            
            // Add a link back to the first page
            if (lo > 1) {
                buf.append("<a href=\"forumContent_thread.jsp?forum=").append(forumID);
                buf.append("&thread=").append(threadID);
                buf.append("&range=").append(range);
                buf.append("\" title=\"Back to first page\">");
                buf.append("1").append("</a> <b>...</b> ");
            }
            
            // Print out low page numbers
    	    while (lo < currentPage) {
                buf.append("<a href=\"forumContent_thread.jsp?forum=").append(forumID);
                buf.append("&thread=").append(threadID);
                buf.append("&start=").append((lo-1)*range);
                buf.append("&range=").append(range);
                buf.append("\"><b>");
                buf.append(lo).append("</b></a>&nbsp;");
                lo++;
            }
            
            // Current page
            buf.append("<b>");
            buf.append(currentPage);
            buf.append("</b>");
            
            // Print out high page numbers
            while ((currentPage < hi) && (currentPage<numPages)) {
                buf.append("&nbsp;<a href=\"forumContent_thread.jsp?forum=").append(forumID);
                buf.append("&thread=").append(threadID);
                buf.append("&start=").append((currentPage)*range);
                buf.append("&range=").append(range).append("\"><b>");
                buf.append(currentPage+1).append("</b></a>");
                currentPage++;
            }
            
            // put ending page at the end, ie: " 2 3 4 ... 33"
            if (numPages > currentPage) {
                buf.append(" <b>...</b>  <a href=\"forumContent_thread.jsp?forum=").append(forumID);
                buf.append("&thread=").append(threadID);
                buf.append("&start=").append((numPages-1)*range);
                buf.append("&range=").append(range);
                buf.append("\">");
                buf.append(numPages).append("</a>&nbsp;");
            }
            
            if (numMessages > (start+range)) {
                int numRemaining = (int)(numMessages-(start+range));
                buf.append(" <a href=\"forumContent_thread.jsp?forum=").append(forumID);
                buf.append("&thread=").append(threadID);
                buf.append("&start=").append(start+range);
                buf.append("&range=").append(range).append("\"");
                buf.append(" title=\"Next page\"><img src=\"images/next.gif\" width=\"10\" height=\"10\" hspace=\"2\" border=\"0\"></a>");
            }
            
            // "]"
            buf.append(" ] ");
        }
        return buf.toString();
    }
%>
