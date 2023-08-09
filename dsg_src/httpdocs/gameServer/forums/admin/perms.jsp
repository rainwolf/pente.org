<%
   /**
    *	$RCSfile: perms.jsp,v $
    *	$Revision: 1.6.4.1 $
    *	$Date: 2003/01/17 18:17:05 $
    */
%>

<%@ page import="java.util.*,
                 java.net.URLEncoder,
                 com.jivesoftware.forum.*,
                 com.jivesoftware.forum.util.*,
                 com.jivesoftware.util.ParamUtils"
         errorPage="error.jsp"
%>

<%@ include file="global.jsp" %>

<%! // Global variables, methods, etc

   // ALL CONSTANTS REFERRED TO IN THIS FILE ARE DEFINED IN
   // permMethods.jsp

   static final Map permNames = new HashMap();

   static {
      permNames.put(new Long(READ_FORUM), "Read Forum");
      permNames.put(new Long(CREATE_THREAD), "Create Thread");
      permNames.put(new Long(CREATE_MESSAGE), "Create Message");
      permNames.put(new Long(MODERATOR), "Moderator");
      permNames.put(new Long(CREATE_MESSAGE_ATTACHMENT), "Create Attachment");
      permNames.put(new Long(SYSTEM_ADMIN), "System Admin");
      permNames.put(new Long(CAT_ADMIN), "Category Admin");
      permNames.put(new Long(FORUM_ADMIN), "Forum Admin");
      permNames.put(new Long(GROUP_ADMIN), "Group Admin");
      permNames.put(new Long(USER_ADMIN), "User Admin");
   }

   // types of users/groups to give permissions to
   static final int ANYBODY = 1;
   static final int REGISTERED = 2;
   static final int USER = 3;
   static final int GROUP = 4;

   // anonymous user & special user constants
   static final long GUEST_ID = -1L;
   static final long REGISTERED_ID = 0L;

   // Returns a list of userIDs in the given list of userIDs and groupIDs.
   private long[] getUserIDs(String[] items) {
      return getIDs(items, "u");
   }

   // Returns a list of groupIDs in the given list of userIDs and groupIDs.
   private long[] getGroupIDs(String[] items) {
      return getIDs(items, "g");
   }

   private long[] getIDs(String[] items, String prefix) {
      if (items == null) {
         return new long[0];
      }
      long[] IDs = new long[items.length];
      int size = 0;
      for (int i = 0; i < items.length; i++) {
         String item = items[i];
         if (item != null && item.startsWith(prefix)) {
            try {
               IDs[size] = Long.parseLong(item.substring(1, item.length()));
               size++;
            } catch (Exception e) {
            }
         }
      }
      if (size == IDs.length) {
         return IDs;
      } else {
         long[] temp = new long[size];
         for (int i = 0; i < temp.length; i++) {
            temp[i] = IDs[i];
         }
         return temp;
      }
   }
%>

