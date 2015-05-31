<%
/**
 * $RCSfile: move.jsp,v $
 * $Revision: 1.5 $
 * $Date: 2002/12/20 17:45:27 $
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
        <%-- Move Topic --%>
        <jive:i18n key="movetopic.title" />
        </p>

        <%--
            To move this topic to another forum, use the form below to select the destination
            forum and click "Move Topic".
        --%>
        <jive:i18n key="movetopic.description" />

    </td>
    <td width="1%"><img src="images/blank.gif" width="10" height="1" border="0"></td>
    <td width="1%">

        <%@ include file="accountbox.jsp" %>

    </td>
</tr>
</table>

<br>

<form action="move!execute.jspa" name="moveform">
<input type="hidden" name="forumID" value="<ww:property value="$forumID" />">
<input type="hidden" name="threadID" value="<ww:property value="$threadID" />">

<ww:if test="hasErrorMessages == true">
    <span class="jive-error-text">
    <%-- Error: --%>
    <jive:i18n key="global.error" /><jive:i18n key="global.colon" />
    <ul>
        <ww:iterator value="errorMessages">
            <ww:property />
        </ww:iterator>
    </ul>
    </span>
    <br>
</ww:if>

<%-- Choose a destination forum: --%>
<jive:i18n key="movetopic.choose_dest_form" /><jive:i18n key="global.colon" />

<select size="1" name="targetForumID">
<option value=""><%-- [Root Category] --%>
                 <jive:i18n key="movetopic.root_category" />

<ww:iterator value="forumFactory/forums">
    <option value="<ww:property value="ID" />"> &nbsp;&#149;&nbsp; <ww:property value="name" />
</ww:iterator>
<ww:iterator value="forumFactory/rootForumCategory/categories">
    <option value="">[<ww:property value="name" />]
    <ww:iterator value="forums">
        <option value="<ww:property value="ID" />"> &nbsp;&#149;&nbsp; <ww:property value="name" />
    </ww:iterator>
</ww:iterator>
</select>

<br>

<span class="jive-description">
<%-- (Note, categories are marked [in brackets] - please choose a forum marked by bullets: &#149;.) --%>
<jive:i18n key="movetopic.note" />
</span>

<br><br><br>

<%-- Move Topic --%>
<input type="submit" name="doMove" value="<jive:i18n key="movetopic.move_topic" />">
<%-- Cancel --%>
<input type="submit" name="doCancel" value="<jive:i18n key="global.cancel" />">

</form>

<script language="JavaScript" type="text/javascript">
<!--
document.moveform.doMove.focus();
//-->
</script>

<jsp:include page="footer.jsp" flush="true" />