<%@ page import="java.util.*,
                 org.pente.gameServer.core.*,
                 org.pente.message.*,
                 com.jivesoftware.base.*,
                 com.jivesoftware.base.filter.*,
                 com.jivesoftware.forum.*" %>

<%! private static com.jivesoftware.base.FilterChain filters; %>
<%! static {
   Emoticon emoticon = new Emoticon();
   emoticon.setImageURL("/gameServer/forums/images/emoticons");
   filters = new com.jivesoftware.base.FilterChain(
      null, 1, new com.jivesoftware.base.Filter[]{
      new HTMLFilter(), new URLConverter(), emoticon, new Newline()},
      new long[]{1, 1, 1, 1});
}
%>

<%
   DSGMessage replyToMessage = (DSGMessage) request.getAttribute("message");
   String to = "";
   String subject = "";
   String body = "";
   String pageTitle = "New Message";

   if (replyToMessage != null) {

      to = (String) request.getAttribute("to");
      if (replyToMessage.getSubject().startsWith("Re:")) {
         subject = replyToMessage.getSubject();
      } else {
         subject = "Re: " + replyToMessage.getSubject();
      }
      pageTitle = subject;
   } else {
      to = request.getParameter("to");
      if (to == null) {
         to = "";
      }
      subject = request.getParameter("subject");
      if (subject == null) {
         subject = "";
      }
      body = request.getParameter("body");
      if (body == null) {
         body = "";
      }
   }
%>

<% pageContext.setAttribute("title", pageTitle); %>

<%@ include file="begin.jsp" %>

<table width="100%" border="0" colspacing="0" colpadding="0">
   <tr>
      <td>
         <h3>Send Message</h3>
      </td>
   </tr>
   <%
      String error = (String) request.getAttribute("error");
      if (error != null) { %>
   <tr>
      <td>
         <font face="Verdana, Arial, Helvetica, sans-serif" size="2" color="<%= textColor2 %>">
            <b>Error: <%= error %>
            </b>
         </font>
      </td>
   </tr>
   <% } %>

   <tr>
      <td>

         <form name="message_form" method="post"
               action="/gameServer/mymessages">
            <input type="hidden" name="command" value="create">
            <table border="0" colspacing="1" colpadding="1">
               <tr>
                  <td>
                     <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
                        From:
                     </font>
                  </td>
                  <td>
                     <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
                        <%= request.getAttribute("name") %>
                     </font>
                  </td>
               </tr>
               <tr>
                  <td>
                     <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
                        To:
                     </font>
                  </td>
                  <td>
                     <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
                        <input type="text" name="to" size="10" maxlength="10" value="<%= to %>">
                     </font>
                  </td>
               </tr>
               <tr>
                  <td>
                     <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
                        Subject:
                     </font>
                  </td>
                  <td>
                     <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
                        <input type="text" name="subject" size="60" maxlength="75" value="<%= subject %>">
                     </font>
                  </td>
               </tr>
               <tr>
               <tr>
                  <td valign="top">
                     <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
                        Message:
                     </font>
                  </td>
                  <td>
                     <textarea name="body" wrap="virtual" cols="58" rows="12"><%= body %></textarea>
                  </td>
               </tr>
               <% if (replyToMessage != null) { %>
               <tr>
                  <td valign="top">
                     <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
                        Original Message:<br>
                  </td>
                  <td valign="top" bgcolor="<%= bgColor2 %>">
                     <%= filters.applyFilters(0, replyToMessage.getBody()) %>
                  </td>
               </tr>
               <% } %>
               <tr>
                  <td>&nbsp;</td>
                  <td align="left">
                     <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
                        <input type="submit" value="Send Message">
                     </font>
                  </td>
               </tr>
            </table>
         </form>
      </td>
   </tr>

</table>

<%@ include file="end.jsp" %>