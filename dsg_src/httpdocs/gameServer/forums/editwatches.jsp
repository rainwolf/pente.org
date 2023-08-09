<%@ page import="com.jivesoftware.forum.action.ForumActionSupport" %>
<%
   /**
    * $RCSfile: editwatches.jsp,v $
    * $Revision: 1.11 $
    * $Date: 2002/12/21 00:04:18 $
    */
%>

<%@ include file="global.jsp" %>

<%@ taglib uri="webwork" prefix="ww" %>
<%@ taglib uri="jivetags" prefix="jive" %>

<% // Get the action for this view.
   ForumActionSupport action = (ForumActionSupport) getAction(request);
%>

<% pageContext.setAttribute("current", "My Profile"); %>
<jsp:include page="header.jsp" flush="true">
   <jsp:param name="pageWidth" value="900"/>
   <jsp:param name="current" value="My Profile"/>
</jsp:include>

<% String selectedTab = action.getText("global.watches"); %>
<%@ include file="tabs.jsp" %>

<form action="editwatches.jspa" method="post">
   <input type="hidden" name="command" value="execute">

   <br>

   <a name="cats"></a>
   <span class="jive-cp-header">
<%-- Watched Categories (COUNT) --%>
<jive:i18n key="watches.watched_cat_count">
   <jive:arg>
      <ww:property value="watchedCategoryCount" id="catWatchCount"/>
   </jive:arg>
</jive:i18n>
</span>
   <br><br>

   <ww:bean name="'com.jivesoftware.webwork.util.Counter'" id="catRowCount">
      <ww:param name="first" value="1"/>
   </ww:bean>

   <ww:if test="@catWatchCount > 0">

    <span class="jive-watch-list">

    <table class="jive-box" cellpadding="3" cellspacing="2" border="0" width="100%">
    <tr>
        <th colspan="2" class="jive-name">
            <%-- Category Name --%>
            <jive:i18n key="global.category_name"/>
        </th>
        <th nowrap>
            <%-- Email Updates --%>
            <jive:i18n key="global.email_updates"/>
        </th>
        <th nowrap>
            <%-- Save --%>
            <jive:i18n key="global.save"/>
        </th>
        <th nowrap>
            <%-- Delete --%>
            <jive:i18n key="global.delete"/>
        </th>
    </tr>
    <ww:iterator value="watchedCategories" status="'status'">

        <tr class="<ww:if test="@status/odd==true">jive-odd</ww:if><ww:else>jive-even</ww:else>">
            <td width="1%" align="center">
                <ww:property value="@catRowCount/next"/>
            </td>
            <td width="96%" class="jive-name">
                <a href="index.jspa?categoryID=<ww:property value="ID" id="cid" />"
                ><ww:property value="name"/></a>
            </td>
            <td width="1%" align="center">
                <input type="checkbox" name="email-cat-<ww:property value="@cid" />"
                       value="<ww:property value="@cid" />"
                       <ww:if test="hasEmailWatch(.) == true">checked</ww:if>>
            </td>
            <td width="1%" align="center">
                <input type="checkbox" name="save-cat-<ww:property value="@cid" />" value="<ww:property value="@cid" />"
                       <ww:if test="isExpirableWatch(.) == false">checked</ww:if>>
            </td>
            <td width="1%" align="center" class="jive-delete">
                <input type="checkbox" name="delete-cat-<ww:property value="@cid" />"
                       value="<ww:property value="@cid" />">
            </td>
        </tr>

    </ww:iterator>
    <tr class="jive-button-row">
        <td width="97%" colspan="2">
            &nbsp;
        </td>
        <td colspan="2" width="2%" align="center" class="jive-update-button">
            <%-- Update Watches --%>
            <input type="submit" name="doCatWatchUpdate" value="<jive:i18n key="global.update_watches" />">
        </td>
        <td width="1%" align="center" class="jive-delete-button">
            <%-- Delete --%>
            <input type="submit" name="doCatWatchDelete" value="<jive:i18n key="global.delete" />">
        </td>
    </tr>
    </table>
    <br>

    </span>

   </ww:if>

   <a name="forums"></a>
   <span class="jive-cp-header">
<%-- Watched Forums (COUNT) --%>
<jive:i18n key="watches.watched_forum_count">
   <jive:arg>
      <ww:property value="watchedForumCount" id="forumWatchCount"/>
   </jive:arg>