<% // Permission check
   if (!isSystemAdmin && !isForumAdmin && !isCatAdmin
      && Version.getEdition() == Version.Edition.LITE) {
      throw new UnauthorizedException("You don't have admin privileges to perform this operation.");
   }

   // Check to see what mode we're in. We're either editing forum or category
   // permissions. If the mode is something else, throw an exception:
   int mode = ParamUtils.getIntParameter(request, "mode", -1);
   if (mode != CAT_MODE && mode != FORUM_MODE) {
      throw new Exception("No permission mode specified");
   }

   // Check to see what group of perms we're going to administer. We can
   // either modify user & group perms or admin permissions.
   int permGroup = ParamUtils.getIntParameter(request, "permGroup", -1);
   if (permGroup != CONTENT_GROUP && permGroup != ADMIN_GROUP) {
      throw new Exception("No permission group specified");
   }

   // Get parameters
   long forumID = ParamUtils.getLongParameter(request, "forum", -1L);
   long categoryID = ParamUtils.getLongParameter(request, "cat", -1L);
   long[] permissions = ParamUtils.getLongParameters(request, "permission", -1L);
   int givePermTo = ParamUtils.getIntParameter(request, "givePermTo", -1);
   boolean add = request.getParameter("add") != null;
   boolean remove = request.getParameter("remove") != null;
   String username = ParamUtils.getParameter(request, "username");
   String groupname = ParamUtils.getParameter(request, "groupname");
   String[] items = request.getParameterValues("items");
   long[] itemType = ParamUtils.getLongParameters(request, "itemType", -1L);

   // Further permission checks. Check if we're in "admin" perm editing mode
   // that a forum admin can't admin global perms:
   if (permGroup == ADMIN_GROUP && forumID != -1L && !isForumAdmin) {
      // Forum admins can't administrate global permissions
      throw new UnauthorizedException("You don't have admin privileges to perform this operation.");
   }

   // UserManager for getting and setting users or lists of users
   UserManager userManager = forumFactory.getUserManager();

   // GroupManager for getting and setting groups or list of groups
   GroupManager groupManager = forumFactory.getGroupManager();

   // Load the forum
   Forum forum = null;
   if (forumID != -1L) {
      forum = forumFactory.getForum(forumID);
   }

   // Load the category
   ForumCategory category = null;
   if (categoryID != -1L) {
      category = forumFactory.getForumCategory(categoryID);
   }

   // Get the permissions manager for the appropriate mode we're in:
   PermissionsManager permManager = null;
   if (mode == CAT_MODE) {
      permManager = category.getPermissionsManager();
   } else if (mode == FORUM_MODE) {
      // The forums permission manager can either be global or local:
      if (forum == null) {
         // Since no forum was specified, we'll get the perm manager from
         // the forum factory so we can edit global permissions:
         permManager = forumFactory.getPermissionsManager();
      } else {
         permManager = forum.getPermissionsManager();
      }
   }

   // Create the group of permissions we're going to administer:
   long[] permGroupDef = null;
   if (permGroup == CONTENT_GROUP) {
      permGroupDef = new long[]{
         READ_FORUM, CREATE_THREAD, CREATE_MESSAGE, CREATE_MESSAGE_ATTACHMENT
      };
   } else if (permGroup == ADMIN_GROUP) {
      if (forum == null) {
         if (category == null) {
            permGroupDef = new long[]{
               SYSTEM_ADMIN, CAT_ADMIN, FORUM_ADMIN, USER_ADMIN, MODERATOR
            };
         } else {
            permGroupDef = new long[]{
               CAT_ADMIN, FORUM_ADMIN, /*USER_ADMIN,*/ MODERATOR
            };
         }
      } else {
         permGroupDef = new long[]{
            FORUM_ADMIN, MODERATOR
         };
      }
   }

   // add a new permission if requested
   if (add) {
      // Check to see that the user clicked a permission radio button and
      // selected at least one permission
      if (givePermTo > 0 && (permissions != null && permissions.length > 0)) {
         switch (givePermTo) {
            case ANYBODY:
               // Add anonymous user permissions
               for (int i = 0; i < permissions.length; i++) {
                  // Don't support setting moderator privleges for anonymous users
                  if (permissions[i] != MODERATOR) {
                     permManager.addAnonymousUserPermission(permissions[i]);
                  }
               }
               break;
            case REGISTERED:
               // Add registered user permissions
               for (int i = 0; i < permissions.length; i++) {
                  // Don't support setting moderator privleges for "registered users"
                  if (permissions[i] != MODERATOR) {
                     permManager.addRegisteredUserPermission(permissions[i]);
                  }
               }
               break;
            case USER:
               try {
                  // get the user:
                  User user = userManager.getUser(username);
                  // Add the user permission
                  for (int i = 0; i < permissions.length; i++) {
                     if (permissions[i] == MODERATOR) {
                        permManager.addUserPermission(user, MODERATOR);
                     } else {
                        permManager.addUserPermission(user, permissions[i]);
                     }
                  }
               } catch (Exception ignored) {
               }
               break;
            case GROUP:
               try {
                  // get the user:
                  Group group = groupManager.getGroup(groupname);
                  // Add the user permission
                  for (int i = 0; i < permissions.length; i++) {
                     if (permissions[i] == MODERATOR) {
                        permManager.addGroupPermission(group, MODERATOR);
                     } else {
                        permManager.addGroupPermission(group, permissions[i]);
                     }
                  }
               } catch (Exception ignored) {
               }
               break;
            default:
         }
      }

      // done adding, so redirect back to this page
      response.sendRedirect("perms.jsp?forum=" + forumID + "&mode=" + mode + "&cat="
         + categoryID + "&permGroup=" + permGroup);
      return;
   }

   // remove a permission if requested
   if (remove) {
      // Get the lists of user IDs and group IDs.
      long[] userIDs = getUserIDs(items);
      long[] groupIDs = getGroupIDs(items);

      // if there are user perms or group perms to remove:
      if (userIDs.length > 0 || groupIDs.length > 0) {
         // users first,
         for (int i = 0; i < userIDs.length; i++) {
            if (userIDs[i] == GUEST_ID) {
               for (int j = 0; j < itemType.length; j++) {
                  if (itemType[j] == MODERATOR) {
                     permManager.removeAnonymousUserPermission(MODERATOR);
                  } else {
                     permManager.removeAnonymousUserPermission(itemType[j]);
                  }
               }
            } else if (userIDs[i] == REGISTERED_ID) {
               for (int j = 0; j < itemType.length; j++) {
                  if (itemType[j] == MODERATOR) {
                     permManager.removeRegisteredUserPermission(MODERATOR);
                  } else {
                     permManager.removeRegisteredUserPermission(itemType[j]);
                  }
               }
            } else {
               try {
                  User user = userManager.getUser(userIDs[i]);
                  for (int j = 0; j < itemType.length; j++) {
                     // only remove this permission if:
                     // 1) If the admin tool user is not "user"
                     // 2) and if itemType[j] is not the SYSTEM_ADMIN priv

                     if (pageUser.getID() != user.getID() || itemType[j] != Permissions.SYSTEM_ADMIN) {
                        if (itemType[j] == MODERATOR) {
                           permManager.removeUserPermission(user, MODERATOR);
                        } else {
                           permManager.removeUserPermission(user, itemType[j]);
                        }
                     }
                  }
               } catch (Exception ignored) {
               }
            }
         }
         // groups next
         for (int i = 0; i < groupIDs.length; i++) {
            try {
               Group group = groupManager.getGroup(groupIDs[i]);
               for (int j = 0; j < itemType.length; j++) {
                  if (itemType[j] == MODERATOR) {
                     permManager.removeGroupPermission(group, MODERATOR);
                  } else {
                     permManager.removeGroupPermission(group, itemType[j]);
                  }
               }
            } catch (Exception ignored) {
            }
         }
      }

      // done removing, so redirect back to this page
      response.sendRedirect("perms.jsp?forum=" + forumID + "&mode=" + mode + "&cat="
         + categoryID + "&permGroup=" + permGroup);
      return;
   }
