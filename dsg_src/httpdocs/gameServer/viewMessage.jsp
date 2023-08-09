<%@ page import="org.pente.gameServer.core.*,
                 org.pente.message.*,
                 org.pente.turnBased.web.TBEmoticon,
                 com.jivesoftware.base.*,
                 com.jivesoftware.base.filter.*" %>


<%! private static com.jivesoftware.base.FilterChain filters; %>
<%! static {
   TBEmoticon emoticon = new TBEmoticon();
   emoticon.setImageURL("/gameServer/forums/images/emoticons");
   filters = new com.jivesoftware.base.FilterChain(
      null, 1, new com.jivesoftware.base.Filter[]{
      new HTMLFilter(), new URLConverter(), emoticon, new Newline()},
      new long[]{1, 1, 1, 1});
}
%>

<%
   DSGMessage message = (DSGMessage) request.getAttribute("message");
   Resources resources = (Resources) application.getAttribute(
      Resources.class.getName());
   DSGPlayerStorer dsgPlayerStorer = resources.getDsgPlayerStorer();
   DSGPlayerData fromData = dsgPlayerStorer.loadPlayer(message.getFromPid());
%>

<% pageContext.setAttribute("title", "View Message"); %>
<%@ include file="begin.jsp" %>
<%
   DateFormat messageDateFormat = null;
   {
      DSGPlayerData meData = dsgPlayerStorer.loadPlayer(me);
      TimeZone tz = TimeZone.getTimeZone(meData.getTimezone());
      messageDateFormat = new SimpleDateFormat("MMM dd, yyyy hh:mm aa z");
      messageDateFormat.setTimeZone(tz);
   }
%>

<table width="100%" border="0" colspacing="0" colpadding="0">
   <tr>
      <td>
         <h3>View Message</h3>
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
   <% } else { %>

   <tr>
      <td>
         <table border="0" cellspacing="0" cellpadding="0">
            <tr>
               <td height="10"></td>
            </tr>
            <tr>
               <td>
                  <a href="/gameServer/mymessages?command=reply&mid=<%= message.getMid() %>">
                     <img src="/gameServer/forums/images/reply-16x16.gif" width="16" height="16"
                          border="0"></a>&nbsp;
               </td>
               <td valign="middle">
                  <a href="/gameServer/mymessages?command=reply&mid=<%= message.getMid() %>">
                     Reply</a>
                  &nbsp;&nbsp;&nbsp;
               </td>
               <td>
                  <a href="/gameServer/mymessages?command=delete&mid=<%= message.getMid() %>">
                     <img src="/gameServer/forums/images/delete-16x16.gif" width="16" height="16"
                          border="0"></a>&nbsp;
               </td>
               <td valign="middle">
                  <a href="/gameServer/mymessages?command=delete&mid=<%= message.getMid() %>">
                     Delete</a>
               </td>
            </tr>
            <tr>
               <td height="3"></td>
            </tr>
         </table>
         <table border="0" cellspacing="0" cellpadding="0" width="600">

            <tr bgcolor="<%= bgColor1 %>">
               <td colspan="2">
                  <font face="Verdana, Arial, Helvetica, sans-serif" size="2" color="white">
                     <b>Subject:</b>&nbsp;&nbsp;&nbsp;
                  </font>
               </td>
               <td colspan="2" width="100%">
                  <font face="Verdana, Arial, Helvetica, sans-serif" size="2" color="white">
                     <b><%= filters.applyFilters(0, message.getSubject()).replaceAll("http://", "") %>
                     </b>
                  </font>
               </td>
            </tr>
            <tr bgcolor="<%= bgColor2 %>">
               <td width="1" bgcolor="<%= bgColor2 %>"><img src="/gameServer/images/dot2.gif"></td>
               <td>
                  <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
                     From:
                  </font>
               </td>
               <td>
                  <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
                     <%= fromData.getName() %>
                  </font>
               </td>
               <td width="1" bgcolor="<%= bgColor2 %>"><img src="/gameServer/images/dot2.gif"></td>
            </tr>
            <tr bgcolor="<%= bgColor2 %>">
               <td width="1" bgcolor="<%= bgColor2 %>"><img src="/gameServer/images/dot2.gif"></td>
               <td>
                  <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
                     Date:
                  </font>
               </td>
               <td>
                  <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
                     <%= messageDateFormat.format(message.getCreationDate()) %>
                  </font>
               </td>
               <td width="1" bgcolor="<%= bgColor2 %>"><img src="/gameServer/images/dot2.gif"></td>
            </tr>
            <tr>
               <td width="1" bgcolor="<%= bgColor2 %>"><img src="/gameServer/images/dot2.gif"></td>
               <td valign="top" colspan="2">
                  <br>
                  <%= filters.applyFilters(0, message.getBody()).replaceAll("#38;", "") %>
                  <br><br>
               </td>
               <td width="1" bgcolor="<%= bgColor2 %>"><img src="/gameServer/images/dot2.gif"></td>
            </tr>
            <tr>
               <td colspan="4" height="1" bgcolor="<%= bgColor2 %>">
                  <img src="/gameServer/images/dot2.gif">
               </td>
            </tr>

         </table>
         <table border="0" cellspacing="0" cellpadding="0">
            <tr>
               <td height="5"></td>
            </tr>
            <tr>
               <td>
                  <a href="/gameServer/mymessages">
                     <img src="/gameServer/forums/images/back-to-16x16.gif" width="16" height="16"
                          border="0"></a>&nbsp;
               </td>
               <td valign="middle">
                  <a href="/gameServer/mymessages">Back to Messages</a>
               </td>

            </tr>
         </table>
         <br>
      </td>
   </tr>

   <% } %>

</table>

<%@ include file="end.jsp" %>