<%
   /**
    *	$RCSfile: editWatches.jsp,v $
    *	$Revision: 1.8.4.1 $
    *	$Date: 2003/02/07 18:29:06 $
    */
%>

<%@ page import="java.util.*,
                 java.text.*,
                 java.net.*,
                 java.sql.*,
                 com.jivesoftware.util.*,
                 com.jivesoftware.forum.*,
                 com.jivesoftware.forum.database.*,
                 com.jivesoftware.forum.util.*,
                 com.jivesoftware.forum.WatchManager"
         errorPage="error.jsp"
%>

<%@ include file="global.jsp" %>

<% // Permission check
   if (!isSystemAdmin) {
      throw new UnauthorizedException("You don't have admin privileges to perform this operation.");
   }

   // get parameters
   boolean emailNotify = ParamUtils.getBooleanParameter(request, "emailNotify");
   String fromName = ParamUtils.getParameter(request, "fromName", false);
   String fromEmail = ParamUtils.getParameter(request, "fromEmail", false);
   String threadSubject = ParamUtils.getParameter(request, "threadSubject", false);
   String threadBody = ParamUtils.getParameter(request, "threadBody", true);
   String forumSubject = ParamUtils.getParameter(request, "forumSubject", false);
   String forumBody = ParamUtils.getParameter(request, "forumBody", true);
   String categorySubject = ParamUtils.getParameter(request, "categorySubject", false);
   String categoryBody = ParamUtils.getParameter(request, "categoryBody", true);
   boolean saveEmailProps = ParamUtils.getBooleanParameter(request, "saveEmailProps");
   boolean saveEmailSettings = ParamUtils.getBooleanParameter(request, "saveEmailSettings");
   boolean saveWatchExprTime = ParamUtils.getBooleanParameter(request, "saveWatchExprTime");
   int watchExprDays = ParamUtils.getIntParameter(request, "watchExprDays", -1);

   // Get a watch manager:
   WatchManager watchManager = forumFactory.getWatchManager();

   // save the email settings if requested
   if (saveEmailSettings) {
      JiveGlobals.setJiveProperty("watches.emailNotifyEnabled", String.valueOf(emailNotify));
      response.sendRedirect("editWatches.jsp");
      return;
   }

   // Save the watch expr time:
   if (saveWatchExprTime) {
      watchManager.setDeleteDays(watchExprDays);
   }

   if (saveEmailProps) {
      if (fromName != null) {
         JiveGlobals.setJiveProperty("watches.email.thread.fromName", fromName);
         JiveGlobals.setJiveProperty("watches.email.forum.fromName", fromName);
         JiveGlobals.setJiveProperty("watches.email.forumCategory.fromName", fromName);
      }
      if (fromEmail != null) {
         JiveGlobals.setJiveProperty("watches.email.thread.fromEmail", fromEmail);
         JiveGlobals.setJiveProperty("watches.email.forum.fromEmail", fromEmail);
         JiveGlobals.setJiveProperty("watches.email.forumCategory.fromEmail", fromEmail);
      }
      if (threadSubject != null) {
         JiveGlobals.setJiveProperty("watches.email.thread.subject", threadSubject);
      }
      if (forumSubject != null) {
         JiveGlobals.setJiveProperty("watches.email.forum.subject", forumSubject);
      }
      if (categorySubject != null) {
         JiveGlobals.setJiveProperty("watches.email.forumCategory.subject", categorySubject);
      }
      if (threadBody != null) {
         JiveGlobals.setJiveProperty("watches.email.thread.body", threadBody);
      }
      if (forumBody != null) {
         JiveGlobals.setJiveProperty("watches.email.forum.body", forumBody);
      }
      if (categoryBody != null) {
         JiveGlobals.setJiveProperty("watches.email.forumCategory.body", categoryBody);
      }

      response.sendRedirect("editWatches.jsp");
      return;
   }

   fromEmail = JiveGlobals.getJiveProperty("watches.email.thread.fromEmail");
   fromName = JiveGlobals.getJiveProperty("watches.email.thread.fromName");
   threadSubject = JiveGlobals.getJiveProperty("watches.email.thread.subject");
   forumSubject = JiveGlobals.getJiveProperty("watches.email.forum.subject");
   categorySubject = JiveGlobals.getJiveProperty("watches.email.forumCategory.subject");
   threadBody = JiveGlobals.getJiveProperty("watches.email.thread.body");
   forumBody = JiveGlobals.getJiveProperty("watches.email.forum.body");
   categoryBody = JiveGlobals.getJiveProperty("watches.email.forumCategory.body");

   boolean isEmailNotifyEnabled = true;
   String emailNotifyStr = JiveGlobals.getJiveProperty("watches.emailNotifyEnabled");
   if (emailNotifyStr != null) {
      isEmailNotifyEnabled = Boolean.valueOf(emailNotifyStr).booleanValue();
   }
%>

<%@ include file="header.jsp" %>

<p>

      <%  // Title of this page and breadcrumbs
    String title = "Watch Settings";
    String[][] breadcrumbs = {
        {"Main", "main.jsp"},
        {title, "editWatches.jsp"}
    };
%>
   <%@ include file="title.jsp" %>

   Watches allow users to track the threads that they're interested in following
   and can be configured to send email notifications when there are updates.
   Use the settings below to turn email notifications on or off and to customize
   the message that is sent.
   Note, your email host is configured via the
   <a href="email.jsp">email settings</a> page.