%>

<% // special onload command to load the sidebar
   if (forum != null) {
      onload = " onload=\"parent.frames['sidebar'].location.href='sidebar.jsp?sidebar=forum';\"";
   }
   if (category != null) {
      onload = " onload=\"parent.frames['sidebar'].location.href='sidebar.jsp?sidebar=forum';\"";
   }
   String bookmark = "";
   if (permGroup == ADMIN_GROUP) {
      bookmark = "admin_perm_group";
   } else if (permGroup == CONTENT_GROUP) {
      bookmark = "content_perm_group";
   }
%>
<%@ include file="header.jsp" %>

<p>

      <%  // Title of this page and breadcrumbs
    String title = null;
    String[][] breadcrumbs = null;
    // Print out different breadcrumbs depending if we're editing forum or
    // category permissions or if we're editing any admin perms.
    if (permGroup == ADMIN_GROUP) {
        title = "Administrators &amp; Moderators";
        if (forum == null) {
            breadcrumbs = new String[][] {
                {"Main", "main.jsp"},
                {"Content", "forums.jsp"},
                {"Admins &amp; Moderators", "perms.jsp?permGroup="+permGroup+"&mode="+mode
                    + ((category!=null)?("&cat="+category.getID()):("")) }
            };
        }
        else {
            breadcrumbs = new String[][] {
                {"Main", "main.jsp"},
                {"Admins &amp; Moderators", "perms.jsp?permGroup="+permGroup+"&mode="+mode}
            };
        }
    }
    else if (permGroup == CONTENT_GROUP) {
        if (mode == CAT_MODE) {
            title = "Category Permissions";
            breadcrumbs = new String[][] {
                {"Main", "main.jsp"},
                {"Categories &amp; Forums", "forums.jsp"},
                {title, "perms.jsp?cat="+categoryID+"&mode="+mode+"&permGroup="+permGroup}
            };
        }
        else if (mode == FORUM_MODE) {
            // Show different breadcrumbs for global or local forums:
            if (forum == null) {
                title = "Global Forum Permissions";
                breadcrumbs = new String[][] {
                    {"Main", "main.jsp"},
                    {title, "perms.jsp?forum="+forumID+"&mode="+mode+"&permGroup="+permGroup}
                };
            }
            else {
                title = "Forum Permissions";
                breadcrumbs = new String[][] {
                    {"Main", "main.jsp"},
                    {"Categories &amp; Forums", "forums.jsp"},
                    {"Edit Forum", "editForum.jsp?forum="+forumID},
                    {title, "perms.jsp?forum="+forumID+"&mode="+mode+"&permGroup="+permGroup}
                };
            }
        }
    }
