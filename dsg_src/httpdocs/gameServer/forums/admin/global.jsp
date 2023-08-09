<%
   /**
    *	$RCSfile: global.jsp,v $
    *	$Revision: 1.11.4.1 $
    *	$Date: 2003/02/04 00:14:09 $
    */
%>

<%@ page import="java.lang.reflect.*,
                 java.util.*,
                 com.jivesoftware.forum.*"
%>

<%@ include file="permMethods.jsp" %>

<% // Security check
   AuthToken authToken = (AuthToken) session.getAttribute("jive.admin.authToken");
   if (authToken == null) {
      response.sendRedirect("login.jsp");
      return;
   } else {
      // check for an anonymous user token
      if (authToken.isAnonymous()) {
         response.sendRedirect("login.jsp");
         return;
      }
   }

   // Get the forum factory object.
   ForumFactory forumFactory = ForumFactory.getInstance(authToken);
   // Get the user of this page
   User pageUser = null;
   try {
      pageUser = forumFactory.getUserManager().getUser(authToken.getUserID());
   } catch (Exception e) {
      response.sendRedirect("login.jsp");
      return;
   }

   String onload = "";

   // Role levels for the page user:
   boolean isSystemAdmin = forumFactory.isAuthorized(Permissions.SYSTEM_ADMIN);
   boolean isCatAdmin = isSystemAdmin
      || hasCategoryWithPermission(forumFactory, ForumPermissions.FORUM_CATEGORY_ADMIN);
   boolean isForumAdmin = isSystemAdmin
      || hasForumWithPermission(forumFactory, ForumPermissions.FORUM_ADMIN);
   boolean isGroupAdmin = isSystemAdmin
      || hasGroupWithPermission(forumFactory, Permissions.GROUP_ADMIN);
   boolean isModerator = isSystemAdmin
      || hasForumWithPermission(forumFactory, ForumPermissions.MODERATOR);
   boolean isUserAdmin = isSystemAdmin || forumFactory.isAuthorized(Permissions.USER_ADMIN);

   boolean isPro = false;
   try {
      LicenseManager.validateLicense("Jive Forums Professional", "3.0");
      isPro = Version.getEdition() == Version.Edition.PROFESSIONAL;
   } catch (Exception ignored) {
   }
   boolean isEnt = false;
   try {
      LicenseManager.validateLicense("Jive Forums Enterprise", "3.0");
      isEnt = Version.getEdition() == Version.Edition.ENTERPRISE;
   } catch (Exception ignored) {
   }

   // Determine if the user & group admin is disabled:
   boolean isUserGroupAdminDisabled = "true".equals(JiveGlobals.getJiveProperty("userGroupAdmin.disabled"));
%>

