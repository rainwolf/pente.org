<%@ page import="java.util.*" %>
<%@ page errorPage="../../five00.jsp" %>
<%@ page contentType="text/html; charset=UTF-8" %>

<%! private static final String loggedInTabs[][] = new String[][]{
   {"/gameServer/index.jsp", "Home"},
   {"/gameServer/myprofile", "My Profile"},
   {"/gameServer/forums", "Forums"},
   {"/help/helpWindow.jsp?file=gettingStarted", "Help"},
   {"/help/helpWindow.jsp?file=faq", "FAQ"},
   {"/gameServer/logout", "Logout"}
};
%>

<% if (request.getAttribute("name") != null) {
   pageContext.setAttribute("tabs", loggedInTabs);
} %>


<% String file = request.getParameter("file");
   String bookmark = null;
   int bookmarkIndex = file.indexOf('#');
   if (bookmarkIndex != -1) {
      file = file.substring(0, bookmarkIndex);
      bookmark = file.substring(bookmarkIndex + 1);
   }
%>
<% pageContext.setAttribute("current", file.startsWith("faq") ? "FAQ" : "Help"); %>
<% pageContext.setAttribute("title", file.startsWith("faq") ? "FAQ" : "Help"); %>
<%@ include file="../top.jsp" %>

<style type="text/css">
    h2 {
        font-size: 14px;
        font-weight: bold;
    }
</style>


<div class="pagebody">
   <table width="800" border="0" cellspacing="0" cellpadding="0">
      <tr>
         <td valign="top" nowrap bgcolor="#deecde">
            <font color="black" size="2" face="Verdana, Arial, Helvetica, sans-serif">
               <b>Table of Contents</b><br>
            </font>
            <%@ include file="helpTOC.jsp" %>
            <br>
         </td>
         <td width="10">&nbsp;</td>
         <td valign="top" width="100%" style="margin-left:20px;">
            <% if (file != null) {
               file += ".jsp"; %>
            <jsp:include page="<%= file %>" flush="true"/>
            <% } %>
         </td>
      </tr>
   </table>
</div>

<% if (bookmark != null) { %>
<script type="text/javascript">addLoadEvent(function () {
   window.location =
#<%= bookmark %>
});</script>
<% } %>
<%@ include file="../bottom.jsp" %>