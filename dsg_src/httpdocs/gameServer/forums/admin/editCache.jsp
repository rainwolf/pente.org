<%
   /**
    *	$RCSfile: editCache.jsp,v $
    *	$Revision: 1.2 $
    *	$Date: 2002/10/02 01:20:37 $
    */
%>

<%@ page import="java.util.*,
                 java.text.*,
                 com.jivesoftware.util.*,
                 com.jivesoftware.forum.*,
                 com.jivesoftware.forum.database.*,
                 com.jivesoftware.forum.util.*"
%>

<%! // global variables/methods, etc

   private int getCurrentPreset(Cache[] caches, int[][] cachePresetSizes) {
      // Custom by default (we loop through the 3 presets, if none match,
      // the current set of presets must be custom).
      int currentPreset = CACHE_PRESET_CUSTOM;
      // The array of presets to check
      int[] presets = {
         CACHE_PRESET_SMALL, CACHE_PRESET_MEDIUM, CACHE_PRESET_LARGE
      };
      int preset;
      for (preset = 0; preset < presets.length; preset++) {
         boolean matches = true;
         for (int i = 0; i < cachePresetSizes.length; i++) {
            if (caches[i].getMaxCacheSize() != cachePresetSizes[i][preset]) {
               matches = false;
            }
         }
         if (matches) {
            currentPreset = preset;
            break;
         }
      }
      return currentPreset;
   }
%>

<%@ include file="global.jsp" %>

<%@ include file="cacheUtils.jsp" %>

<% // Permission check
   if (!isSystemAdmin) {
      throw new UnauthorizedException("You don't have admin privileges to perform this operation.");
   }

   // Get a list of DbForum objects for the special caches
   List dbForumList = new java.util.LinkedList();
   ForumCategory rootCat = dbForumFactory.getRootForumCategory();
   for (Iterator iter = rootCat.getRecursiveForums(); iter.hasNext(); ) {
      Forum forum = (Forum) iter.next();
      dbForumList.add((DbForum) forum);
   }

   // get parameters
   boolean setCacheSize = ParamUtils.getBooleanParameter(request, "setCacheSize");
   boolean custom = ParamUtils.getBooleanParameter(request, "custom");
   int cachePreset = ParamUtils.getIntParameter(request, "cachePreset", getCurrentPreset(caches, cachePresetSizes));

   // set the cache size if requested
   if (setCacheSize) {
      if (cachePreset == CACHE_PRESET_CUSTOM) {
         custom = true;
         session.setAttribute("cache.customMode", "true");
      } else {
         session.removeAttribute("cache.customMode");
         // Loop through the caches, set the size with the size of the
         // requested preset:
         for (int i = 0; i < caches.length; i++) {
            Cache cache = caches[i];
            cache.setMaxCacheSize(cachePresetSizes[i][cachePreset]);
            JiveGlobals.setJiveProperty("cache." + names[i] + ".size", "" + cachePresetSizes[i][cachePreset]);
         }
         response.sendRedirect("editCache.jsp?cachePreset=" + cachePreset);
         return;
      }
   }
   if ("true".equals((String) session.getAttribute("cache.customMode"))) {
      cachePreset = CACHE_PRESET_CUSTOM;
   }
   if (cachePreset == CACHE_PRESET_CUSTOM) {
      custom = true;
   }
   boolean setCustom = ParamUtils.getBooleanParameter(request, "setCustom");

   // custom cache parameter values (in K)
   int[] customSizeParams = new int[caches.length];
   for (int i = 0; i < caches.length; i++) {
      customSizeParams[i] = ParamUtils.getIntParameter(request, "custom" + i + "K", -1);
   }

   // Check for the "cutom" value in the session

   // set the custom sizes
   if (custom && setCustom) {
      for (int i = 0; i < caches.length; i++) {
         Cache cache = caches[i];
         if (customSizeParams[i] != -1) {
            cache.setMaxCacheSize(customSizeParams[i] * 1024);
            JiveGlobals.setJiveProperty("cache." + names[i] + ".size", "" + customSizeParams[i] * 1024);
         }
      }
      response.sendRedirect("editCache.jsp");
      return;
   }
