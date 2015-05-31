<%
/**
 * $RCSfile: lock.jsp,v $
 * $Revision: 1.5 $
 * $Date: 2002/12/20 08:07:29 $
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

        <p class="jive-page-title">
        <%-- Lock Topic --%>
        <jive:i18n key="lock.title" />
        </p>

        <%-- To lock this topic and block new messages, click the "Lock Topic" button below. --%>
        <jive:i18n key="lock.description" />

    </td>
    <td width="1%"><img src="images/blank.gif" width="10" height="1" border="0"></td>
    <td width="1%">

        <%@ include file="accountbox.jsp" %>

    </td>
</tr>
</table>

<br>

<ww:property value="thread">
    <%--
        Are you sure you want to lock the topic "{TOPIC_NAME}"?
    --%>
    <jive:i18n key="lock.confirm">
        <jive:arg>
            <a href="thread.jspa?forumID=<ww:property value="$forumID" />&threadID=<ww:property value="$threadID" />" target="_blank"
             ><ww:property value="name" /></a>
        </jive:arg>
    </jive:i18n>
</ww:property>

<br><br>

<form action="lock!execute.jspa" name="lockform">
<input type="hidden" name="forumID" value="<ww:property value="$forumID" />">
<input type="hidden" name="threadID" value="<ww:property value="$threadID" />">

<%-- Lock Topic --%>
<input type="submit" name="doLock" value="<jive:i18n key="lock.lock_topic" />">
<%-- Cancel --%>
<input type="submit" name="doCancel" value="<jive:i18n key="global.cancel" />">

</form>

<script language="JavaScript" type="text/javascript">
<!--
document.lockform.doLock.focus();
//-->
</script>

<jsp:include page="footer.jsp" flush="true" />