<form action="editWatches.jsp" method="post" name="f1">
   <input type="hidden" name="saveEmailSettings" value="true">
   <b>Email Watch Settings</b>
   <ul>
      <table cellpadding="2" cellspacing="0" border="0">
         <tr>
            <td width="1%"><input type="radio" name="emailNotify"
                                  value="true" <%= (isEmailNotifyEnabled?"checked":"") %> id="rb01"></td>
            <td><font size="-1"><label for="rb01">Email Watch Notification Enabled</label></font></td>
         </tr>
         <tr>
            <td width="1%"><input type="radio" name="emailNotify"
                                  value="false" <%= (!isEmailNotifyEnabled?"checked":"") %> id="rb02"></td>
            <td><font size="-1"><label for="rb02">Email Watch Notification Disabled</label></font></td>
         </tr>
         <tr>
            <td colspan="2">
               <br><input type="submit" value="Save Settings">
            </td>
         </tr>
      </table>
   </ul>
</form>

<form action="editWatches.jsp" method="post" name="f2">
   <input type="hidden" name="saveWatchExprTime" value="true">
   <b>Automatic Watch Expiration</b>
   <ul>
      <table cellpadding="2" cellspacing="0" border="0">
         <tr>
            <td>Time (in days) before inactive watches are automatically deleted:</td>
            <td>
               <input type="text" name="watchExprDays" size="5" maxlength="5"
                      value="<%= watchManager.getDeleteDays() %>">
            </td>
         </tr>
         <tr>
            <td colspan="2">
               <br><input type="submit" value="Save Settings">
            </td>
         </tr>
      </table>
   </ul>
</form>

<form action="editWatches.jsp" method="post" name="f3">
   <input type="hidden" name="saveEmailProps" value="true">
   <b>Email Properties</b>
   <ul>
      <table cellpadding="2" cellspacing="0" border="0">
         <tr>
            <td nowrap><font size="-1">From Name:</font></td>
            <td><input type="text" name="fromName" size="30" value="<%= ((fromName!=null)?fromName:"") %>"></td>
         </tr>
         <tr>
            <td nowrap><font size="-1">From Email:</font></td>
            <td><input type="text" name="fromEmail" size="30" value="<%= ((fromEmail!=null)?fromEmail:"") %>"></td>
         </tr>
      </table>
      <br>
      <font size="-1">
         You can insert the following tokens into the subject or body of the
         email message and they'll be dynamically replaced with the appropriate
         values when the email is sent.
         <br>
      </font>
      <font color="#006600">
         <tt>
            {username}
            {email}
            {name}
            {userID}
            {messageUser}
            {messageID}
            {messageSubject}
            {messageBody}
            {messageCreationDate}
            {messageModificationDate}
            {threadID}
            {threadName}
            {threadModificationDate}
            {threadCreationDate}
            {forumID}
            {forumName}
            {categoryID}
            {categoryName}
            {jiveURL}
         </tt>
      </font>
      <br><br>
      <b>Thread watch emails</b>
      <br><br>
      <table cellpadding="2" cellspacing="0" border="0">
         <tr>
            <td nowrap><font size="-1">Subject:</font></td>
            <td><input type="text" name="threadSubject" size="75"
                       value="<%= ((threadSubject!=null)?StringUtils.replace(threadSubject,"\"","&quot;"):"") %>"></td>
         </tr>
         <tr>
            <td valign="top" nowrap><font size="-1">Body of email:</font></td>
            <td>
               <textarea name="threadBody" cols="60" rows="6"
                         wrap="virtual"><%= ((threadBody != null) ? threadBody : "") %></textarea>
            </td>
         </tr>
      </table>
      <br>
      <b>Forum watch emails</b>
      <br><br>
      <table cellpadding="2" cellspacing="0" border="0">
         <tr>
            <td nowrap><font size="-1">Subject:</font></td>
            <td><input type="text" name="forumSubject" size="75"
                       value="<%= ((forumSubject!=null)?StringUtils.replace(forumSubject,"\"","&quot;"):"") %>"></td>
         </tr>
         <tr>
            <td valign="top" nowrap><font size="-1">Body of email:</font></td>
            <td>
               <textarea name="forumBody" cols="60" rows="6"
                         wrap="virtual"><%= ((forumBody != null) ? forumBody : "") %></textarea>
            </td>
         </tr>
      </table>
      <br>
      <b>Category watch emails</b>
      <br><br>
      <table cellpadding="2" cellspacing="0" border="0">
         <tr>
            <td nowrap><font size="-1">Subject:</font></td>
            <td><input type="text" name="categorySubject" size="75"
                       value="<%= ((categorySubject!=null)?StringUtils.replace(categorySubject,"\"","&quot;"):"") %>">
            </td>
         </tr>
         <tr>
            <td valign="top" nowrap><font size="-1">Body of email:</font></td>
            <td>
               <textarea name="categoryBody" cols="60" rows="6"
                         wrap="virtual"><%= ((categoryBody != null) ? categoryBody : "") %></textarea>
            </td>
         </tr>
         <tr>
            <td>&nbsp;</td>
            <td><br><input type="submit" value="Save Settings"></td>
         </tr>
      </table>
   </ul>
</form>

<%@ include file="footer.jsp" %>
