<%
   /**
    *    $RCSfile: cacheUtils.jsp,v $
    *    $Revision: 1.4.4.1 $
    *    $Date: 2003/02/04 16:09:26 $
    */
%>

<%@ page import="java.util.*,
                 java.text.*,
                 com.jivesoftware.util.*,
                 com.jivesoftware.forum.*,
                 com.jivesoftware.forum.database.*,
                 com.jivesoftware.forum.util.*,
                 com.tangosol.net.*,
                 com.jivesoftware.base.*"
%>

<%! // global variables

   // variable for the VM memory monitor box:
   static final int NUM_BLOCKS = 50;

   // Cache size preset definitions:
   static final int CACHE_PRESET_SMALL = 0;
   static final int CACHE_PRESET_MEDIUM = 1;
   static final int CACHE_PRESET_LARGE = 2;
   static final int CACHE_PRESET_CUSTOM = 3;

   // Cache size preset names:
   static final String[] CACHE_PRESET_NAMES = {
      "Small",
      "Medium",
      "Large",
      "Custom"
   };

   // Cache size preset descriptions:
   static final String[] CACHE_PRESET_DESCRIPTIONS = {
      "Suitable for workgroups or small to medium communities. (Approx 4 MB total cache)",
      "Suitable for medium to large communities. (17 MB total cache)",
      "Suitable for very large communities on servers with a lot of memory. (65 MB total cache)",
      "Use this option to be able to edit each of the cache sizes below."
   };
%>

<% // Get a db forum factory instance:
   DbForumFactory dbForumFactory = DbForumFactory.getInstance();

   // Get a cache manager:
   DatabaseCacheManager cacheManager = dbForumFactory.getCacheManager();

   int numCaches = 11; // all caches
   if (!cacheManager.isShortTermQueryCacheEnabled()) {
      // don't show the query cache unless it is enabled:
      numCaches = 10;
   }

   // Various cache arrays
   int[][] cachePresetSizes = null;
   Cache[] caches = null;
   String[] names = null;

   if (cacheManager.isShortTermQueryCacheEnabled() && Version.EDITION == Version.Edition.ENTERPRISE) {
      // Preset mem sizes (in bytes):
      //     SMALL      MEDIUM      LARGE
      cachePresetSizes = new int[][]{
         {256 * 1024, 512 * 1024, 1024 * 1024},  /* Category */
         {512 * 1024, 1024 * 1024, 5120 * 1024},  /* Forum */
         {512 * 1024, 3072 * 1024, 10240 * 1024},  /* Thread */
         {1152 * 1024, 8192 * 1024, 32768 * 1024},  /* Message */
         {512 * 1024, 1024 * 1024, 2048 * 1024},  /* Category Query */
         {128 * 1024, 256 * 1024, 512 * 1024},  /* Short Term Query */
         {512 * 1024, 1024 * 1024, 6144 * 1024},  /* User */
         {64 * 1024, 128 * 1024, 256 * 1024},  /* User ID */
         {256 * 1024, 768 * 1024, 4608 * 1024},  /* User Permissions */
         {128 * 1024, 256 * 1024, 512 * 1024},  /* Group */
         {16 * 1024, 32 * 1024, 64 * 1024},  /* Group Membership */
         {128 * 1024, 256 * 1024, 512 * 1024}   /* Watches */
      };
      // Get a list of caches:
      caches = new Cache[]{
         cacheManager.categoryCache,
         cacheManager.forumCache,
         cacheManager.threadCache,
         cacheManager.messageCache,
         (Cache) cacheManager.queryCache,
         cacheManager.shortTermQueryCache,
         UserManagerFactory.userCache,
         UserManagerFactory.userIDCache,
         cacheManager.userPermsCache,
         GroupManagerFactory.groupCache,
         GroupManagerFactory.groupMemberCache,
         cacheManager.watchCache
      };
      // Get a list of caches:
      names = new String[]{
         "categoryCache",
         "forumCache",
         "threadCache",
         "messageCache",
         "queryCache",
         "shortTermQueryCache",
         "userCache",
         "userIDCache",
         "userPermsCache",
         "groupCache",
         "groupMemberCache",
         "watchCache"
      };
   } else {
      // Preset mem sizes (in bytes):
      //     SMALL      MEDIUM      LARGE
      cachePresetSizes = new int[][]{
         {256 * 1024, 512 * 1024, 1024 * 1024},  /* Category */
         {512 * 1024, 1024 * 1024, 5120 * 1024},  /* Forum */
         {512 * 1024, 3072 * 1024, 10240 * 1024},  /* Thread */
         {1152 * 1024, 8192 * 1024, 32768 * 1024},  /* Message */
         {64 * 1024, 128 * 1024, 256 * 1024},  /* Attachment */
         {512 * 1024, 1024 * 1024, 2048 * 1024},  /* Category Query */
         {512 * 1024, 1024 * 1024, 6144 * 1024},  /* User */
         {64 * 1024, 128 * 1024, 256 * 1024},  /* User ID */
         {256 * 1024, 768 * 1024, 4608 * 1024},  /* User Permissions */
         {64 * 1024, 128 * 1024, 256 * 1024},  /* User Message Count */
         {128 * 1024, 256 * 1024, 512 * 1024},  /* Group */
         {16 * 1024, 32 * 1024, 64 * 1024},  /* Group ID */
         {16 * 1024, 32 * 1024, 64 * 1024},  /* Group Membership */
         {128 * 1024, 256 * 1024, 512 * 1024}   /* Watches */
      };
      // Get a list of caches:
      caches = new Cache[]{
         cacheManager.categoryCache,
         cacheManager.forumCache,
         cacheManager.threadCache,
         cacheManager.messageCache,
         cacheManager.attachmentCache,
         (Cache) cacheManager.queryCache,
         UserManagerFactory.userCache,
         UserManagerFactory.userIDCache,
         cacheManager.userPermsCache,
         cacheManager.userMessageCountCache,
         GroupManagerFactory.groupCache,
         GroupManagerFactory.groupIDCache,
         GroupManagerFactory.groupMemberCache,
         cacheManager.watchCache
      };
      // Get a list of caches:
      names = new String[]{
         "categoryCache",
         "forumCache",
         "threadCache",
         "messageCache",
         "attachmentCache",
         "queryCache",
         "userCache",
         "userIDCache",
         "userPermsCache",
         "userMsgCountCache",
         "groupCache",
         "groupIDCache",
         "groupMemberCache",
         "watchCache"
      };
   }

   // List of the cache names
   String[] cacheNames = new String[caches.length];
   for (int i = 0; i < caches.length; i++) {
      cacheNames[i] = caches[i].getName();
   }

   // decimal formatter for cache values
   DecimalFormat mbFormat = new DecimalFormat("#0.00");
   DecimalFormat kFormat = new DecimalFormat("#");
   DecimalFormat percentFormat = new DecimalFormat("#0.0");
%>

<%!

   static boolean isEnt = false;

   static {
      try {
         LicenseManager.validateLicense("Jive Forums Enterprise", "3.0");
         isEnt = true;
      } catch (Exception ignored) {
      }
   }
%>