</jive:i18n>
</span>
   <br><br>

   <ww:bean name="'com.jivesoftware.webwork.util.Counter'" id="forumRowCount">
      <ww:param name="first" value="1"/>
   </ww:bean>

   <ww:if test="@forumWatchCount > 0">

    <span class="jive-watch-list">

    <table class="jive-box" cellpadding="3" cellspacing="2" border="0" width="100%">
    <tr>
        <th colspan="2" class="jive-name">
            <%-- Forum Name --%>
            <jive:i18n key="global.forum_name"/>
        </th>
        <th nowrap>
            <%-- Email Updates --%>
            <jive:i18n key="global.email_updates"/>
        </th>
        <th nowrap>
            <%-- Save --%>
            <jive:i18n key="global.save"/>
        </th>
        <th nowrap>
            <%-- Delete --%>
            <jive:i18n key="global.delete"/>
        </th>
    </tr>
    <ww:iterator value="watchedForums" status="'status'">

        <tr class="<ww:if test="@status/odd==true">jive-odd</ww:if><ww:else>jive-even</ww:else>">
            <td width="1%" align="center">
                <ww:property value="@forumRowCount/next"/>
            </td>
            <td width="96%" class="jive-name">
                <a href="forum.jspa?forumID=<ww:property value="ID" id="fid" />"><ww:property value="name"/></a>
            </td>
            <td width="1%" align="center">
                <input type="checkbox" name="email-forum-<ww:property value="@fid" />"
                       value="<ww:property value="@fid" />"
                       <ww:if test="hasEmailWatch(.) == true">checked</ww:if>>
            </td>
            <td width="1%" align="center">
                <input type="checkbox" name="save-forum-<ww:property value="@fid" />"
                       value="<ww:property value="@fid" />"
                       <ww:if test="isExpirableWatch(.) == false">checked</ww:if>>
            </td>
            <td width="1%" align="center" class="jive-delete">
                <input type="checkbox" name="delete-forum-<ww:property value="@fid" />"
                       value="<ww:property value="@fid" />">
            </td>
        </tr>

    </ww:iterator>
    <tr class="jive-button-row">
        <td width="97%" colspan="2">
            &nbsp;
        </td>
        <td colspan="2" width="2%" align="center" class="jive-update-button">
            <%-- Update Watches --%>
            <input type="submit" name="doForumWatchUpdate" value="<jive:i18n key="global.update_watches" />">
        </td>
        <td width="1%" align="center" class="jive-delete-button">
            <%-- Delete --%>
            <input type="submit" name="doForumWatchDelete" value="<jive:i18n key="global.delete" />">
        </td>
    </tr>
    </table>
    <br>

    </span>

   </ww:if>

   <a name="topics"></a>
   <span class="jive-cp-header">
<%-- Watched Topics (COUNT) --%>
<jive:i18n key="watches.watched_topic_count">
   <jive:arg>
      <ww:property value="watchedThreadCount" id="threadWatchCount"/>
   </jive:arg>
</jive:i18n>
</span>
   <br><br>

   <ww:bean name="'com.jivesoftware.webwork.util.Counter'" id="threadRowCount">
      <ww:param name="first" value="1"/>
   </ww:bean>

   <ww:if test="@threadWatchCount > 0">

    <span class="jive-watch-list">

    <table class="jive-box" cellpadding="3" cellspacing="2" border="0" width="100%">
    <tr>
        <th colspan="2" class="jive-name">
            <%-- Topic --%>
            <jive:i18n key="global.topic"/>
        </th>
        <th nowrap>
            <%-- Forum --%>
            <jive:i18n key="global.forum"/>
        </th>
        <th nowrap>
            <%-- Email Updates --%>
            <jive:i18n key="global.email_updates"/>
        </th>
        <th nowrap>
            <%-- Save --%>
            <jive:i18n key="global.save"/>
        </th>
        <th nowrap>
            <%-- Delete --%>
            <jive:i18n key="global.delete"/>
        </th>
    </tr>
    <ww:iterator value="watchedThreads" status="'status'">

        <tr class="<ww:if test="@status/odd==true">jive-odd</ww:if><ww:else>jive-even</ww:else>">
            <td width="1%" align="center">
                <ww:property value="@threadRowCount/next"/>
            </td>
            <td width="95%" class="jive-name">
                <a href="thread.jspa?forumID=<ww:property value="forum/ID" id="tfid" />&threadID=<ww:property value="ID" id="tid" />"
                ><ww:property value="name"/></a>
            </td>
            <td width="1%" nowrap>
                <a href="forum.jspa?forumID=<ww:property value="forum/ID" id="tfid" />"
                ><ww:property value="forum/name"/></a>
            </td>
            <td width="1%" align="center">
                <input type="checkbox" name="email-thread-<ww:property value="@tid" />"
                       value="<ww:property value="@tfid" />-<ww:property value="@tid" />"
                       <ww:if test="hasEmailWatch(.) == true">checked</ww:if>>
            </td>
            <td width="1%" align="center">
                <input type="checkbox" name="save-thread-<ww:property value="@tid" />"
                       value="<ww:property value="@tfid" />-<ww:property value="@tid" />"
                       <ww:if test="isExpirableWatch(.) == false">checked</ww:if>>
            </td>
            <td width="1%" align="center" class="jive-delete">
                <input type="checkbox" name="delete-thread-<ww:property value="@tid" />"
                       value="<ww:property value="@tfid" />-<ww:property value="@tid" />">
            </td>
        </tr>

    </ww:iterator>
    <tr class="jive-button-row">
        <td width="97%" colspan="3">
            &nbsp;
        </td>
        <td colspan="2" width="2%" align="center" class="jive-update-button">
            <%-- Update Watches --%>
            <input type="submit" name="doThreadWatchUpdate" value="<jive:i18n key="global.update_watches" />">
        </td>
        <td width="1%" align="center" class="jive-delete-button">
            <%-- Delete --%>
            <input type="submit" name="doThreadWatchDelete" value="<jive:i18n key="global.delete" />">
        </td>
    </tr>
    </table>
    <br>

    </span>

   </ww:if>

</form>

<jsp:include page="footer.jsp" flush="true"/>
