<%@ page import="com.jivesoftware.forum.action.SettingsAction,
                 java.util.Locale,org.pente.gameServer.core.*"%>
<%
/**
 * $RCSfile: editsettings.jsp,v $
 * $Revision: 1.18 $
 * $Date: 2002/12/20 23:56:28 $
 */
%>

<%@ include file="global.jsp" %>

<%@ taglib uri="webwork" prefix="ww" %>
<%@ taglib uri="jivetags" prefix="jive" %>

<%  // Get the action for this view.
    SettingsAction action = (SettingsAction)getAction(request);
	DSGPlayerData dsgPlayerData = (DSGPlayerData) request.getAttribute("dsgPlayerData");
    String timeZoneID = "";
    if (dsgPlayerData != null) {
    	timeZoneID = dsgPlayerData.getTimezone();
    }
%>

<jsp:include page="header.jsp" flush="true">
 <jsp:param name="pageWidth" value="900"/>
 <jsp:param name="current" value="My Profile"/></jsp:include>


<%  String selectedTab = action.getText("global.general_settings"); %>
<%@ include file="tabs.jsp" %>

<br>

<ww:if test="hasErrorMessages == true">

    <span class="jive-error-text">
    <ww:iterator value="errorMessages">
        <ww:property /> <br>
    </ww:iterator>
    <br>
    </span>

</ww:if>

<ww:if test="hasErrors == true">

    <span class="jive-error-text">
    <ww:iterator value="errors">
        <ww:property /> <br>
    </ww:iterator>
    <br>
    </span>

</ww:if>

<a name="viewing"></a>
<span class="jive-cp-header">
<%-- Viewing Preferences --%>
<jive:i18n key="settings.viewing_prefs" />
</span>
<br><br>

<form action="settings.jspa" method="post">
<input type="hidden" name="command" value="execute">

<span class="jive-cp-formbox">
<table cellpadding="3" cellspacing="2" border="0" width="100%">
<tr>
    <td width="1%" nowrap>
        <%-- Topics Per Forum Page: --%>
        <jive:i18n key="settings.topics_forum" /><jive:i18n key="global.colon" />
    </td>
    <td>
        <select size="1" name="topicsPerForumPage">

            <%  String[] topicCounts = { "10", "15", "25", "50" };
                String currentTopicCount = action.getTopicsPerForumPage();
                for (int i=0; i<topicCounts.length; i++) {
                    boolean selected = topicCounts[i].equals(currentTopicCount);
            %>
                <option value="<%= topicCounts[i] %>"<%= (selected?" selected":"") %>
                 ><%= topicCounts[i] %>

            <%  } %>

        </select>
    </td>
</tr>
<%  if ("flat".equals(JiveGlobals.getJiveProperty("skin.default.threadMode"))) { %>
    <tr>
        <td width="1%" nowrap>
            <%-- Messages Per Topic Page: --%>
            <jive:i18n key="settings.messages_topic" /><jive:i18n key="global.colon" />
        </td>
        <td>
            <select size="1" name="messagesPerTopicPage">

                <%  String[] messageCounts = { "10", "15", "25", "50" };
                    String currentMessageCount = action.getMessagesPerTopicPage();
                    for (int i=0; i<messageCounts.length; i++) {
                        boolean selected = messageCounts[i].equals(currentMessageCount);
                %>
                    <option value="<%= messageCounts[i] %>"<%= (selected?" selected":"") %>
                     ><%= messageCounts[i] %>

                <%  } %>

            </select>
        </td>
    </tr>
<%  } %>
<%--
    <ww:if test="pageUser">
        <jive:property if="skin.default.usersChooseThreadMode">
            <tr>
                <td width="1%" nowrap>
                    <!-- Topic Page View: -->
                    <jive:i18n key="settings.topic_page_view" />
                </td>
                <td>
                    <select size="1" name="threadMode">
                    <%  String[][] threadModes = action.getThreadModes();
                        String currentMode = action.getCurrentThreadMode();
                        for (int i=0; i<threadModes.length; i++) {
                            boolean selected = threadModes[i][0].equals(currentMode);
                    %>
                        <option value="<%= threadModes[i][1] %>"<%= (selected?" selected":"") %>
                         ><%= threadModes[i][1] %>

                    <%  } %>
                    </select>
                </td>
            </tr>
        </jive:property>
    </ww:if>
