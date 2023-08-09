<%@ page import="com.jivesoftware.base.JiveGlobals" %>
<%
   /**
    *	$RCSfile: header.jsp,v $
    *	$Revision: 1.2 $
    *	$Date: 2002/10/28 01:47:00 $
    */
%>

<% // Set the content type and character encoding
   response.setContentType("text/html; charset=" + JiveGlobals.getCharacterEncoding());
   // Note: the recommended way to set the character encoding on the request
   // is to enable the SetCharacterEncodingFilter Servlet 2.3 filter in the
   // web.xml file. See the default web.xml that ships with Jive Forums for
   // more information.
%>

<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN">

<html>
<head>
   <title>Jive Forums 3 Admin</title>
   <meta http-equiv="content-type" content="text/html; charset=<%= JiveGlobals.getCharacterEncoding() %>">
   <script language="JavaScript" type="text/javascript">
      <!-- // code for window popups
      function helpwin(page, hashLink) {
         window.open('helpwin.jsp?f=' + page + '&hash=' + hashLink, 'newWindow', 'width=500,height=550,menubar=yes,location=no,personalbar=no,scrollbars=yes,resize=yes');
      }

      //-->
   </script>
   <link rel="stylesheet" href="style/global.css" type="text/css">
</head>

<body<%= onload %>>
