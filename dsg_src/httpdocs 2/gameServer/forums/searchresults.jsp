<%--
  - $RCSfile: searchresults.jsp,v $
  - $Revision: 1.15.4.3 $
  - $Date: 2003/03/21 18:59:37 $
  -
  - Copyright (C) 2002-2003 Jive Software. All rights reserved.
  -
  - This software is the proprietary information of Jive Software. Use is subject to license terms.
--%>

<%@ page import="com.jivesoftware.forum.action.SearchAction,
                 com.jivesoftware.forum.action.util.Page,
                 com.jivesoftware.util.RelativeDateRange,
                 java.util.Date"
%>

<%@ include file="global.jsp" %>

<%@ taglib uri="webwork" prefix="ww" %>
<%@ taglib uri="jivetags" prefix="jive" %>

<%  // Get the action for this view.
    SearchAction action = (SearchAction)getAction(request);
%>

<jsp:include page="header.jsp" flush="true">
 <jsp:param name="pageWidth" value="800"/></jsp:include>

<table cellpadding="0" cellspacing="0" border="0" width="100%">
<tr valign="top">
    <td width="98%">

        <%-- Breadcrumbs (customizable via the admin tool) --%>

        <jsp:include page="breadcrumbs.jsp" flush="true" />

        <%-- Forum name and brief info about the forum --%>

        <p class="jive-page-title">
        <%-- Forum Search --%>
        <jive:i18n key="search.title" />
        </p>

        <%--
            Use the form below to search the forum content. You can choose to search all content
            or restrict it to certain forums or dates. Also, you can filter the results by
            a username or user ID.
        --%>
        <jive:i18n key="search.description" />

    </td>
    <td width="1%"><img src="images/blank.gif" width="10" height="1" border="0"></td>
    <td width="1%">

        <%@ include file="accountbox.jsp" %>

    </td>
</tr>
</table>

<br>

<form action="search!execute.jspa" name="searchform">

<span class="jive-search-form">
<table cellpadding="3" cellspacing="2" border="0" width="100%">
<tr>
    <th colspan="3">
        <%-- Search Forum Content --%>
        <jive:i18n key="search.search_forum_content" />
    </th>
</tr>
<tr>
    <td align="right" width="1%" nowrap>
        <%-- Search Terms: --%>
        <jive:i18n key="search.search_terms" /><jive:i18n key="global.colon" />
    </td>
    <td width="1%"><input type="text" name="q" size="40" maxlength="100" value="<ww:if test="q"><ww:property value="q" /></ww:if>"></td>
    <%-- Search (button) --%>
    <td width="98%"><input type="submit" value="<jive:i18n key="global.search" />"></td>
</tr>
<ww:if test="errors['q']">
    <tr>
        <td align="right" width="1%" nowrap>&nbsp;</td>
        <td colspan="2" width="99%">
            <span class="jive-error-text">
            <ww:property value="errors['q']" />
            </span>
        </td>
    </tr>
</ww:if>
<tr>
    <td align="right" width="1%" nowrap>
        <%-- Category or Forum: --%>
        <jive:i18n key="search.category_or_forum" /><jive:i18n key="global.colon" />
    </td>
    <td colspan="2" width="99%">
        <select size="1" name="objID">
        <option value="" style="border-bottom:2px #ccc solid"
         ><%-- All Categories --%>
          <jive:i18n key="search.all_categories" />

        <ww:iterator value="forumFactory/forums">

            <option value="f<ww:property value="ID" />"
             <ww:if test="forumSelected(ID) == true">selected</ww:if>
            >&nbsp;

            &#149; <ww:property value="name" />

        </ww:iterator>

        <ww:property value="forumFactory/rootForumCategory">
            <ww:iterator value="recursiveCategories">

                <ww:generator val="'&nbsp;&nbsp;'" count="categoryDepth" id="space" />

                <option value="c<ww:property value="ID" />" style="border-bottom:1px #ccc dotted"
                 <ww:if test="categorySelected(ID) == true">selected</ww:if>
                >

                <ww:iterator value="@space">
                    <ww:property />
                </ww:iterator>

                <ww:property value="name" />

                <ww:iterator value="forums">

                    <ww:generator val="'&nbsp;&nbsp;'" count="categoryDepth" id="space" />

                    <option value="f<ww:property value="ID" />"
                     <ww:if test="forumSelected(ID) == true">selected</ww:if>
                    >&nbsp;

                    <ww:iterator value="@space">
                        <ww:property />
                    </ww:iterator>

                    &#149; <ww:property value="name" />

                </ww:iterator>

            </ww:iterator>
        </ww:property>
        </select>
    </td>
