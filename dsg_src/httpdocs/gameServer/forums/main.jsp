<%
   /**
    * $RCSfile: main.jsp,v $
    * $Revision: 1.33 $
    * $Date: 2003/01/09 03:47:33 $
    */
%>

<%@ page import="java.util.Iterator,
                 com.jivesoftware.forum.*,
                 com.jivesoftware.forum.action.ForumCategoryAction,
                 com.jivesoftware.base.*"
%>

<%@ include file="global.jsp" %>

<%@ taglib uri="webwork" prefix="ww" %>
<%@ taglib uri="jivetags" prefix="jive" %>

<% // Get the action for this view.
   ForumCategoryAction action = (ForumCategoryAction) getAction(request);
   ForumCategory category = action.getCategory();
%>

<jsp:include page="header.jsp" flush="true"/>

<table cellpadding="0" cellspacing="0" border="0" width="100%">
   <tr valign="top">
      <td width="98%">

         <%-- Breadcrumbs (customizable via the admin tool) --%>

         <jsp:include page="breadcrumbs.jsp" flush="true"/>

         <% if (category.getID() == 1L) { %>

         <%-- Text describing your community (customizable via the admin tool) --%>

         <p>
            <% if (!"false".equals(getProp("useDefaultWelcomeText"))) { %>
            <jive:i18n key="global.community_text"/>
            <% } else { %>
            <%= getProp("communityDescription") %>
            <% } %>
         </p>

         <% } else { %>

         <p>
            <span class="jive-page-title">
            <%-- Category: CAT_NAME --%>
            <jive:i18n key="global.category"/><jive:i18n key="global.colon"/>
            <%= category.getName() %>
            </span>
            <br>
            <%-- Sub-Categories: NUM, Forums: NUM --%>
            <jive:i18n key="index.sub_cat_count_forum_count">
               <jive:arg>
                  <%= action.getNumberFormat().format(category.getCategoryCount()) %>
               </jive:arg>
               <jive:arg>
                  <%= action.getNumberFormat().format(category.getForumCount()) %>
               </jive:arg>
            </jive:i18n>
         </p>

         <% if (category.getDescription() != null) { %>
         <span class="jive-description">
                <%= category.getDescription() %>
                </span>
         <% } %>

         <% } %>

      </td>
      <td width="1%"><img src="images/blank.gif" width="10" height="1" border="0"></td>
      <td width="1%">

         <%@ include file="accountbox.jsp" %>
         <span class="jive-account-box">
        <table class="jive-box" cellpadding="3" cellspacing="0" border="0" width="200">
          <tr>
            <td width="1%"><a href="/rss.xml"><img src="/gameServer/images/feed.png" border="0" width="16" height="16"></a></td>
            <td width="99%">
             <a href="/rss.xml">RSS Feed</a>
            </td>
          </tr>
        </table>
        </span>
      </td>
   </tr>
</table>

<% if (category.getID() != 1L) { %>

<br>

<jive:property if="watches.enabled">

   <% if (action.getPageUser() != null) { %>

   <% if (action.getForumFactory().getWatchManager().isWatched(action.getPageUser(), category)) { %>

   <table class="jive-info-message" cellpadding="3" cellspacing="0" border="0" width="100%">
      <tr valign="top">
         <td width="1%"><img src="images/info-16x16.gif" width="16" height="16" border="0"></td>
         <td width="99%">

                        <span class="jive-info-text">

                        <%-- You are watching this category. To remove this watch, click "Stop Watching Category" below. --%>
                        <jive:i18n key="index.watching_category"/>

                        <%-- Watch options --%>
                        <a href="editwatches!default.jspa"><jive:i18n key="global.watch_options"/></a>

                        </span>

         </td>
      </tr>
   </table>
   <br>

   <% } %>

   <% } %>

</jive:property>

<span class="jive-button">
    <table cellpadding="0" cellspacing="0" border="0">
    <tr>
        <td>
            <table cellpadding="3" cellspacing="0" border="0">
            <tr>
                <td><a href="index.jspa?categoryID=<%= category.getParentCategory().getID() %>"><img
                   src="images/up-16x16.gif" width="16" height="16" border="0"></a></td>
                <td>
                    <span class="jive-button-label">
                    <%-- Up one category --%>
                    <a href="index.jspa?categoryID=1"
                    ><jive:i18n key="index.up_one_category"/></a>
                    </span>
                </td>
            </tr>
            </table>
        </td>
        <td>
            <table cellpadding="3" cellspacing="0" border="0">
            <tr>
                <td><a href="index.jspa?categoryID=<%= category.getParentCategory().getID() %>"><img
                   src="images/back-to-16x16.gif" width="16" height="16" border="0"></a></td>
                <td>
                    <span class="jive-button-label">
                    <%-- Back to main category --%>
                    <a href="index.jspa?categoryID=1"
                    ><jive:i18n key="index.back_to_main_cat"/></a>
                    </span>
                </td>
            </tr>
            </table>
        </td>
        <% if (action.getPageUser() != null) { %>
            <td>
                <table cellpadding="3" cellspacing="2" border="0">
                <tr>
                    <td><a href="watches.jspa"><img src="images/watch-16x16.gif" width="16" height="16" border="0"></a></td>
                    <td>
                        <span class="jive-button-label">
                        <% if (action.getForumFactory().getWatchManager().isWatched(action.getPageUser(), category)) { %>

                            <%-- Stop watching category --%>
                            <a href="watches!remove.jspa?categoryID=<%= category.getID() %>"
                            ><jive:i18n key="index.stop_watching_category"/></a>

                        <% } else { %>

                            <%-- Watch category --%>
                            <a href="watches!add.jspa?categoryID=<%= category.getID() %>"
                            ><jive:i18n key="global.watch_category"/></a>

                        <% } %>
                        </span>
                    </td>
                </tr>
                </table>
            </td>
        <% } %>
    </tr>
    </table>
    </span>

<% } %>