%>
   <%@ include file="title.jsp" %>

   <font size="-1">
      <% if (permGroup == ADMIN_GROUP) { %>

      <% if (forum != null) { %>

      Grant forum administrator privileges to users or groups for this forum.

      <% } else { // global %>

      Grant global forum admin or system admin privileges to users or groups.
      Note, this sets permission for admins over all forums. To designate
      administrators for individual forums, click on the "Content" tab,
      choose a forum then choose "Admins/Moderators" from the left menu.

      <% } %>

      Permissions are always additive, such that the final permissions for a forum
      will be global permissions, plus forum specific permissions.

      <% } else if (permGroup == CONTENT_GROUP) { %>

      <% if (mode == CAT_MODE) { %>

      Modify the permissions for categories.

      <% } else if (mode == FORUM_MODE) { %>

      <% if (forum == null) { %>

      Edit global permissions to set the permissions policies that all of your
      forums will use.

      <% } else { %>

      Permissions are always additive, such that the final permissions for a
      forum will be global permissions, plus forum specific permissions.

      <% } %>

      <% } %>

      <% } %>
   </font>

<p>

      <%  if (permGroup == ADMIN_GROUP) { %>

   <b>Permission Summary</b>

      <%  } else if (permGroup == CONTENT_GROUP) { %>

      <%  if (mode == CAT_MODE) { %>

   <b>Permission Summary for Category: <%= category.getName() %>
   </b>

      <%  } else if (mode == FORUM_MODE) { %>

      <%  if (forum != null) { %>

   <b>Permission Summary for Forum: <%= forum.getName() %>
   </b>

      <%  } else { %>

   <b>Global Permission Summary</b>

      <%  } %>

      <%  } %>

      <%  } %>

