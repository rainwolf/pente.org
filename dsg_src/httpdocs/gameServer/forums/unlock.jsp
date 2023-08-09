<%
   /**
    * $RCSfile: unlock.jsp,v $
    * $Revision: 1.6 $
    * $Date: 2002/12/20 21:14:48 $
    */
%>

<%@ include file="global.jsp" %>

<%@ taglib uri="webwork" prefix="ww" %>
<%@ taglib uri="jivetags" prefix="jive" %>

<jsp:include page="header.jsp" flush="true"/>

<table cellpadding="0" cellspacing="0" border="0" width="100%">
   <tr valign="top">
      <td width="98%">

         <%-- Breadcrumbs (customizable via the admin tool) --%>

         <jsp:include page="breadcrumbs.jsp" flush="true"/>

         <%-- Forum name and brief info about the forum --%>

         <p class="jive-page-title">
            <%-- Unlock Topic --%>
            <jive:i18n key="unlock.title"/>
         </p>

         <%-- To unlock this topic and allow new replies, click the "Unlock Topic" button below. --%>
         <jive:i18n key="unlock.description"/>

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
       Are you sure you want to unock the topic "{topic name}"?
   --%>
   <jive:i18n key="unlock.confirm">
      <jive:arg>
         <a href="thread.jspa?forumID=<ww:property value="$forumID" />&threadID=<ww:property value="$threadID" />"
            target="_blank"
         ><ww:property value="name"/></a>
      </jive:arg>
   </jive:i18n>

</ww:property>

<br><br>

<form action="unlock!execute.jspa" name="unlockform">
   <input type="hidden" name="forumID" value="<ww:property value="$forumID" />">
   <input type="hidden" name="threadID" value="<ww:property value="$threadID" />">

   <%-- Unlock Topic --%>
   <input type="submit" name="doUnlock" value="<jive:i18n key="unlock.unlock_topic" />">
   <%-- Cancel --%>
   <input type="submit" name="doCancel" value="<jive:i18n key="global.cancel" />">

</form>

<script language="JavaScript" type="text/javascript">
   <!--
   document.unlockform.doUnlock.focus();
   //-->
</script>

<jsp:include page="footer.jsp" flush="true"/>