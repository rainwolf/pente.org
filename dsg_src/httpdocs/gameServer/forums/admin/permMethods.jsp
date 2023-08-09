<%
   /**
    *	$RCSfile: permMethods.jsp,v $
    *	$Revision: 1.9 $
    *	$Date: 2002/12/20 22:50:59 $
    */
%>

<%@ page import="java.util.*,
                 com.jivesoftware.forum.*,
                 com.jivesoftware.base.*,
                 com.jivesoftware.base.util.JiveComparators"
%>

<%! // Global vars, methods, etc

   // User Perms
   static final long READ_FORUM = ForumPermissions.READ_FORUM;
   static final long CREATE_THREAD = ForumPermissions.CREATE_THREAD;
   static final long CREATE_MESSAGE = ForumPermissions.CREATE_MESSAGE;
   static final long MODERATOR = ForumPermissions.MODERATOR;
   static final long CREATE_MESSAGE_ATTACHMENT = ForumPermissions.CREATE_MESSAGE_ATTACHMENT;
   // Admin Perms
   static final long SYSTEM_ADMIN = Permissions.SYSTEM_ADMIN;
   static final long CAT_ADMIN = ForumPermissions.FORUM_CATEGORY_ADMIN;
   static final long FORUM_ADMIN = ForumPermissions.FORUM_ADMIN;
   static final long GROUP_ADMIN = Permissions.GROUP_ADMIN;
   static final long USER_ADMIN = Permissions.USER_ADMIN;

   /**
    * Returns a list of forums where the user has the given privilege type.
    * If the user doesn't have the given privilege on at least 1 forum, a
    * list of size zero is returned.
    */
   private boolean hasForumWithPermission(ForumFactory forumFactory, long type) {
      boolean hasPerm = false;
      // Iterator through all forums:
      for (Iterator iter = forumFactory.getRootForumCategory().getRecursiveForums(); iter.hasNext(); ) {
         Forum forum = (Forum) iter.next();
         if (forumFactory.isAuthorized(type) || forum.isAuthorized(type)) {
            hasPerm = true;
            break;
         }
      }
      return hasPerm;
   }

   /**
    * Returns a list of forums where the user has the given privilege type.
    * If the user doesn't have the given privilege on at least 1 forum, a
    * list of size zero is returned.
    */
   private java.util.List forumsWithPermission(ForumFactory forumFactory, long type) {
      java.util.List forums = new java.util.LinkedList();
      // Iterator through all forums:
      for (Iterator iter = forumFactory.getRootForumCategory().getRecursiveForums(); iter.hasNext(); ) {
         Forum forum = (Forum) iter.next();
         if (forumFactory.isAuthorized(type) || forum.isAuthorized(type)) {
            forums.add(forum);
         }
      }
      return forums;
   }

   /**
    * Returns a list of forums where the user has the given privilege type.
    * If the user doesn't have the given privilege on at least 1 forum, a
    * list of size zero is returned.
    */
   private boolean hasCategoryWithPermission(ForumFactory forumFactory, long type) {
      ForumCategory rootCategory = forumFactory.getRootForumCategory();
      java.util.List catsWithPerm = new java.util.LinkedList();
      getCatsWithPerm(type, rootCategory, catsWithPerm, true);
      return catsWithPerm.size() > 0;
   }

   /**
    * Returns a list of forums where the user has the given privilege type.
    * If the user doesn't have the given privilege on at least 1 forum, a
    * list of size zero is returned.
    */
   private java.util.List categoriesWithPermission(ForumFactory forumFactory, long type) {
      ForumCategory rootCategory = forumFactory.getRootForumCategory();
      java.util.List catsWithPerm = new java.util.LinkedList();
      getCatsWithPerm(type, rootCategory, catsWithPerm, false);
      return catsWithPerm;
   }

   /**
    * Recursively builds a list of categories where the user has the
    * specified permission.
    */
   private void getCatsWithPerm(long type, ForumCategory category, java.util.List catsWithPerm, boolean shortCircuit) {
      Iterator iter = category.getCategories();
      if (!iter.hasNext()) {
         return;
      } else {
         java.util.List categories = new java.util.LinkedList();
         while (iter.hasNext()) {
            ForumCategory subCategory = (ForumCategory) iter.next();
            if (subCategory.isAuthorized(type)) {
               catsWithPerm.add(subCategory);
               if (shortCircuit) {
                  break;
               }
            }
            getCatsWithPerm(type, subCategory, catsWithPerm, shortCircuit);
         }
      }
   }

   /**
    * Returns a list of all forums where the user is a moderator. Moderation
    * may or may not be turned on in these forums.
    * If the user doesn't have those permissions on at least 1 forum, a
    * list of size zero is returned.
    */
   private java.util.List moderatedForums(ForumFactory forumFactory) {
      return moderatedForums(forumFactory, false);
   }

   /**
    * This method will return a list of forums where the user is a moderator
    * and where moderation is turned on if moderationEnabled is true.
    * If the user doesn't have those permissions on at least 1 forum, a
    * list of size zero is returned.
    */
   private java.util.List moderatedForums(ForumFactory forumFactory, boolean moderationEnabled) {
      // Get the root category
      ForumCategory rootCategory = forumFactory.getRootForumCategory();
      // The list of forums to return:
      java.util.List forums = new java.util.LinkedList();
      // Loop through all forums
      for (Iterator iter = rootCategory.getRecursiveForums(); iter.hasNext(); ) {
         Forum forum = (Forum) iter.next();
         if (forumFactory.isAuthorized(Permissions.SYSTEM_ADMIN)
            || forum.isAuthorized(ForumPermissions.FORUM_ADMIN)
            || forum.isAuthorized(ForumPermissions.MODERATOR)) {
            if (!moderationEnabled) {
               forums.add(forum);
            } else {
               // Check to see if modeation is enabled. If so, add this
               // forum to the list.
               boolean isThreadModOn = (
                  forum.getModerationDefaultThreadValue() < forum.getModerationMinThreadValue()
               );
               boolean isMessageModOn = (
                  forum.getModerationDefaultMessageValue() < forum.getModerationMinMessageValue()
               );
               if (isThreadModOn || isMessageModOn) {
                  forums.add(forum);
               }
            }
         }
      }
      return forums;
   }

   /**
    * Returns a list of groups where the user has the given privilege type.
    * If the user doesn't have the given privilege on at least 1 group, a
    * list of size zero is returned.
    */
   private boolean hasGroupWithPermission(ForumFactory forumFactory, long type) {
      return groupsWithPermission(forumFactory, type, true).size() > 0;
   }

   /**
    * Returns a list of groups where the user has the given privilege type.
    * If the user doesn't have the given privilege on at least 1 group, a
    * list of size zero is returned.
    */
   private java.util.List groupsWithPermission(ForumFactory forumFactory, long type, boolean shortCircuit) {
      java.util.List groups = new java.util.LinkedList();
      GroupManager groupManager = forumFactory.getGroupManager();
      for (Iterator iter = groupManager.getGroups(); iter.hasNext(); ) {
         Group group = (Group) iter.next();
         if (group.isAuthorized(type)) {
            groups.add(group);
            if (shortCircuit) {
               break;
            }
         }
      }
      return groups;
   }

   /**
    * Returns an Iterator of users with permission types of both
    * MODERATE_THREADS and MODERATE_MESSAGES
    */
   private Iterator getUserModerators(PermissionsManager permManager) {
      // Temporarily hold all moderators in a hashmap so when we combine the
      // thread & message moderators, duplicates will be ignored.
      Map moderators = new HashMap();

      // add thread moderators (user)
      for (Iterator iter = permManager.usersWithPermission(MODERATOR); iter.hasNext(); ) {
         User user = (User) iter.next();
         moderators.put(user.getUsername(), user);
      }
      // add message moderators (user)
      for (Iterator iter = permManager.usersWithPermission(MODERATOR); iter.hasNext(); ) {
         User user = (User) iter.next();
         moderators.put(user.getUsername(), user);
      }

      // Sort the user moderators list
      java.util.List moderatorList = new ArrayList(moderators.values());
      Object[] moderatorArray = moderatorList.toArray();
      Arrays.sort(moderatorArray, JiveComparators.USER);

      return (Arrays.asList(moderatorArray)).iterator();
   }

   /**
    * Returns an iterator of users with permission types of both
    * MODERATE_THREADS and MODERATE_MESSAGES
    */
   private Iterator getGroupModerators(PermissionsManager permManager) {
      // Temporarily hold all moderators in a hashmap so when we combine the
      // thread & message moderators, duplicates will be ignored.
      Map moderators = new HashMap();

      // add thread moderators (groups)
      for (Iterator iter = permManager.groupsWithPermission(MODERATOR); iter.hasNext(); ) {
         Group group = (Group) iter.next();
         moderators.put(group.getName(), group);
      }
      // add message moderators (groups)
      for (Iterator iter = permManager.groupsWithPermission(MODERATOR); iter.hasNext(); ) {
         Group group = (Group) iter.next();
         moderators.put(group.getName(), group);
      }

      // Sort the user moderators list
      java.util.List moderatorList = new ArrayList(moderators.values());
      Object[] moderatorArray = moderatorList.toArray();
      Arrays.sort(moderatorArray, new Comparator() {
         public int compare(Object o1, Object o2) {
            Group g1 = (Group) o1;
            Group g2 = (Group) o2;
            return (g1.getName().compareTo(g2.getName()));
         }
      });

      return (Arrays.asList(moderatorArray)).iterator();
   }
%>
