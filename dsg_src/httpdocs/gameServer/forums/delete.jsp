<%
/**
 * $RCSfile: delete.jsp,v $
 * $Revision: 1.6 $
 * $Date: 2002/12/20 23:23:21 $
 */
%>

<%@ include file="global.jsp" %>

<%@ taglib uri="webwork" prefix="ww" %>
<%@ taglib uri="jivetags" prefix="jive" %>

<jsp:include page="header.jsp" flush="true" />

<table cellpadding="0" cellspacing="0" border="0" width="100%">
<tr valign="top">
    <td width="98%">

        <%-- Breadcrumbs (customizable via the admin tool) --%>

        <jsp:include page="breadcrumbs.jsp" flush="true" />

        <%-- Forum name and brief info about the forum --%>

        <p>
        <span class="jive-page-title">
        <ww:if test="isThreadDeletion == true">

            <%-- Delete Topic --%>
            <jive:i18n key="delete.topic" />

        </ww:if>
        <ww:else>

            <%-- Delete Message --%>
            <jive:i18n key="delete.message" />

        </ww:else>
        </span>
        </p>

        <ww:if test="isThreadDeletion == true">

            <%--
                Warning: This will delete the topic and all of its messages. If you're sure you
                want to proceed, click "Delete Topic" below.
            --%>
            <jive:i18n key="delete.topic_warning" />

        </ww:if>
        <ww:else>

            <%--
                Warning: This will delete the message and all of its replies. If you're sure
                you want to proceed, click "Delete Message" below.
            --%>
            <jive:i18n key="delete.message_warning" />

        </ww:else>

    </td>
    <td width="1%"><img src="images/blank.gif" width="10" height="1" border="0"></td>
    <td width="1%">

        <%@ include file="accountbox.jsp" %>

    </td>
</tr>
</table>

<br>

<ww:if test="isThreadDeletion == true">

    <%--
        Are you sure you want to delete the topic "xxx" and all of its messages?
    --%>
    <jive:i18n key="delete.topic_confirm">
        <jive:arg>
            <a href="thread.jspa?forumID=<ww:property value="$forumID" />&threadID=<ww:property value="$threadID" />" target="_blank"
             ><ww:property value="thread/name" /></a>
        </jive:arg>
    </jive:i18n>

</ww:if>
<ww:else>

    <%-- Replies to this message: XX --%>

    <jive:i18n key="global.replies_to_this_message" /><jive:i18n key="global.colon" />
    <ww:property value="thread/treeWalker/childCount(message)" />

    <br>

    <%--
        Are you sure you want to delete the message "xxx" and all of its messages?
    --%>
    <jive:i18n key="delete.message_confirm">
        <jive:arg>
            <a href="thread.jspa?forumID=<ww:property value="$forumID" />&threadID=<ww:property value="$threadID" />&messageID=<ww:property value="message/ID" />#<ww:property value="message/ID" />" target="_blank"
             ><ww:property value="message/subject" /></a>
        </jive:arg>
    </jive:i18n>

</ww:else>

<br><br>

<form action="delete!execute.jspa" name="deleteform">
<input type="hidden" name="forumID" value="<ww:property value="$forumID" />">
<input type="hidden" name="threadID" value="<ww:property value="$threadID" />">
<input type="hidden" name="messageID" value="<ww:property value="messageID" />">

<ww:if test="isThreadDeletion == true">

    <%-- Delete Topic --%>
    <input type="submit" name="doDelete" value="<jive:i18n key="delete.topic" />">

</ww:if>
<ww:else>

    <%-- Delete Message and Replies --%>
    <input type="submit" name="doDelete" value="<jive:i18n key="delete.message_and_replies" />">

</ww:else>

<%-- Cancel --%>
<input type="submit" name="doCancel" value="<jive:i18n key="global.cancel" />">

</form>

<script language="JavaScript" type="text/javascript">
<!--
document.deleteform.doDelete.focus();
//-->
</script>

<jsp:include page="footer.jsp" flush="true" />