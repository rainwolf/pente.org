<%
   /**
    *	$RCSfile: forumModeration.jsp,v $
    *	$Revision: 1.2 $
    *	$Date: 2002/10/02 01:20:37 $
    */
%>

<%@ page import="java.util.*,
                 com.jivesoftware.forum.*,
                 com.jivesoftware.forum.util.*,
                 com.jivesoftware.util.ParamUtils"
         errorPage="error.jsp"
%>

<%! // Global vars, methods, etc

   // moderation presets
   static final int NONE = 1;
   static final int THREAD_MODERATION = 2;
   static final int THREAD_AND_MESSAGE_MODERATION = 3;
   static final int[] PRESETS = {
      NONE, THREAD_MODERATION, THREAD_AND_MESSAGE_MODERATION
   };
   static final String[] PRESET_LABELS = {
      "None",
      "Threads Only",
      "Threads and Messages"
   };
%>

<%@ include file="global.jsp" %>

<% // Permission check
   if (!isSystemAdmin && !isForumAdmin) {
      throw new UnauthorizedException("You don't have admin privileges to perform this operation.");
   }

   // get parameters
   long forumID = ParamUtils.getLongParameter(request, "forum", -1L);
   long threadID = ParamUtils.getLongParameter(request, "thread", -1L);
   long messageID = ParamUtils.getLongParameter(request, "message", -1L);
   boolean savePreset = ParamUtils.getBooleanParameter(request, "savePreset");
   boolean success = ParamUtils.getBooleanParameter(request, "success");
   int moderationPreset = ParamUtils.getIntParameter(request, "moderationPreset", NONE);

   // Load up requested forum
   Forum forum = forumFactory.getForum(forumID);

   // Change what is being moderated by altering the moderationPreset value
   if (savePreset) {
      // based on the moderation preset, switch:
      switch (moderationPreset) {
         case NONE:
            forum.setModerationDefaultThreadValue(1);
            forum.setModerationMinThreadValue(1);
            forum.setModerationDefaultMessageValue(1);
            forum.setModerationMinMessageValue(1);
            break;
         case THREAD_MODERATION:
            forum.setModerationDefaultThreadValue(0);
            forum.setModerationMinThreadValue(1);
            forum.setModerationDefaultMessageValue(1);
            forum.setModerationMinMessageValue(1);
            break;
         case THREAD_AND_MESSAGE_MODERATION:
            forum.setModerationDefaultThreadValue(0);
            forum.setModerationMinThreadValue(1);
            forum.setModerationDefaultMessageValue(0);
            forum.setModerationMinMessageValue(1);
            break;
         default:
      }
      // done saving, redirect back to this page:
      response.sendRedirect("forumModeration.jsp?forum=" + forumID + "&success=true");
      return;
   }

   // determine what type of moderation is turned on
   boolean isThreadModOn = (
      forum.getModerationDefaultThreadValue() < forum.getModerationMinThreadValue()
   );
   boolean isMsgModOn = (
      forum.getModerationDefaultMessageValue() < forum.getModerationMinMessageValue()
   );

   // determine the correct value of the moderation preset (the radio button)
   if (isThreadModOn) {
      moderationPreset = THREAD_MODERATION;
   }
   if (isMsgModOn) {
      moderationPreset = THREAD_AND_MESSAGE_MODERATION;
   }

   // Get Iterators of messages for each moderation type. Start with a result
   // filter to grab only pending threads/messages
   ResultFilter filter = new ResultFilter();
   filter.setModerationRangeMin(0);
   filter.setModerationRangeMax(0);
   Iterator pendingThreads = forum.getThreads(filter);
%>

<%@ include file="header.jsp" %>

<% // Title of this page and breadcrumbs
   String title = "Moderation";
   String[][] breadcrumbs = {
      {"Main", "main.jsp"},
      {"Moderation", "forumModeration.jsp?forum=" + forumID}
   };
%>
<%@ include file="title.jsp" %>

<font size="-1">
   Moderation allows to administrators and moderators to screen threads and messages before they are viewable
   in your forum. When moderation is turned on, you must approve or reject
   each thread or reply. You can see a list of pending messages by clicking
   on the "Moderation" tab.
</font>
<p>

      <%  if (success) { %>

   <font size="-1" color="#339900">
      Moderation setting updated successfully.
   </font>
<p>

      <%  } %>

   <font size="-1"><b>Moderation Settings</b></font>
<ul>
   <form action="forumModeration.jsp">
      <input type="hidden" name="savePreset" value="true">
      <input type="hidden" name="forum" value="<%= forumID %>">
      <table cellpadding="2" cellspacing="0" border="0">
         <% for (int i = 0; i < PRESETS.length; i++) {
            String checked = "";
            if (moderationPreset == PRESETS[i]) {
               checked = " checked";
            }
         %>
         <tr>
            <td><input type="radio" name="moderationPreset" value="<%= PRESETS[i] %>" id="rb<%= i %>"<%= checked %>>
            </td>
            <td><label for="rb<%= i %>"><font size="-1"><%= PRESET_LABELS[i] %>
            </font></label></td>
         </tr>
         <% } %>
         <tr>
            <td>&nbsp;</td>
            <td><input type="submit" value="Save"></td>
         </tr>
      </table>
   </form>
</ul>

<p>

   <%@ include file="footer.jsp" %>