<ul>
   <font size="-1">
      Below is a summary of permissions associated with various users and groups.
      To remove a permission, select the user or group and click "Remove".
      <p>
   </font>
   <table bgcolor="<%= tblBorderColor %>" cellpadding="0" cellspacing="0" border="0" width="90%">
      <tr>
         <td>
            <table bgcolor="<%= tblBorderColor %>" cellpadding="0" cellspacing="1" border="0" width="100%">
               <tr bgcolor="#eeeeee">
                  <% // Loop through the perm group, display the table headers:
                     for (int i = 0; i < permGroupDef.length; i++) {
                        String header = ((String) permNames.get(new Long(permGroupDef[i]))).toUpperCase();
                  %>
                  <td align="center">
                     <table cellpadding="3" cellspacing="0" border="0">
                        <tr>
                           <td><font size="-2" face="verdana"><b><%= header %>
                           </b></font></td>
                        </tr>
                     </table>
                  </td>
                  <% } %>
               </tr>
               <tr bgcolor="#ffffff">
                  <% // Loop through the perm group array again, this time print the box
                     // showing the list of users/groups that have the certain perm:
                     // (do a special check for the moderator type b/c we need to print out a message
                     // which says we don't support moderator perms for anony & "registered" users.
                     boolean wasModeratorPerm = false;
                     for (int i = 0; i < permGroupDef.length; i++) {
                        long perm = permGroupDef[i];
                        if (perm == MODERATOR) {
                           wasModeratorPerm = true;
                        }
                        Iterator iter = null;
                  %>
                  <form action="perms.jsp" method="post">
                     <input type="hidden" name="mode" value="<%= mode %>">
                     <input type="hidden" name="cat" value="<%= categoryID %>">
                     <input type="hidden" name="forum" value="<%= forumID %>">
                     <input type="hidden" name="remove" value="true">
                     <input type="hidden" name="itemType" value="<%= perm %>">
                     <input type="hidden" name="permGroup" value="<%= permGroup %>">
                     <td align="center">
                        <table cellpadding="3" cellspacing="0" border="0">
                           <tr>
                              <td>
                                 <select size="5" name="items" multiple
                                         onchange="">
                                    <% if (permManager.anonymousUserHasPermission(perm)) { %>
                                    <option value="u-1">* Anybody
                                          <%  } %>
                                          <%	if (permManager.registeredUserHasPermission(perm)) { %>
                                    <option value="u0">* Registered Users
                                          <%  } %>
                                          <%  if (perm == MODERATOR) {
                iter = getUserModerators(permManager);
            }
            else {
                iter = permManager.usersWithPermission(perm);
            }
            while (iter.hasNext()) {
                User user = (User)iter.next();
        %>
                                    <option value="u<%= user.getID() %>"><%= user.getUsername() %>
                                          <%  } %>
                                          <%  if (perm == MODERATOR) {
                iter = getGroupModerators(permManager);
            }
            else {
                iter = permManager.groupsWithPermission(perm);
            }
            while (iter.hasNext()) {
                Group group = (Group)iter.next();
        %>
                                    <option value="g<%= group.getID() %>"><%= group.getName() %>
                                          <%  } %>
                                 </select>
                              </td>
                           </tr>
                        </table>
                        <img src="images/line_gray.gif" width="100%" height="5" border="0"
                        >
                        <table cellpadding="3" cellspacing="0" border="0">
                           <tr>
                              <td
                              ><font size="-1"><input type="submit" value="Remove"></font></td>
                           </tr>
                        </table>
                     </td>
                  </form>
                  <% } // end for %>
               </tr>
            </table>
         </td>
      </tr>
   </table>
   <% if (wasModeratorPerm) { %>
   <font size="-2" face="verdana">
      <br>
      Note: Moderator permissions are not supported for "anonymous" or "registered users" user types.
   </font>
   <% } %>
</ul>

