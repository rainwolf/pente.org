<%
   /**
    *	$RCSfile: createForum.jsp,v $
    *	$Revision: 1.2 $
    *	$Date: 2002/10/17 20:10:34 $
    */
%>

<%@ page import="java.util.*,
                 com.jivesoftware.forum.*,
                 com.jivesoftware.forum.util.*,
                 com.jivesoftware.util.ParamUtils"
         errorPage="error.jsp"
%>

<%@ include file="global.jsp" %>

<%! // Global variables, methods, etc

   // Permission presets
   static final int USE_GLOBAL_PERMS = 1;
   static final int ALL_ACCESS = 2;
   static final int USERS_ONLY = 3;
   static final int USERS_AND_ANON_READ = 4;

   static final int DEFAULT_PERM_PRESET = USE_GLOBAL_PERMS;

   static final int[] PERM_PRESETS = {
      USE_GLOBAL_PERMS,
      USERS_ONLY,
      USERS_AND_ANON_READ,
      ALL_ACCESS
   };

   static final String[][] PERM_PRESET_INFO = {
      {"Use Global Permissions", "Let the global permission settings dictate the permissions for this forum."},
      {"Registered Users Only", "Only registered users can read and post messages."},
      {"Registered Users with Guest Read", "Registered users can read and post messages while guests can only read messages."},
      {"All Access", "Everyone can read and post new messages and threads."}
   };
%>

<% // Permission check
   if (!isSystemAdmin && !isCatAdmin) {
      throw new UnauthorizedException("You don't have admin privileges to perform this operation.");
   }

   // Remove the forum in the session (if we come to this page, the sidebar
   // shouldn't show the specific forum options).
   session.removeAttribute("admin.sidebar.forums.currentForumID");

   // Get parameters
   boolean create = request.getParameter("create") != null;
   boolean cancel = request.getParameter("cancel") != null;
   String name = ParamUtils.getParameter(request, "name");
   String description = ParamUtils.getParameter(request, "description");
   int permPreset = ParamUtils.getIntParameter(request, "permPreset", DEFAULT_PERM_PRESET);
   long categoryID = ParamUtils.getLongParameter(request, "cat", -1L);

   // Cancel, if requested
   if (cancel) {
      response.sendRedirect("forums.jsp?cat" + categoryID);
      return;
   }

   // Load the forum category if requested
   ForumCategory category = null;
   if (categoryID != -1L) {
      category = forumFactory.getForumCategory(categoryID);
   } else {
      category = forumFactory.getRootForumCategory();
   }
   // Make sure the user has cat admin priv on this category.
   if (!isSystemAdmin && !category.isAuthorized(ForumPermissions.FORUM_CATEGORY_ADMIN)) {
      throw new UnauthorizedException("You don't have admin privileges to perform this operation.");
   }

   // Check for errors
   boolean errors = false;
   if (create) {
      if (name == null) {
         errors = true;
      }
   }

   // Create a forum if requested
   if (create && !errors) {

      // Create this forum in the category
      Forum forum = forumFactory.createForum(name, description, category);

      // Get a permissions manager so we can set permissions below
      PermissionsManager permManager = forum.getPermissionsManager();

      // set permissions
      switch (permPreset) {
         case USE_GLOBAL_PERMS:
            // do nothing -- global permission are used by default
            break;
         case USERS_ONLY:
            permManager.addRegisteredUserPermission(ForumPermissions.READ_FORUM);
            permManager.addRegisteredUserPermission(ForumPermissions.CREATE_THREAD);
            permManager.addRegisteredUserPermission(ForumPermissions.CREATE_MESSAGE);
            break;
         case USERS_AND_ANON_READ:
            permManager.addAnonymousUserPermission(ForumPermissions.READ_FORUM);
            permManager.addRegisteredUserPermission(ForumPermissions.CREATE_THREAD);
            permManager.addRegisteredUserPermission(ForumPermissions.CREATE_MESSAGE);
            break;
         case ALL_ACCESS:
            permManager.addAnonymousUserPermission(ForumPermissions.READ_FORUM);
            permManager.addAnonymousUserPermission(ForumPermissions.CREATE_THREAD);
            permManager.addAnonymousUserPermission(ForumPermissions.CREATE_MESSAGE);
            break;
         default:
      }

      // redirect back to the forums page
      response.sendRedirect("forums.jsp?cat=" + category.getID());
      return;
   }
%>

<% // special onload command to load the sidebar
   onload = " onload=\"parent.frames['sidebar'].location.href='sidebar.jsp?sidebar=forum';\"";
%>
<%@ include file="header.jsp" %>

<p>

      <%  // Title of this page and breadcrumbs
    String title = "Create a New Forum";
    String[][] breadcrumbs = {
        {"Main", "main.jsp"},
        {"Forums", "forums.jsp"},
        {"Create Forum", "createForum.jsp?cat=" + category.getID()}
    };
%>
   <%@ include file="title.jsp" %>

   <font size="-1">
      Note: This creates a forum with no permissions. After you create this forum,
      you will be taken to the forum permissions screen.
   </font>

<p>

      <%	// error messages
	if(errors) {
%>
   <font color="#ff0000" size="-1">
      Error creating forum. Please make sure you've entered a forum name.
   </font>
<p>
      <%	} %>

<form action="createForum.jsp" method="post" name="createForm">
   <input type="hidden" name="cat" value="<%= category.getID() %>">

   <font size="-1"><b>Forum Properties</b></font>
   <ul>
      <table cellpadding="2" cellspacing="0" border="0">
         <tr>
            <td><font size="-1">Category:</font></td>
            <td>
               <font size="-1">
                  <% if (category == null || category.getID() == 1L) { %>
                  Root Category
                  <% } else { %>
                  <%= category.getName() %>
                  <% } %>
               </font>
            </td>
         </tr>
         <tr>
            <td><font size="-1">Forum Name:</font></td>
            <td>
               <input type="text" name="name" size="40" maxlength="255" value="<%= (name!=null)?name:"" %>">
            </td>
         </tr>
         <tr>
            <td valign="top"><font size="-1">Description (optional):</font></td>
            <td>
               <textarea name="description" cols="35" rows="10"
                         wrap="virtual"><%= (description != null) ? description : "" %></textarea>
            </td>
         </tr>
         <td>

      </table>
   </ul>

   <font size="-1"><b>Forum Permission Presets</b></font>
   <ul>
      <font size="-1">
         For finer-grain permission control (including group settings), please view
         the permissions pages.
         <p>
      </font>
      <table cellpadding="3" cellspacing="0" border="0">
         <% for (int i = 0; i < PERM_PRESETS.length; i++) {
            String checked = "";
            if (PERM_PRESETS[i] == permPreset) {
               checked = " checked";
            }
         %>
         <tr>
            <td valign="top"><input type="radio" name="permPreset" value="<%= PERM_PRESETS[i] %>"
                                    id="rb<%= i %>"<%= checked %>></td>
            <td><font size="-1"><label for="rb<%= i %>">
               <b><%= PERM_PRESET_INFO[i][0] %>
               </b>
               --
               <%= PERM_PRESET_INFO[i][1] %>
            </label></font>
            </td>
         </tr>
         <% } %>
      </table>
   </ul>

   <input type="submit" name="create" value="Create Forum">
   <input type="submit" name="cancel" value="Cancel">
</form>

<script language="JavaScript" type="text/javascript">
   <!--
   document.createForm.name.focus();
   //-->
</script>

<%@ include file="footer.jsp" %>
