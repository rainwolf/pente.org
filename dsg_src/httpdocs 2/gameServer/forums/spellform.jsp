<%
/**
 *	$RCSfile: spellform.jsp,v $
 *	$Revision: 1.8 $
 *	$Date: 2002/12/20 21:14:48 $
 */
%>

<%@ include file="global.jsp" %>

<%@ taglib uri="webwork" prefix="ww" %>
<%@ taglib uri="jivetags" prefix="jive" %>

<jsp:include page="header.jsp" flush="true" />

<script language="JavaScript" type="text/javascript" src="utils.js"></script>

<table cellpadding="0" cellspacing="0" border="0" width="100%">
<tr valign="top">
    <td width="98%">

        <%-- Breadcrumbs (customizable via the admin tool) --%>

        <jsp:include page="breadcrumbs.jsp" flush="true" />

        <%-- Page title --%>

        <p class="jive-page-title">
        <%-- Post Message: Spell Check --%>
        <jive:i18n key="spell.title" />
        </p>

    </td>
    <td width="1%"><img src="images/blank.gif" width="10" height="1" border="0"></td>
    <td width="1%">

        <%@ include file="accountbox.jsp" %>

    </td>
</tr>
</table>

<p>
<ww:if test="hasSpellingErrors == true">

    <%-- A summary of spelling errors ... --%>
    <jive:i18n key="spell.errors" />

</ww:if>
<ww:else>

    <%-- There are no spelling errors. Use the buttons ... --%>
    <jive:i18n key="spell.no_errors" />

</ww:else>
</p>

<ww:if test="hasErrors == true || hasErrorMessages == true">

    <table class="jive-error-message" cellpadding="3" cellspacing="2" border="0" width="350">
    <tr valign="top">
        <td width="1%"><img src="images/error-16x16.gif" width="16" height="16" border="0"></td>
        <td width="99%">

            <span class="jive-error-text">

            <ww:iterator value="errorMessages">
                <ww:property />
            </ww:iterator>

            <br><br>

            <ww:iterator value="error">
                <ww:property />
            </ww:iterator>

            </span>

        </td>
    </tr>
    </table>

    <br>

</ww:if>

<form action="spell!execute.jspa" method="post" name="postform" onsubmit="return checkPost();">

<input type="hidden" name="forumID" value="<ww:property value="$forumID" />">
<ww:if test="$threadID">
    <input type="hidden" name="threadID" value="<ww:property value="$threadID" />">
</ww:if>
<ww:if test="$messageID">
    <input type="hidden" name="messageID" value="<ww:property value="$messageID" />">
</ww:if>
<input type="hidden" name="reply" value="<ww:property value="reply" />">

<ww:bean name="'com.jivesoftware.webwork.util.Counter'" id="tabIndex" />

<span class="jive-spell-form">

<table class="jive-box" cellpadding="3" cellspacing="2" border="0" width="100%">
<tr valign="top" class="jive-odd">
    <td width="1%">

        <table cellpadding="0" cellspacing="0" border="0" width="180">
        <tr>
            <td>
                <ww:property value="pageUser/username" />
            </td>
        </tr>
        </table>

    </td>
    <td width="99%">

        <table cellpadding="0" cellspacing="0" border="0" width="100%">
        <tr>
            <td>
                <span class="jive-subject">
                <%-- Subject: XXX --%>
                <jive:i18n key="global.subject" /><jive:i18n key="global.colon" />
                <ww:property value="subject" />
                </span>
            </td>
        </tr>
        <tr>
            <td style="border-top: 1px #ccc solid;">
                <br>
                <span class="jive-body">
                <ww:property value="body" />
                </span>
                <br><br>
            </td>
        </tr>
        </table>
    </td>
</tr>
</table>

<br><br>

<ww:if test="hasSuggestions == true">

    <table class="jive-box" cellpadding="3" cellspacing="2" border="0">
    <tr>
        <th colspan="3">
            <ww:if test="misspelledWords == true">

                <%-- Misspelled Word --%>
                <jive:i18n key="spell.misspelled" />

            </ww:if>
            <ww:else>

                <%-- Duplicated Word --%>
                <jive:i18n key="spell.repeated" />

            </ww:else>
        </th>
    </tr>
    <tr valign="top">

        <ww:if test="misspelledWords == true">

            <td>
                <%-- Change to: --%>
                <jive:i18n key="spell.change_to" /><jive:i18n key="global.colon" />
            </td>
            <td>
                <input type="text" name="newWord" size="20" maxlength="50" value="<ww:property value="suggestions[0]" />">
            </td>
            <td rowspan="2">

                <table cellpadding="2" cellspacing="0" border="0">
                <tr>
                    <%-- Change --%>
                    <td><input type="submit" name="doChange" value="<jive:i18n key="spell.change" />" class="jive-spell-button"></td>
                    <%-- Ignore --%>
                    <td><input type="submit" name="doIgnore" value="<jive:i18n key="spell.ignore" />" class="jive-spell-button"></td>
                </tr>
                <tr>
                    <%-- Change all --%>
                    <td><input type="submit" name="doChangeAll" value="<jive:i18n key="spell.change_all" />" class="jive-spell-button"></td>
                    <%-- Ignore all --%>
                    <td><input type="submit" name="doIgnoreAll" value="<jive:i18n key="spell.ignore_all" />" class="jive-spell-button"></td>
                </tr>
                </table>

            </td>

         </ww:if>
         <ww:else>

            <%-- Remove dupblicated words --%>
            <td><input type="submit" name="doDeleteSecond" value="<jive:i18n key="spell.remove_duplicated_word" />" class="jive-spell-button"></td>
            <%-- Ignore --%>
            <td><input type="submit" name="doIgnore" value="<jive:i18n key="spell.ignore" />" class="jive-spell-button"></td>

         </ww:else>
    </tr>
    <ww:if test="misspelledWords == true">
        <tr valign="top">
            <td>
                <%-- Suggestions: --%>
                <jive:i18n key="spell.suggestions" /><jive:i18n key="global.colon" />
            </td>
            <td>
                <select size="5" name="suggestions"
                 onchange="this.form.newWord.value=this.options[this.selectedIndex].value;">
                <ww:iterator value="suggestions">
                    <option value="<ww:property id="suggestion" />"><ww:property value="@suggestion" />
                </ww:iterator>
                </select>
            </td>
        </tr>
    </ww:if>
    </table>

    <br><br>

</ww:if>

</span>

<%-- go back/edit --%>
<input type="submit" name="doEdit" value="<jive:i18n key="global.go_back_or_edit" />">
<%-- post message --%>
<input type="submit" name="doPostNow" value="<jive:i18n key="global.post_message" />">

</form>

<jsp:include page="footer.jsp" flush="true" />