<form method="post" action="perms.jsp" name="addForm">
   <input type="hidden" name="mode" value="<%= mode %>">
   <input type="hidden" name="cat" value="<%= categoryID %>">
   <input type="hidden" name="add" value="true">
   <input type="hidden" name="forum" value="<%= forumID %>">
   <input type="hidden" name="permGroup" value="<%= permGroup %>">

   <font size="-1"><b>Grant Permissions</b></font>
   <ul>
      <font size="-1">Use the form below to grant a permission to guests, all users, a specific user or a specific
         group.</font>
      <p>
      <table cellpadding="0" cellspacing="0" border="0">
         <tr>
            <td>
               <font size="-1"><b>Grant Permission To:</b></font>
            </td>
            <td>
               <a href="#" onclick="helpwin('perms','grant_to');return false;"
                  title="Click for help"
               ><img src="images/help-16x16.gif" width="16" height="16" hspace="8" border="0"></a>
            </td>
         </tr>
      </table>
      <ul>
         <table cellpadding="2" cellspacing="0" border="0">
            <tr>
               <td><input type="radio" name="givePermTo" value="<%= ANYBODY %>" id="e1"></td>
               <td colspan="2"><font size="-1"><label for="e1">Anybody (including guests)</label></font></td>
            </tr>
            <tr>
               <td><input type="radio" name="givePermTo" value="<%= REGISTERED %>" id="e2"></td>
               <td colspan="2"><font size="-1"><label for="e2">Registered Users</label></font></td>
            </tr>
            <tr>
               <td><input type="radio" name="givePermTo" value="<%= USER %>" id="e3"></td>
               <td><font size="-1"><label for="e3">User:</label></font></td>
               <td><input type="text" name="username" value="(enter username)" size="20"
                          onclick="this.select();document.addForm.givePermTo[2].checked=true;"></td>
            </tr>
            <tr>
               <td><input type="radio" name="givePermTo" value="<%= GROUP %>" id="e4"></td>
               <td><font size="-1"><label for="e4">Group:</label></font></td>
               <td><input type="text" name="groupname" value="(enter group name)" size="20"
                          onclick="this.select();document.addForm.givePermTo[3].checked=true;"></td>
            </tr>
         </table>
      </ul>
      <p>

      <table cellpadding="0" cellspacing="0" border="0">
         <tr>
            <td>
               <font size="-1"><b>Permission:</b></font>
            </td>
            <td>
               <a href="#" onclick="helpwin('perms','<%= bookmark %>');return false;"
                  title="Click for help"
               ><img src="images/help-16x16.gif" width="16" height="16" hspace="8" border="0"></a>
            </td>
         </tr>
      </table>
      <ul>
         <table cellpadding="2" cellspacing="0" border="0">
            <% // Loop through the perm group array, print out the perms we can set
               for (int i = 0; i < permGroupDef.length; i++) {
                  long perm = permGroupDef[i];
                  String permName = (String) permNames.get(new Long(perm));
            %>
            <tr>
               <td><input type="checkbox" name="permission" value="<%= perm %>" id="<%= (i+1) %>"></td>
               <td><font size="-1"><label for="<%= (i+1) %>"><%= permName %>
               </label></font></td>
            </tr>
            <% } // end for %>
         </table>
      </ul>
      <p>
            <%  if (permGroup == ADMIN_GROUP) { %>

            <%  if (forum != null) { %>

         <input type="submit" value="Grant Admin Permission">

            <%  } else { // global %>

         <input type="submit" value="Grant Global Admin Permission">

            <%  } %>

            <%  } else if (permGroup == CONTENT_GROUP) { %>

            <%  if (mode == CAT_MODE) { %>

         <input type="submit" value="Grant Permission">

            <%  } else if (mode == FORUM_MODE) { %>

            <%  if (forum != null) { %>

         <input type="submit" value="Grant Forum Permission">

            <%  } else { %>

         <input type="submit" value="Grant Global Forum Permission">

            <%  } %>

            <%  } %>

            <%  } %>
   </ul>

</form>

<p>

   <%@ include file="footer.jsp" %>