<%! // Global vars/methods for the entire skin

   static final String tblBorderColor = "#aaaaaa";

   // Vars to indicate what permission "mode" we're working in - either we're
   // editing forum perms or category perms. We define these vars here because
   // there are multiple pages in the admin tool that need to link to the
   // permission pages and pass in what mode they're operating in.
   static final int CAT_MODE = 1;
   static final int FORUM_MODE = 2;

   // Vars to indicate what permission "group" we're working in. We can either
   // modify permissions for conetn (read, post, attach, etc) or for admins
   // (system admin, group admin, moderator, etc):
   static final int CONTENT_GROUP = 3;
   static final int ADMIN_GROUP = 4;

   /**
    * Gets a message from the session. The message is removed from the session
    * after we get it.
    */
   private String getOneTimeMessage(HttpSession session, String name) {
      String message = (String) session.getAttribute("jive.admin." + name);
      if (message != null) {
         session.removeAttribute("jive.admin." + name);
         return message;
      }
      return null;
   }

   /**
    * Sets a message in the session. The message is removed from the session
    * after it is accessed (via getOneTimeMessage(...)) once.
    */
   private void setOneTimeMessage(HttpSession session, String name, String value) {
      session.setAttribute("jive.admin." + name, value);
   }

   /**
    * Returns an Iterator of categories this group has read access to. The
    * iterator is in depth-first order of the hierarchy of categories.
    *
    * @param forumFactory
    * @param group the group we are examining.
    * @return an iterator of categories where this group has read permission.
    * @throws UnauthorizedException
    */
   private Iterator categoriesWithGroupRead(ForumFactory forumFactory, Group group)
      throws UnauthorizedException {
      // The read perm - used here to shorten the code:
      long READ = ForumPermissions.READ_FORUM;
      // The root category - again, used here to shorten the code:
      ForumCategory rootCategory = forumFactory.getRootForumCategory();
      // Get a permission manager:
      PermissionsManager globalPermMananger = forumFactory.getPermissionsManager();

      // Start by making a list of group-readable categories - we return an iterator of items
      // in this list:
      java.util.List groupReadableCats = new java.util.LinkedList();

      // Do a special check - if anonymous users have global read perm, that
      // means we can just return an iterator of all the categories in the system
      if (globalPermMananger.anonymousUserHasPermission(READ)) {
         groupReadableCats.add(rootCategory);
         for (Iterator iter = rootCategory.getRecursiveCategories(); iter.hasNext(); ) {
            ForumCategory category = (ForumCategory) iter.next();
            groupReadableCats.add(category);
         }

         return groupReadableCats.iterator();
      }
      // Do another special check - if the group parameter is the same as one of the
      // groups with the global read perm, that means we can just return an iterator
      // of all categories in the system:
      for (Iterator iter = globalPermMananger.groupsWithPermission(READ); iter.hasNext(); ) {
         Group g = (Group) iter.next();
         if (group.getID() == g.getID()) {
            groupReadableCats.add(rootCategory);
            for (Iterator iter2 = rootCategory.getRecursiveCategories(); iter2.hasNext(); ) {
               ForumCategory category = (ForumCategory) iter2.next();
               groupReadableCats.add(category);
            }

            return groupReadableCats.iterator();
         }
      }

      // Getting to this point means we need to drill down in all categories and
      // look for categories where the group has read access:
      // The list of total categories to examine -- this is the root cat plus subcatories:
      java.util.List allCats = new java.util.LinkedList();
      allCats.add(rootCategory);
      for (Iterator iter = rootCategory.getRecursiveCategories(); iter.hasNext(); ) {
         ForumCategory category = (ForumCategory) iter.next();
         allCats.add(category);
      }
      // Now loop through all categories, examine the perms:
      for (int i = 0; i < allCats.size(); i++) {
         ForumCategory category = (ForumCategory) allCats.get(i);
         // Quick check to see if the parent of this category already has the perm:
         boolean parentHasPerm = parentCatHasReadPerm(rootCategory, category, group);
         if (parentHasPerm) {
            groupReadableCats.add(category);
         } else {
            // Get the permission manager for this category:
            PermissionsManager catPermManager = category.getPermissionsManager();

            // everyone has permission to read
            if (catPermManager.anonymousUserHasPermission(READ)) {
               groupReadableCats.add(category);
               // go back to outer for loop
               break;
            }

            // Do the perms check by looping through all groups having read access
            // in this category - when one is found, add the category to the list
            // of group-readable categories.
            for (Iterator groups = catPermManager.groupsWithPermission(READ); groups.hasNext(); ) {
               Group g = (Group) groups.next();
               if (group.getID() == g.getID()) {
                  groupReadableCats.add(category);
                  // go back to outer for loop
                  break;
               }
            }
         }
      }
      // return an iterator of the group-readable categories - might
      // be an empty iterator:
      return groupReadableCats.iterator();
   }

   /**
    * Returns true if any parent category of the specified category has read access
    * to the group.
    *
    * @param rootCat
    * @param cat
    * @param group
    * @return
    * @throws UnauthorizedException
    */
   private boolean parentCatHasReadPerm(ForumCategory rootCat, ForumCategory cat, Group group)
      throws UnauthorizedException {
      ForumCategory parentCat = cat.getParentCategory();
      // when the parent cat is null that means we've hit the parent of the root
      while (parentCat != null) {
         PermissionsManager parentCatPermManager = parentCat.getPermissionsManager();
         // everyone has permission to read
         if (parentCatPermManager.anonymousUserHasPermission(ForumPermissions.READ_FORUM)) {
            return true;
         }

         // check groups who have read to see if any of them are the current group
         for (Iterator groups = parentCatPermManager.groupsWithPermission(ForumPermissions.READ_FORUM);
              groups.hasNext(); ) {
            Group g = (Group) groups.next();
            if (group.getID() == g.getID()) {
               return true;
            }
         }
         parentCat = parentCat.getParentCategory();
      }
      return false;
   }
%>
