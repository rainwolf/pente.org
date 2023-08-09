<%--
  - $RCSfile: editattachments.jsp,v $
  - $Revision: 1.8.4.1 $
  - $Date: 2003/03/21 20:33:17 $
  -
  - Copyright (C) 2002-2003 Jive Software. All rights reserved.
  -
  - This software is the proprietary information of Jive Software. Use is subject to license terms.
--%>

<%@ include file="global.jsp" %>

<%@ taglib uri="webwork" prefix="ww" %>
<%@ taglib uri="jivetags" prefix="jive" %>

<jsp:include page="header.jsp" flush="true"/>

<script language="JavaScript" type="text/javascript" src="utils.js"></script>

<table cellpadding="0" cellspacing="0" border="0" width="100%">
   <tr valign="top">
      <td width="98%">

         <%-- Breadcrumbs (customizable via the admin tool) --%>

         <jsp:include page="breadcrumbs.jsp" flush="true"/>

         <%-- Edit Attachments --%>

         <p class="jive-page-title">
            <jive:i18n key="editattach.edit_attachments"/>
         </p>

      </td>
      <td width="1%"><img src="images/blank.gif" width="10" height="1" border="0"></td>
      <td width="1%">

         <%@ include file="accountbox.jsp" %>

      </td>
   </tr>
</table>

<ww:if test="hasErrorMessages == true">

    <span class="jive-error-text">
    <ww:iterator value="errorMessages">
       <ww:property/>
    </ww:iterator>
    </span>
   <br><br>

</ww:if>

<ww:if test="message/attachmentCount == 0">

   <%-- There are no files attached to this message. --%>
   <jive:i18n key="editattach.no_files_attached_to_message"/>

   <form action="thread.jspa">
      <input type="hidden" name="forumID" value="<ww:property value="$forumID" />">
      <ww:if test="$threadID">
         <input type="hidden" name="threadID" value="<ww:property value="$threadID" />">
      </ww:if>
      <ww:if test="$messageID">
         <input type="hidden" name="messageID" value="<ww:property value="$messageID" />">
      </ww:if>

      <input type="submit" name="" value="<jive:i18n key="attach.go_back_to_message" />">

   </form>

</ww:if>
<ww:else>

   <p>
         <%--
             Below is a list of files attached to this message. To remove a file, click the delete
             icon next to the filename.
         --%>
      <jive:i18n key="editattach.description"/>
   </p>

   <ww:bean name="'com.jivesoftware.util.ByteFormat'" id="byteFormatter"/>

   <ww:bean name="'com.jivesoftware.webwork.util.Counter'" id="counter">
      <ww:param name="first" value="1"/>
      <ww:param name="last" value="attachmentManager/maxAttachmentsPerMessage"/>
   </ww:bean>

   <span class="jive-edit-attach-list">

    <table class="jive-box" cellpadding="3" cellspacing="2" border="0">
    <tr>
        <th colspan="3">
            <%-- Filename --%>
            <jive:i18n key="global.filename"/>
        </th>
        <th>
            <%-- Size --%>
            <jive:i18n key="global.size"/>
        </th>
        <th>
            <%-- Delete --%>
            <jive:i18n key="global.delete"/>
        </th>
    </tr>

    <ww:iterator value="message/attachments" status="'status'">

        <tr class="<ww:if test="@status/odd==true">jive-odd</ww:if><ww:else>jive-even</ww:else>">
            <td>
                <ww:property value="@counter/next"/>
            </td>
            <td>
                <img
                   src="<%= request.getContextPath() %>/servlet/JiveServlet?attachImage=true&contentType=<ww:property value="contentType" />&attachment=<ww:property value="ID" />"
                   border="0">
            </td>
            <td>
                <a href="<%= request.getContextPath() %>/servlet/JiveServlet/download/<ww:property value="$forumID" />-<ww:property value="$threadID" />-<ww:property value="$messageID" />-<ww:property value="ID" />/<ww:property value="name" />"
                ><ww:property value="name"/></a>
            </td>
            <td>
                <ww:property value="@byteFormatter/format(size)"/>
            </td>
            <td align="center">
                <a href="editattach!delete.jspa?forumID=<ww:property value="$forumID" /><ww:if test="$threadID">&threadID=<ww:property value="$threadID" /></ww:if><ww:if test="$messageID">&messageID=<ww:property value="$messageID" /></ww:if>&attachID=<ww:property value="ID" />"
                ><img src="images/delete-16x16.gif" width="16" height="16" border="0"></a>
            </td>
        </tr>

    </ww:iterator>
    </table>

    </span>

   <ww:if test="@counter/current <= attachmentManager/maxAttachmentsPerMessage">

      <br><br>

      <%-- Attach more files using the form below. --%>
      <jive:i18n key="editattach.attach_more"/>

      <%-- The maximum size per upload file is: XXX --%>
      <jive:i18n key="editattach.max_size_is">
         <jive:arg>
            <ww:property value="@byteFormatter/formatKB(attachmentManager/maxAttachmentSize)"/>
         </jive:arg>
      </jive:i18n>

      <form
         action="editattach!execute.jspa?forumID=<ww:property value="$forumID" /><ww:if test="$threadID">&threadID=<ww:property value="$threadID" /></ww:if><ww:if test="$messageID">&messageID=<ww:property value="$messageID" /></ww:if>"
         method="post" enctype="multipart/form-data" onsubmit="return isClicked();">

         <br>

         <table cellpadding="3" cellspacing="2" border="0">

            <% ValueStack stack = ValueStack.getStack(request);
               long current = ((Long) stack.findValue("@counter/current")).longValue();
               int max = ((Integer) stack.findValue("attachmentManager/maxAttachmentsPerMessage")).intValue();
            %>

            <% while (current++ <= max) { %>

            <tr>
               <td><jive:i18n key="attach.file_in_sequence">
                  <jive:arg><%= (current - 1) %>
                  </jive:arg>
               </jive:i18n></td>
               <td>
                  <input type="file" name="attachFile<%= (current-1) %>" size="40">
               </td>
            </tr>

            <% } %>

         </table>

         <br>

            <%-- Go Back to Message --%>
         <input type="submit" name="doCancel" value="<jive:i18n key="attach.go_back_to_message" />">
            <%-- Attach Files --%>
         <input type="submit" name="doAttach" value="<jive:i18n key="post.attach_files" />">

      </form>

   </ww:if>
   <ww:else>

      <br>
      <form action="thread.jspa">
         <input type="hidden" name="forumID" value="<ww:property value="$forumID" />">
         <ww:if test="$threadID">
            <input type="hidden" name="threadID" value="<ww:property value="$threadID" />">
         </ww:if>
         <ww:if test="$messageID">
            <input type="hidden" name="messageID" value="<ww:property value="$messageID" />">
         </ww:if>
            <%-- Go Back to Message --%>
         <input type="submit" name="doCancel" value="<jive:i18n key="attach.go_back_to_message" />">
      </form>

   </ww:else>

</ww:else>

<jsp:include page="footer.jsp" flush="true"/>