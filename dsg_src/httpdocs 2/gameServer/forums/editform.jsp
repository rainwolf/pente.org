<%@ page import="java.text.DateFormat"%>
<%
/**
 *	$RCSfile: editform.jsp,v $
 *	$Revision: 1.12 $
 *	$Date: 2002/12/20 23:40:40 $
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

        <p class="jive-page-title">
        <%-- Edit Message --%>
        <jive:i18n key="edit.title" />
        </p>

        <%-- Editing message: XXX in forum: YYY --%>
        <jive:i18n key="edit.edit_message_in_forum">
            <jive:arg>
                <a href="thread.jspa?forumID=<ww:property value="$forumID" />&threadID=<ww:property value="$threadID" />&messageID=<ww:property value="message/ID" />#<ww:property value="message/ID" />"
                 ><ww:property value="message/subject" /></a>
            </jive:arg>
            <jive:arg>
                <a href="forum.jspa?forumID=<ww:property value="$forumID" />"><ww:property value="forum/name" /></a>
            </jive:arg>
        </jive:i18n>

    </td>
    <td width="1%"><img src="images/blank.gif" width="10" height="1" border="0"></td>
    <td width="1%">

        <%@ include file="accountbox.jsp" %>

    </td>
</tr>
</table>

<p>
<%--
    Use the form below to edit this message. When you're done, click "Save" to save the message and
    return to the topic. You can preview your changes by clicking "Preview" or cancel and go back
    to the topic by clicking "Cancel".
--%>
<jive:i18n key="edit.description" />
</p>

<ww:if test="hasErrors == true || hasErrorMessages == true">

    <table class="jive-error-message" cellpadding="3" cellspacing="2" border="0" width="350">
    <tr valign="top">
        <td width="1%"><img src="images/error-16x16.gif" width="16" height="16" border="0"></td>
        <td width="99%">

            <span class="jive-error-text">

            <ww:if test="errors['subject']">

                <%-- Please enter a subject. --%>
                <jive:i18n key="post.error_subject" />

            </ww:if>
            <ww:elseIf test="errors['body']">

                <%-- You can not post a blank message. Please type your message and try again. --%>
                <jive:i18n key="post.error_body" />

            </ww:elseIf>
            <ww:else>
                <ww:iterator value="errorMessages">
                    <ww:property /> <br>
                </ww:iterator>
            </ww:else>

            </span>

        </td>
    </tr>
    </table>

    <br>

</ww:if>

<ww:if test="hasInfoMessages == true">

    <table class="jive-info-message" cellpadding="3" cellspacing="2" border="1" width="350">
    <tr valign="top">
        <td width="1%"><img src="images/info-icon.gif" width="16" height="16" border="1"></td>
        <td width="99%">

            <span class="jive-info-text">

            <ww:iterator value="infoMessages">
                <ww:property /> <br>
            </ww:iterator>

            </span>

        </td>
    </tr>
    </table>

    <br><br>

</ww:if>

<ww:if test="hasWarningMessages == true">

    <table class="jive-warning-message" cellpadding="3" cellspacing="2" border="1" width="350">
    <tr valign="top">
        <td width="1%"><img src="images/warning-icon.gif" width="16" height="16" border="1"></td>
        <td width="99%">

            <span class="jive-warning-text">

            <ww:iterator value="warningMessages">
                <ww:property /> <br>
            </ww:iterator>

            </span>

        </td>
    </tr>
    </table>

    <br><br>

</ww:if>

<form action="edit!execute.jspa" method="post" name="editform" onsubmit="return checkPost();">
<input type="hidden" name="forumID" value="<ww:property value="$forumID" />">
<ww:if test="$threadID">
    <input type="hidden" name="threadID" value="<ww:property value="$threadID" />">
</ww:if>
<ww:if test="$messageID">
    <input type="hidden" name="messageID" value="<ww:property value="$messageID" />">
</ww:if>
<ww:if test="reply == true">
    <input type="hidden" name="reply" value="<ww:property value="true" />">
</ww:if>

<ww:bean name="'com.jivesoftware.webwork.util.Counter'" id="tabIndex">
    <ww:param name="'first'" value="1" />
</ww:bean>

<span class="jive-post-form">

<table cellpadding="3" cellspacing="2" border="0">
<ww:if test="guest == true">

    <tr>
        <td class="jive-label">
            <%-- Name: --%>
            <jive:i18n key="global.name" /><jive:i18n key="global.colon" />
        </td>
        <td>
            <input type="text" name="name" size="30" maxlength="75" tabindex="<ww:property value="@tabIndex/next" />"
             value="<ww:if test="name"><ww:property value="name" /></ww:if>">
        </td>
    </tr>
    <tr>
        <td class="jive-label">
            <%-- Email: --%>
            <jive:i18n key="global.email" /><jive:i18n key="global.colon" />
        </td>
        <td>
            <input type="text" name="email" size="30" maxlength="75" tabindex="<ww:property value="@tabIndex/next" />"
             value="<ww:if test="email"><ww:property value="email" /></ww:if>">
        </td>
    </tr>

</ww:if>

<tr>
    <td class="jive-label">
        <%-- Subject: --%>
        <jive:i18n key="global.subject" /><jive:i18n key="global.colon" />
    </td>
    <td>
        <input type="text" name="subject" size="60" maxlength="75" tabindex="<ww:property value="@tabIndex/next" />"
         value="<ww:if test="message/unfilteredSubject"><ww:property value="message/unfilteredSubject" /></ww:if>">
    </td>
</tr>
<tr>
    <td>&nbsp;</td>
    <td>
        <table class="jive-font-buttons" cellpadding="2" cellspacing="0" border="0">
        <tr>
            <td><a href="#" onclick="styleTag('b',document.editform.body);return false;"
                 title="<jive:i18n key="post.bold" />"
                 ><img src="images/bold.gif" width="20" height="22" border="0"></a></td>
            <td><a href="#" onclick="styleTag('i',document.editform.body);return false;"
                 title="<jive:i18n key="post.italic" />"
                 ><img src="images/italics.gif" width="20" height="22" border="0"></a></td>
            <td><a href="#" onclick="styleTag('u',document.editform.body);return false;"
                 title="<jive:i18n key="post.underline" />"
                 ><img src="images/underline.gif" width="20" height="22" border="0"></a></td>
        </tr>
        </table>
    </td>
</tr>
<tr>
    <td class="jive-label" valign="top">
        <%-- Message: --%>
        <jive:i18n key="global.message" /><jive:i18n key="global.colon" />
    </td>
    <td>
        <textarea name="body" wrap="virtual" cols="58" rows="12" tabindex="<ww:property value="@tabIndex/next" />"
         ><ww:if test="message/unfilteredBody"><ww:property value="message/unfilteredBody" /></ww:if></textarea>
    </td>
</tr>
<tr>
    <td>&nbsp;</td>
    <td>
        <input type="checkbox" name="addComment" value="true" id="cb01" checked>
        <label for="cb01"><jive:i18n key="edit.add_text" /></label> - <jive:i18n key="edit.add_text_explanation" />
    </td>
</tr>
<tr>
    <td>&nbsp;</td>
    <td>

    <%  // Date formatter for the date in the textare below
        DateFormat formatter = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);
    %>

<textarea name="comment" cols="58" rows="2" wrap="virtual">
<%-- Message was edited by: USERNAME at DATE --%>
<jive:i18n key="edit.message_edit_by">
    <jive:arg>
        <ww:property value="pageUser/username" />
    </jive:arg>
    <jive:arg>
        <%= formatter.format(new java.util.Date()) %>
    </jive:arg>
</jive:i18n>
</textarea>

    </td>
</tr>
<tr>
    <td>&nbsp;</td>
    <td>
        <%-- Save --%>
        <input type="submit" name="doPost" value="<jive:i18n key="global.save" />" tabindex="<ww:property value="@tabIndex/next" />">
        &nbsp;
        <%-- Cancel --%>
        <input type="submit" name="doCancel" value="<jive:i18n key="global.cancel" />" tabindex="<ww:property value="@tabIndex/next" />">
    </td>
</tr>
</table>

</span>

</form>

<script language="JavaScript" type="text/javascript">
<!--
    <ww:if test="guest == true">
        document.editform.name.focus();
    </ww:if>
    <ww:else>
        document.editform.body.focus();
    </ww:else>
//-->
</script>

<jsp:include page="footer.jsp" flush="true" />