</tr>
<tr>
    <td align="right" width="1%" nowrap>
        <%-- Date Range: --%>
        <jive:i18n key="search.date_range" /><jive:i18n key="global.colon" />
    </td>
    <td colspan="2" width="99%">

        <select size="1" name="dateRange">

        <%  // Print out available dates
            RelativeDateRange[] ranges = action.getDateRanges();
            String currentRangeID = action.getDateRange();
            Date now = new Date();

            for (int i=0; i<ranges.length; i++) {
                RelativeDateRange range = ranges[i];
        %>
            <option value="<%= range.getID() %>"
             <%= ((range.getID().equals(currentRangeID)) ? "selected" : "") %>>

             <jive:i18n key="<%= range.getI18nKey() %>" />

             <% if (!"all".equals(range.getID())) { %>

                 -
                 <%= action.getShortDateFormat().format(range.getStartDate(now)) %>

             <% } %>

        <%
            }
        %>

        </select>
    </td>
</tr>
<tr>
    <td align="right" width="1%" nowrap>
        <%-- Username or User ID: --%>
        <jive:i18n key="search.username" /><jive:i18n key="global.colon" />
    </td>
    <td colspan="2" width="99%">
        <input type="text" name="userID" size="20" maxlength="50"
         value="<ww:if test="searchedUser"><ww:property value="searchedUser/username" /></ww:if><ww:elseIf test="userID"><ww:property value="userID" /></ww:elseIf>">
        <span class="jive-description">
        <%-- (Leave field blank to search all users) --%>
        <jive:i18n key="search.note" />
        </span>
    </td>
</tr>
<ww:if test="errors['userID']">
    <tr>
        <td align="right" width="1%" nowrap>&nbsp;</td>
        <td colspan="2" width="99%">
            <span class="jive-error-text">
            <ww:property value="errors['userID']" />
            </span>
        </td>
    </tr>
</ww:if>
<tr>
    <td align="right" width="1%" nowrap>
        <%-- Results Per Page: --%>
        <jive:i18n key="search.results_page" /><jive:i18n key="global.colon" />
    </td>
    <td colspan="2" width="99%">
        <select size="1" name="numResults">
        <ww:iterator value="numResultOptions">
            <option value="<ww:property />"
             <ww:if test="numResults == .">selected</ww:if>
             ><ww:property />
        </ww:iterator>
        </select>
    </td>
</tr>
</table>
</span>

</form>

<script language="JavaScript" type="text/javascript">
<!--
document.searchform.q.focus();
//-->
</script>

