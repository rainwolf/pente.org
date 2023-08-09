<%
   /**
    * $RCSfile: delete-success.jsp,v $
    * $Revision: 1.5 $
    * $Date: 2002/12/20 05:25:43 $
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

         <p>
        <span class="jive-page-title">
        <ww:if test="isThreadDeletion == true">

           <%-- Topic Deleted Successfully --%>
           <jive:i18n key="delete.topic_deleted_successfully"/>

        </ww:if>
        <ww:else>

           <%-- Message Deleted Successfully --%>
           <jive:i18n key="delete.message_deleted_successfully"/>

        </ww:else>
        </span>
         </p>

         <ww:if test="isThreadDeletion == true">

            <%-- You have successfully deleted the topic. --%>
            <jive:i18n key="delete.you_have_deleted_topic"/>

         </ww:if>
         <ww:else>

            <%-- You have successfully deleted the message. --%>
            <jive:i18n key="delete.you_have_deleted_message"/>

         </ww:else>

      </td>
      <td width="1%"><img src="images/blank.gif" width="10" height="1" border="0"></td>
      <td width="1%">

         <%@ include file="accountbox.jsp" %>

      </td>
   </tr>
</table>

<br>

<%-- Choose where you'd like to go: --%>
<jive:i18n key="global.choose_where_like_to_go"/><jive:i18n key="global.colon"/>
<ul>
   <ww:if test="isThreadDeletion == true">

   <li><%-- Go to: This Topic's Forum --%>
         <jive:i18n key="global.go_to"/>
         <jive:i18n key="global.colon"/>
      <a href="forum.jspa?forumID=<ww:property value="$forumID" />"
      ><jive:i18n key="global.this_topics_forum"/></a>

      </ww:if>
      <ww:else>

   <li><%-- Go to: This Topic --%>
         <jive:i18n key="global.go_to"/>
         <jive:i18n key="global.colon"/>
      <a href="thread.jspa?forumID=<ww:property value="$forumID" />&threadID=<ww:property value="$threadID" />"
      ><jive:i18n key="global.this_topic"/></a>.

      </ww:else>

   <li><%-- Go to: The Main Forums Page --%>
      <jive:i18n key="global.go_to"/>
      <jive:i18n key="global.colon"/>
      <a href="index.jspa"><jive:i18n key="global.the_main_forums_page"/></a>.
</ul>

<jsp:include page="footer.jsp" flush="true"/>