%>

<%@ include file="header.jsp" %>

<p>

      <%  // Title of this page and breadcrumbs
    String title = "Edit Cache Settings";
    String[][] breadcrumbs = {
        {"Main", "main.jsp"},
        {"Cache Settings", "cache.jsp"},
        {"Edit Cache Settings", "editCache.jsp"}
        
    };
%>
   <%@ include file="title.jsp" %>

   <font size="-1">
      Customize your cache settings using the forms below.
      You should never set your cache sizes to total more than half of your
      total JVM memory. By default, most JVMs are configured to use 64 MB of memory,
      although the default may vary or have been changed on your system.
      <p>
         Setting the caches too large will not result in increased performance. Instead,
         tune your cache larger only as long as it results in greater effectivness over
         time (as measured on the <a href="cache.jsp">main cache</a> page).
   </font>

<p>

   <font size="-1"><b>Cache Presets</b></font>
<form action="editCache.jsp">
   <input type="hidden" name="setCacheSize" value="true">
   <ul>
      <% for (int i = 0; i < CACHE_PRESET_NAMES.length; i++) {
         String checked = "";
         if (cachePreset == i) {
            checked = " checked";
         }
      %>
      <input type="radio" name="cachePreset" value="<%= i %>" id="rb0<%= i %>"<%= checked %>>
      <font size="-1"><label for="rb0<%= i %>"><b><%= CACHE_PRESET_NAMES[i] %>
      </b> -- <%= CACHE_PRESET_DESCRIPTIONS[i] %>
      </label></font>
      <p>
            <%  } %>
         <input type="submit" value="Save Settings">
   </ul>
</form>

<p>

   <font size="-1"><b>Cache Sizes</b></font>
<form action="editCache.jsp" name="cacheForm">
   <input type="hidden" name="custom" value="<%= custom %>">
   <input type="hidden" name="setCustom" value="true">
   <ul>
      <table bgcolor="<%= tblBorderColor %>" cellpadding="0" cellspacing="0" border="0" width="300">
         <tr>
            <td>
               <table bgcolor="<%= tblBorderColor %>" cellpadding="3" cellspacing="1" border="0" width="100%">
                  <tr bgcolor="#eeeeee">
                     <td align="center" width="50%"><font face="verdana" size="-2"><b>CACHE NAME</b></font></td>
                     <td align="center" width="50%" colspan="2"><font face="verdana" size="-2"><b>SIZE</b></font></td>
                  </tr>
                  <% for (int i = 0; i < caches.length; i++) {
                     Cache cache = caches[i];
                     double totalMemMB = (double) cache.getMaxCacheSize() / (1024 * 1024);
                     double totalMemK = (double) cache.getMaxCacheSize() / 1024;
                  %>
                  <tr bgcolor="#ffffff">
                     <td width="50%">
                        <font size="-1"><b><%= cache.getName() %>
                        </b></font>
                     </td>
                     <td width="25%">
                        <% if (custom) { %>
                        <input type="text" name="custom<%= i %>K" size="6" value="<%= kFormat.format(totalMemK) %>"
                               maxlength="6"><font size="-1">K</font>
                        <% } else { %>
                        <font size="-1"><%= kFormat.format(totalMemK) %> K</font>
                        <% } %>
                     </td>
                     <td width="25%">
                        <font size="-1">(<%= mbFormat.format(totalMemMB) %> MB)</font>
                     </td>
                  </tr>
                  <% } %>
               </table>
            </td>
         </tr>
      </table>
      <% if (custom) { %>
      <p>
         <input type="submit" value="Save Custom Settings">
            <%  } %>
      <p>
         <font size="-1"><i>Note: 1 MB = 1024 K</i></font>
   </ul>
</form>

<p>

<center>
   <form action="cache.jsp">
      <input type="submit" value="Return to Cache Page">
   </form>
</center>

<p>

   <%@ include file="footer.jsp" %>