<ww:if test="results">

    <ww:bean name="'com.jivesoftware.webwork.util.Counter'" id="resultNum">
        <ww:param name="'first'" value="resultStart" />
    </ww:bean>

    <span class="jive-search-results">
    <table cellpadding="3" cellspacing="0" border="0" width="100%">
    <tr>
        <th colspan="2" style="text-align:left;border-bottom:1px #ccc solid;">
            <ww:if test="resultCount == 0" id="resultTest">
                <%-- No Results - please try a less restrictive search. --%>
                <jive:i18n key="search.no_results" />
            </ww:if>
            <ww:else>
                <%-- Results: {NUM} --%>
                <jive:i18n key="search.result_count">
                    <jive:arg>
                        <ww:property value="numberFormat/format(resultCount)" />
                    </jive:arg>
                </jive:i18n>

                <ww:if test="q">
                    &nbsp;
                    <%-- Search Terms: {terms} --%>
                    <jive:i18n key="search.search_terms_display">
                        <jive:arg>
                            <ww:property value="q" escape="true" />
                        </jive:arg>
                    </jive:i18n>
                </ww:if>
            </ww:else>
        </th>
    </tr>
    <ww:if test="@resultTest && numPages > 1">
        <tr>
            <td colspan="2" style="text-align:left;border-bottom:1px #ccc solid;">
            <jive:cache id="paginator">

                <%-- Pages: XXX --%>
                <jive:i18n key="global.pages" /><jive:i18n key="global.colon" />
                <ww:property value="numPages" />

                <ww:if test="numPages > 1">

                    <span class="jive-paginator">
                    [
                    <ww:if test="previousPage == true">

                        <%-- previous --%>
                        <a href="search!execute.jspa?<ww:property value="searchParams" />&start=<ww:property value="previousPageStart" />"
                         ><jive:i18n key="global.previous" /></a> |

                    </ww:if>

                    <ww:iterator value="pages">

                        <ww:if test=".">

                            <a href="search!execute.jspa?<ww:property value="searchParams" />&start=<ww:property value="start" />"
                             class="<ww:if test="start==../start">jive-current</ww:if>"
                             ><ww:property value="number" /></a>

                        </ww:if>
                        <ww:else>

                            ...

                        </ww:else>

                    </ww:iterator>

                    <ww:if test="nextPage == true">

                        <%-- Next --%>
                        | <a href="search!execute.jspa?<ww:property value="searchParams" />&start=<ww:property value="nextPageStart" />"
                         ><jive:i18n key="global.next" /></a>

                    </ww:if>
                    ]
                    </span>

                </ww:if>

            </jive:cache>
            </td>
        </tr>
    </ww:if>
    <tr><td colspan="2">&nbsp;</td></tr>
    </table>

    <ww:iterator value="results" status="'status'">

        <table cellpadding="3" cellspacing="0" border="0" width="100%">
        <tr valign="top">
            <td width="1%"><ww:property value="@resultNum/next" />)</td>
            <td width="99%">
                <span class="jive-search-result">
                <a href="thread.jspa?forumID=<ww:property value="forumThread/forum/ID" />&threadID=<ww:property value="forumThread/ID" />&messageID=<ww:property value="ID" />#<ww:property value="ID" />"
                 ><ww:property value="subject" /></a>
                <br>
                <span class="jive-info">
                <%--
                    Posted on: {date}, by: {user}
                --%>
                <jive:i18n key="search.search_result_info">
                    <jive:arg>
                        <ww:property value="dateFormat/format(creationDate)" />
                    </jive:arg>
                    <jive:arg>
                        <ww:if test="user">

                            <a href="<%= request.getContextPath() %>/gameServer/profile?viewName==<ww:property value="user/username" />"
                             ><ww:property value="user/username" /></a>

                        </ww:if>
                        <ww:else>

                            <i><jive:i18n key="global.guest" /></i>

                        </ww:else>
                    </jive:arg>
                </jive:i18n>
                </span>

                <ww:if test="q">

                <br>

                    <ww:if test="messageBodyPreview(.)">
                <span class="jive-body">
                <ww:property value="messageBodyPreview(.)" />
                </span>
                    </ww:if>
                </ww:if>

                </span>
                <br><br>
            </td>
        </tr>
        </table>

    </ww:iterator>

    <ww:if test="@resultTest">
        <table cellpadding="3" cellspacing="0" border="0" width="100%">
            <tr>
                <td colspan="2" style="text-align:left;border-top:1px #ccc solid;">
                    <jive:cache id="paginator" />
                </td>
            </tr>
        </table>
    </ww:if>
    </span>

</ww:if>
<ww:else>

    <ww:if test="q">

        <br><hr size="0">

        <p>
        <%--
            No search results for "{0}". You should try a less restrictive search.
        --%>
        <jive:i18n key="search.no_results_query">
            <jive:arg>
                <ww:property value="q" escape="true" />
            </jive:arg>
        </jive:i18n>
        </p>

    </ww:if>

</ww:else>

<jsp:include page="footer.jsp" flush="true" />
