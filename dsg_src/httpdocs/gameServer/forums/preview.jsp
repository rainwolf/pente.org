<%
/**
 *	$RCSfile: preview.jsp,v $
 *	$Revision: 1.7 $
 *	$Date: 2002/12/20 19:10:47 $
 */
%>

<%@ taglib uri="webwork" prefix="ww" %>
<%@ taglib uri="jivetags" prefix="jive" %>

<%@ include file="global.jsp" %>

<jsp:include page="header.jsp" flush="true" />

<table cellpadding="0" cellspacing="0" border="0" width="100%">
<tr valign="top">
    <td width="98%">

        <%-- Breadcrumbs (customizable via the admin tool) --%>

        <jsp:include page="breadcrumbs.jsp" flush="true" />

        <%-- Page title --%>

        <p class="jive-page-title">
        <ww:if test="reply == true">

            <%-- Message Preview: Reply --%>
            <jive:i18n key="preview.post_reply" />

        </ww:if>
        <ww:else>

            <%--  Message Preview: New Topic --%>
            <jive:i18n key="preview.post_new" />

        </ww:else>
        </p>

    </td>
    <td width="1%"><img src="images/blank.gif" width="10" height="1" border="0"></td>
    <td width="1%">

        <%@ include file="accountbox.jsp" %>

    </td>
</tr>
</table>

<br>

<ww:if test="hasInfoMessages == true">

    <table class="jive-info-message" cellpadding="3" cellspacing="2" border="1" width="350">
    <tr valign="top">
        <td width="1%"><img src="images/info-icon.gif" width="16" height="16" border="1"></td>
        <td width="99%">
            <ww:iterator value="infoMessages">
                <ww:property /> <br>
            </ww:iterator>
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
            <ww:iterator value="warningMessages">
                <ww:property /> <br>
            </ww:iterator>
        </td>
    </tr>
    </table>

    <br><br>

</ww:if>

<form action="preview!post.jspa" method="post">

<%-- go back/edit --%>
<input type="submit" name="doEdit" value="<jive:i18n key="global.go_back_or_edit" />">
<%-- post message --%>
<input type="submit" name="doPost" value="<jive:i18n key="global.post_message" />">
<%-- cancel --%>
<input type="submit" name="doCancel" value="<jive:i18n key="global.cancel" />">
</form>

<jsp:include page="footer.jsp" flush="true" />