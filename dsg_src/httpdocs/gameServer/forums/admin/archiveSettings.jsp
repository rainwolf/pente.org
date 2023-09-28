<%
   /**
    *	$RCSfile: archiveSettings.jsp,v $
    *	$Revision: 1.4 $
    *	$Date: 2002/11/22 01:59:07 $
    */
%>

<%@ page import="java.util.*,
                 java.text.*,
                 com.jivesoftware.forum.*,
                 com.jivesoftware.forum.database.*,
                 com.jivesoftware.forum.util.*,
                 com.jivesoftware.util.ParamUtils"
         errorPage="error.jsp"
%>

<%@ include file="global.jsp" %>

<%!
   final static int[] INTERVAL_HOURS = {
      1, 2, 4, 8, 12, 24, 48
   };
   final static int DEFAULT_INTERVAL_HOUR = 12;

   final static int[] THREAD_DAYS = {
      10, 20, 30, 60, 90, 120, 150, 180, 210, 240, 270, 300, 330, 365
   };
   final static int DEFAULT_THREAD_DAY = 180;
%>

<% // Permission check
   if (!isSystemAdmin) {
      throw new UnauthorizedException("You don't have admin privileges to perform this operation.");
   }

   // Get parameters
   long forumID = ParamUtils.getLongParameter(request, "forum", -1L);
   long archiveForumID = ParamUtils.getLongParameter(request, "archiveForum", -1L);
   boolean doArchive = request.getParameter("doArchive") != null;
   boolean running = ParamUtils.getBooleanParameter(request, "running");
   boolean saveGlobal = request.getParameter("saveGlobal") != null;
   boolean saveForum = ParamUtils.getBooleanParameter(request, "saveForum");
   boolean saveEnable = request.getParameter("saveEnable") != null;
   boolean saveDays = request.getParameter("saveDays") != null;
   boolean saveMode = request.getParameter("saveMode") != null;
   boolean autoArchiveEnabled = ParamUtils.getBooleanParameter(request, "autoArchiveEnabled");
   boolean archiveEnabled = ParamUtils.getBooleanParameter(request, "archiveEnabled");
   int autoArchiveInterval =
      ParamUtils.getIntParameter(request, "autoArchiveInterval", DEFAULT_INTERVAL_HOUR);
   int threadInactInterval =
      ParamUtils.getIntParameter(request, "threadInactInterval", DEFAULT_THREAD_DAY);
   int archiveMode = ParamUtils.getIntParameter(request, "archiveMode", ArchiveManager.MARK_ONLY);

   // In global mode?
   boolean isGlobal = (forumID == -1L);

   // Load a forum, if specified
   Forum forum = null;
   if (!isGlobal) {
      forum = forumFactory.getForum(forumID);
   }

   // Load the archive forum if specified:
   Forum archiveForum = null;
   if (!isGlobal && archiveForumID != -1L) {
      try {
         archiveForum = forumFactory.getForum(archiveForumID);
      } catch (Exception ignored) {
      }
   }

   // Get the archive manager
   ArchiveManager archiveManager = forumFactory.getArchiveManager();

   // If a request to start the archive task was received, do that:
   if (doArchive) {
      archiveManager.runArchiver();
      // Done, so redirect
      response.sendRedirect("archiveSettings.jsp?running=true&forum=" + forumID);
      return;
   }

   // Save
   if (saveGlobal) {
      // set auto archiving
      archiveManager.setAutoArchiveEnabled(autoArchiveEnabled);
      // set auto-archive interval
      archiveManager.setAutoArchiveInterval(autoArchiveInterval);
      // done, so redirect
      response.sendRedirect("archiveSettings.jsp");
      return;
   } else if (saveForum) {
      if (saveEnable) {
         // Enable/Disable archiving
         archiveManager.setArchivingEnabled(forum, archiveEnabled);
      } else if (saveDays) {
         // Number of inactive days b4 archiving:
         archiveManager.setArchiveDays(forum, threadInactInterval);
      } else if (saveMode) {
         // set the archiving mode
         archiveManager.setArchiveMode(forum, archiveMode);
         // if the mode is to move to another forum, set the forum:
         if (archiveForum != null && archiveMode == ArchiveManager.MOVE_THREADS) {
            archiveManager.setArchiveForum(forum, archiveForum);
         }
      }
      // done, so redirect:
      response.sendRedirect("archiveSettings.jsp?forum=" + forumID);
      return;
   }

   // Get properties as they are set in the system:
   if (isGlobal) {
      autoArchiveEnabled = archiveManager.isAutoArchiveEnabled();
      autoArchiveInterval = archiveManager.getAutoArchiveInterval();
   } else {
      archiveEnabled = archiveManager.isArchivingEnabled(forum);
      threadInactInterval = archiveManager.getArchiveDays(forum);
      archiveMode = archiveManager.getArchiveMode(forum);
      archiveForum = archiveManager.getArchiveForum(forum);
   }

   java.util.Date lastArchiveTime = archiveManager.getLastArchivedDate();

   // Indicate if the archiver is currently running
   boolean isBusy = archiveManager.isBusy();