<br>

<table cellpadding="0" cellspacing="0" border="0" width="100%">
   <tr valign="top">
      <td width="98%" valign="top">

         <%-- Category and forum table: --%>

         <table id="jive-cat-forum-list" class="jive-list" cellpadding="3" cellspacing="2" width="100%">
            <tr>
               <th class="jive-forum-name" colspan="2" width="98%">
                  <%-- Forum / Category --%>
                  <jive:i18n key="global.forum"/>
                  <jive:i18n key="global.slash"/>
                  <jive:i18n key="global.category"/>
               </th>
               <th class="jive-counts" nowrap width="1%">
                  <%-- Topics / Messages --%>
                  <jive:i18n key="global.topics"/>
                  <jive:i18n key="global.slash"/>
                  <jive:i18n key="global.messages"/>
               </th>
               <th class="jive-date" nowrap width="1%">
                  <%-- Last Post --%>
                  <jive:i18n key="global.last_post"/>
               </th>
            </tr>

            <%-- Print all forums in the current category --%>

            <% int status = 0;
               for (Iterator forums = category.getForums(); forums.hasNext(); ) {
                  Forum forum = (Forum) forums.next();
            %>
            <%@ include file="forum-row.jsp" %>

            <% } %>

            <%-- Print all subcategories and subforums in this category --%>

            <% for (Iterator categories = category.getCategories(); categories.hasNext(); ) {
               ForumCategory subCategory = (ForumCategory) categories.next();
            %>

            <tr>
               <td class="jive-category-name" colspan="4">
                  <a href="index.jspa?categoryID=<%= subCategory.getID() %>"><%= subCategory.getName() %>
                  </a>
                  <% if (subCategory.getDescription() != null) { %>
                  <span class="jive-description">
                        <br><%= subCategory.getDescription() %>
                        </span>
                  <% } %>
               </td>
            </tr>

            <% status = 0;
               for (Iterator forums = subCategory.getForums(); forums.hasNext(); ) {
                  Forum forum = (Forum) forums.next();
            %>
            <%@ include file="forum-row.jsp" %>

            <% } %>

            <% } %>

         </table>

         <br>

         <table cellpadding="3" cellspacing="0" border="0">
            <tr>
               <td><img src="images/unread.gif" width="9" height="9" border="0"></td>
               <td>
                <span class="jive-description">
                <%-- Denotes unread or updated content since your last visit. --%>
                <jive:i18n key="global.new_messages_explained"/>
                </span>
               </td>
            </tr>
         </table>

      </td>
      <td width="1%"><img src="images/blank.gif" width="10" height="1" border="0"></td>
      <td width="1%" align="center">

        <span class="jive-sidebar">

        <form action="search!execute.jspa">

        <span class="jive-search-box">
        <table class="jive-box" cellpadding="3" cellspacing="0" border="0" width="200">
        <tr>
            <th>
                <!-- Search -->
                <jive:i18n key="global.search"/>
            </th>
        </tr>
        <tr>
            <td>
                <table cellpadding="3" cellspacing="0" border="0" width="100%">
                <tr>
                    <td>
                        <input type="text" name="q" maxlength="150"><input type="submit" value="Go">
                    </td>
                </tr>
                <tr>
                    <td>
                        <select size="5" name="objID" style="width:185px" multiple>

                        <%-- only show a list of forums if there are less than 10 total --%>
                        <ww:property value="forumFactory/rootForumCategory" escape="false">
                            <ww:if test="forumCount <= 20">

                                    <option value=""
                                    ><%-- Choose Forums: --%>
                                    <jive:i18n key="searchbox.choose_forums"/>

                                    <ww:iterator value="forums">
                                        <option value="f<ww:property value="ID" />">&nbsp;&#149;&nbsp; <ww:property
                                           value="name"/>
                                    </ww:iterator>
                            </ww:if>
                        </ww:property>
                        </select>
                    </td>
                </tr>
                </table>
            </td>
        </tr>
        <tr>
            <td colspan="2" align="center">
                <%-- More search options --%>
                <a href="search!default.jspa"><jive:i18n key="searchbox.more_search_options"/></a>
            </td>
        </tr>
        </table>
        </span>

        </form>

        <span class="jive-popular-box">
        <table class="jive-box" cellpadding="3" cellspacing="0" border="0" width="200">
        <tr>
            <th colspan="2">
                <%-- Popular Discussions --%>
                <jive:i18n key="populartopics.popular_discussions"/>
            </th>
        </tr>
        <ww:if test="forumFactory/popularThreads/hasNext == true">
           <ww:iterator value="forumFactory/popularThreads">

                <tr valign="top">
                    <td width="1%">&#149;</td>
                    <td width="99%">
                        <a href="thread.jspa?forumID=<ww:property value="forum/ID" />&threadID=<ww:property value="ID" />"
                        ><ww:property value="name"/></a>
                        <span class="jive-description">
                        <br>
                        <ww:property value="forum/name"/>
                        </span>
                    </td>
                </tr>

           </ww:iterator>
        </ww:if>
        <ww:else>

            <tr>
                <td colspan="2">
                    <%-- No popular dicussions. --%>
                    <jive:i18n key="populartopics.no_popular_discussions"/>
                </td>
            </tr>

        </ww:else>
        </table>
        </span>

        <br>



        </span>

      </td>
   </tr>
</table>

<jsp:include page="footer.jsp" flush="true"/>