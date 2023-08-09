<%
   /**
    *	$RCSfile: toolbar.jsp,v $
    *	$Revision: 1.1 $
    *	$Date: 2002/08/16 06:52:22 $
    */
%>

<%@ page
   import="com.jivesoftware.forum.*,
           com.jivesoftware.forum.util.*,
           com.jivesoftware.util.ParamUtils"
%>

<%@ include file="global.jsp" %>

<% ////////////////
   // get parameters

   String tab = ParamUtils.getParameter(request, "tab");
   if (tab == null) {
      tab = "global";
   }
%>

<html>
<head>
   <title>toolbar.jsp</title>
   <link rel="stylesheet" href="style/global.css">
</head>

<body marginwidth=0 marginheight=0 leftmargin=0 topmargin=0
      bgcolor="#dddddd" text="#000000" link="#0000ff" vlink="#0000ff" alink="#ff0000">

<table class="toolbarBg" cellpadding="0" cellspacing="0" border="0" height="100%">
   <td width="99%">
      <font face="verdana,arial,helvetica" size="-1">
         &nbsp;

         <%-- "Global" tab --%>
         <a href="sidebar.jsp?tree=system"
            target="sidebar" class="toolbarLink"
            title="Goto the Global menu"
            onclick="location.href='toolbar.jsp?tab=global';"
         ><%= (tab.equals("global")) ? "<b>Global</b>" : "Global" %>
         </a>

         &nbsp;

         <%-- "Forums" tab --%>
         <a href="sidebar.jsp?tree=forum"
            target="sidebar" class="toolbarLink"
            title="Goto the Forums menu"
            onclick="location.href='toolbar.jsp?tab=forums';"
         ><%= (tab.equals("forums")) ? "<b>Forums</b>" : "Forums" %>
         </a>

         &nbsp;

         <%-- "Rewards" tab --%>
         <a href="sidebar.jsp?tree=reward"
            target="sidebar" class="toolbarLink"
            title="Goto the Rewards menu"
            onclick="location.href='toolbar.jsp?tab=rewards';"
         ><%= (tab.equals("rewards")) ? "<b>Rewards</b>" : "Rewards" %>
         </a>

         &nbsp;

         <%-- "watches" tab --%>
         <a href="sidebar.jsp?tree=watches"
            target="sidebar" class="toolbarLink"
            title="Goto the Watches menu"
            onclick="location.href=watches.jsp?tab=watches';"
         ><%= (tab.equals("watches")) ? "<b>Watches</b>" : "Watches" %>
         </a>

      </font>
   </td>
   <td width="1%" nowrap>

      <%-- logout link --%>
      <% // get the username %>
      <% try { %>
      <% %>
      <% UserManager manager = forumFactory.getUserManager(); %>
      <% User user = manager.getUser(authToken.getUserID()); %>
      Logged in as: <b><%= user.getUsername() %>
   </b>
      <% } catch (Exception ignored) {
      } %>
      &nbsp;
      <a href="index.jsp?logout=true"
         target="_top" class="toolbarLink"
         title="Logout of the Jive Admin tool"
      ><b>Logout</b></a>

      &nbsp;
   </td>
</table>

</body>
</html>