%>

<% if (!isGlobal) {
   // Put the forum in the session (is needed by the sidebar)
   session.setAttribute("admin.sidebar.forums.currentForumID", "" + forumID);
   // special onload command to load the sidebar
   onload = " onload=\"parent.frames['sidebar'].location.href='sidebar.jsp?sidebar=forum';\"";
}
%>
<%@ include file="header.jsp" %>

<% // Title of this page and breadcrumbs
   String title = "Archive Settings";
   String[][] breadcrumbs = null;
   if (isGlobal) {
      breadcrumbs = new String[][]{
         {"Main", "main.jsp"},
         {title, "archiveSettings.jsp"}
      };
   } else {
      breadcrumbs = new String[][]{
         {"Main", "main.jsp"},
         {title, "archiveSettings.jsp?forum=" + forumID}
      };
   }
%>
<%@ include file="title.jsp" %>

<% if (isGlobal) { %>

<% if (running) { %>

<p>
   <font size="-1">
      <i>An archiving task has been started in the background.</i>
   </font>
</p>

<% } %>

<form action="archiveSettings.jsp">

   <font size="-1">
      <b>Auto-Archiving</b>
   </font>
   <ul>
      <font size="-1">
         <p>
            Enable or disable automatic archiving. If auto-archiving is disabled your
            content will not be automatically moved or deleted.
         </p>
         <table cellpadding="2" cellspacing="0" border="0">
            <tr>
               <td><input type="radio" name="autoArchiveEnabled" value="true"<%= (autoArchiveEnabled?" checked":"") %>
                          id="rb01"></td>
               <td><font size="-1"><label for="rb01">Auto-Archiving Enabled</label></font></td>
            </tr>
            <tr>
               <td><input type="radio" name="autoArchiveEnabled" value="false"<%= (!autoArchiveEnabled?" checked":"") %>
                          id="rb02"></td>
               <td><font size="-1"><label for="rb02">Auto-Archiving Disabled</label></font></td>
            </tr>
         </table>
         <p>
            If auto archiving is enabled, you can set how often auto archiving is run:
         </p>
         <table cellpadding="2" cellspacing="0" border="0">
            <tr>
               <td><font size="-1">Hours between archiving:</font></td>
               <td>
                  <select size="1" name="autoArchiveInterval">
                     <% for (int i = 0; i < INTERVAL_HOURS.length; i++) {
                        String selected = (autoArchiveInterval == INTERVAL_HOURS[i]) ? " selected" : "";
                     %>
                     <option value="<%= INTERVAL_HOURS[i] %>"<%= selected %>><%= INTERVAL_HOURS[i] %>
                           <%  } %>
                  </select>
               </td>
            </tr>
         </table>
      </font>

      <p>
         <input type="submit" name="saveGlobal" value="Save Settings">
      </p>
   </ul>
</form>

<font size="-1">
   <b>Start Archiving Task</b>
</font>
<ul>
   <font size="-1">
      <p>
         Periodically, Jive Forums will archive your content. However, if you want
         to start the archiving process now, click the button below.
      </p>
      <% if (isBusy) { %>

      <table cellpadding="2" cellspacing="0" border="0">
         <tr>
            <td><img src="images/busy.gif" width="16" height="16" border="0"></td>
            <td>
               <font size="-1">
                  An archiving process is currently running in the background.
               </font>
            </td>
         </tr>
      </table>

      <% } else { %>

      <p>
         Last archive time:
         <% if (lastArchiveTime == null) { %>
         <i>Not available (an archiving process may not have been run yet).</i>
         <% } else { %>
         <%= SkinUtils.formatDate(request, pageUser, lastArchiveTime) %>
         <% } %>
      </p>
   </font>
   <form action="archiveSettings.jsp">
      <input type="hidden" name="forum" value="<%= forumID %>">
      <input type="submit" name="doArchive" value="Start Archiving Now">
   </form>

   <% } %>
</ul>


<% } else { // is not global %>

<font size="-1">
   <b>Archiving Status</b>
</font>
<ul>
   <form action="archiveSettings.jsp">
      <input type="hidden" name="forum" value="<%= forumID %>">
      <input type="hidden" name="saveForum" value="true">
      <font size="-1">
         <p>Enable or disable archiving for this forum</p>
      </font>
      <table cellpadding="2" cellspacing="0" border="0">
         <tr>
            <td><input type="radio" name="archiveEnabled" value="true"<%= (archiveEnabled?" checked":"") %> id="rb01">
            </td>
            <td><font size="-1"><label for="rb01">Archiving Enabled</label></font></td>
         </tr>
         <tr>
            <td><input type="radio" name="archiveEnabled" value="false"<%= (!archiveEnabled?" checked":"") %> id="rb02">
            </td>
            <td><font size="-1"><label for="rb02">Archiving Disabled</label></font></td>
         </tr>
      </table>

      <p><input type="submit" name="saveEnable" value="Update"></p>
   </form>
</ul>

<% if (archiveEnabled) { %>

<font size="-1">
   <b>Inactive Content</b>
</font>
<ul>
   <form action="archiveSettings.jsp">
      <input type="hidden" name="forum" value="<%= forumID %>">
      <input type="hidden" name="saveForum" value="true">
      <table cellpadding="2" cellspacing="0" border="0">
         <tr>
            <td><font size="-1">Number of days a thread should be inactive before archiving it:</font></td>
            <td>
               <select size="1" name="threadInactInterval">
                  <% for (int i = 0; i < THREAD_DAYS.length; i++) {
                     String selected = (threadInactInterval == THREAD_DAYS[i]) ? " selected" : "";
                  %>
                  <option value="<%= THREAD_DAYS[i] %>"<%= selected %>><%= THREAD_DAYS[i] %>
                        <%  } %>
               </select>
            </td>
         </tr>
      </table>
      <p><input type="submit" name="saveDays" value="Update"></p>
   </form>
</ul>

<font size="-1">
   <b>Archive Actions</b>
</font>
<ul>
   <font size="-1">
      <p>
         Use the form below to specify what should happen when content is archived.
      </p>
   </font>
   <form action="archiveSettings.jsp">
      <input type="hidden" name="forum" value="<%= forumID %>">
      <input type="hidden" name="saveForum" value="true">
      <table cellpadding="3" cellspacing="0" border="0">
         <tr>
            <td>
               <input type="radio" name="archiveMode" value="<%= ArchiveManager.MARK_ONLY %>"
                  <%= (archiveMode==ArchiveManager.MARK_ONLY?" checked":"") %> id="rb03">
            </td>
            <td><font size="-1"><label for="rb03">Do Nothing - Just mark the content as "archived"</label></font></td>
         </tr>
         <tr>
            <td>
               <input type="radio" name="archiveMode" value="<%= ArchiveManager.DELETE_THREADS %>"
                  <%= (archiveMode==ArchiveManager.DELETE_THREADS?" checked":"") %> id="rb04">
            </td>
            <td><font size="-1"><label for="rb04">Delete the Content - Archived threads and messages will be
               automatically deleted.</label></font></td>
         </tr>
         <tr>
            <td valign="top">
               <input type="radio" name="archiveMode" value="<%= ArchiveManager.MOVE_THREADS %>"
                  <%= (archiveMode==ArchiveManager.MOVE_THREADS?" checked":"") %> id="rb05">
            </td>
            <td>
               <font size="-1">
                  <label for="rb05">
                     Move the Content - Use the form below to specify which forum
                     archived threads should be moved to.
                  </label>
                  <br><br>
                  <font size="-1">
                     <ul>
                        <li type="disc">
                           Forum:

                           <select size="1" name="archiveForum" style="margin:1;"
                                   onchange="this.form.archiveMode[2].checked=true;">
                              <% if (archiveForum == null) { %>
                              <option value=""> - No Forum Selected -
                              <option value="">
                                    <%  } %>
                                    <%  Iterator forums = forumFactory.getRootForumCategory().getRecursiveForums();
                        List forumList = new LinkedList();
                        while (forums.hasNext()) {
                            Forum f = (Forum)forums.next();
                            forumList.add(f);
                        }
                        Collections.sort(forumList,(obj1,obj2)-> {
                            String f1Name = ((Forum)obj1).getName().toLowerCase();
                            String f2Name = ((Forum)obj2).getName().toLowerCase();
                            return f1Name.compareTo(f2Name);
                        });
                        for (int i=0; i<forumList.size(); i++) {
                            Forum f = (Forum)forumList.get(i);
                            String selected = "";
                            if (archiveForum != null && archiveForum.getID() == f.getID()) {
                                selected = " selected";
                            }
                            if (f.getID() != forum.getID()) {
                    %>
                              <option value="<%= f.getID() %>"<%= selected %>><%= f.getName() %>

                                    <%      }
                        }
                    %>
                           </select>
                        <li type="disc">
                           <a href="createForum.jsp?cat=<%= forum.getForumCategory().getID() %>&name=<%= java.net.URLEncoder.encode(forum.getName() + " Archive") %>"
                           >Create a new forum</a>.
                     </ul>
                  </font>
            </td>
         </tr>
      </table>
      <p><input type="submit" name="saveMode" value="Update"></p>
   </form>
</ul>

<% } %>


<% } // end else is not global %>

<%@ include file="footer.jsp" %>