--%>
<jive:property if="skin.default.usersChooseLocale">
    <tr>
        <td width="1%" nowrap>
            <%-- Locale: --%>
            <jive:i18n key="global.locale" /><jive:i18n key="global.colon" />
        </td>
        <td>
            <select size="1" name="localeID">
            <%  Locale[] locales = action.getLocales();
                Locale currentLocale = action.getLocale();
                for (int i=0; i<locales.length; i++) {
            %>
                <option value="<%= locales[i].toString() %>"
                 <%= ((currentLocale.getDisplayName().equals(locales[i].getDisplayName()))?" selected":"") %>
                 ><%= locales[i].getDisplayName() %>

            <%  } %>
            </select>
        </td>
    </tr>
</jive:property>
<input type="hidden" name="timeZoneID" value="<%= timeZoneID %>">
<%--
<tr>
    <td width="1%" nowrap>

        <jive:i18n key="global.time_zone" /><jive:i18n key="global.colon" />
    </td>
    <td>
        <select size="1" name="timeZoneID">
        <%	String[][] timeZones = action.getTimeZones();
            String timeZoneID = action.getTimeZone().getID();
            for (int i=0; i<timeZones.length; i++) {
                boolean selected = timeZones[i][0].equals(timeZoneID);
        %>
            <option value="<%= timeZones[i][0] %>"<%= (selected?" selected":"") %>><%= timeZones[i][1] %>

        <%	} %>
        </select>
    </td>
</tr>
--%>
</table>
</span>

<ww:if test="pageUser">

    <jive:property if="watches.enabled">

        <br><br>

        <a name="viewing"></a>
        <span class="jive-cp-header">
        <%-- Watch Preferences --%>
        <jive:i18n key="settings.watch" />
        </span>
        <br><br>

        <span class="jive-cp-formbox">
        <table cellpadding="3" cellspacing="2" border="0" width="100%">
        <tr>
            <td width="1%" nowrap>
                <%-- Automatically watch new topics I create: --%>
                <jive:i18n key="settings.always_watch_create" /><jive:i18n key="global.colon" />
            </td>
            <td>
                <input type="radio" name="autoAddTopicWatch" value="true" id="w01"
                 <ww:if test="autoAddTopicWatch == true">checked</ww:if>>
                <%-- Yes --%>
                <label for="w01"><jive:i18n key="global.yes" /></label>
                &nbsp;
                <input type="radio" name="autoAddTopicWatch" value="false" id="w02"
                 <ww:if test="autoAddTopicWatch == false">checked</ww:if>>
                <%-- No --%>
                <label for="w02"><jive:i18n key="global.no" /></label>
            </td>
        </tr>
        <tr>
            <td width="1%" nowrap>
                <%-- Automatically watch topics I reply to: --%>
                <jive:i18n key="settings.always_watch_reply" /><jive:i18n key="global.colon" />
            </td>
            <td>
                <input type="radio" name="autoAddReplyWatch" value="true" id="w03"
                 <ww:if test="autoAddReplyWatch == true">checked</ww:if>>
                <%-- Yes --%>
                <label for="w03"><jive:i18n key="global.yes" /></label>
                &nbsp;
                <input type="radio" name="autoAddReplyWatch" value="false" id="w04"
                 <ww:if test="autoAddReplyWatch == false">checked</ww:if>>
                <%-- No --%>
                <label for="w04"><jive:i18n key="global.no" /></label>
            </td>
        </tr>
        <tr>
            <td width="1%" nowrap>
                <%-- Notify me by email of watch updates: --%>
                <jive:i18n key="settings.email_updates" /><jive:i18n key="global.colon" />
            </td>
            <td>
                <input type="radio" name="autoAddEmailWatch" value="true" id="w05"
                 <ww:if test="autoAddEmailWatch == true">checked</ww:if>>
                <%-- Yes --%>
                <label for="w05"><jive:i18n key="global.yes" /></label>
                &nbsp;
                <input type="radio" name="autoAddEmailWatch" value="false" id="w06"
                 <ww:if test="autoAddEmailWatch == false">checked</ww:if>>
                <%-- No --%>
                <label for="w06"><jive:i18n key="global.no" /></label>
            </td>
        </tr>
        </table>

    </jive:property>

</ww:if>

<br>

<%-- Save Changes --%>
<input type="submit" name="doSaveChangs" value="<jive:i18n key="settings.save_changes" />">

</form>

<br><br>

<jsp:include page="footer.jsp" flush="true" />