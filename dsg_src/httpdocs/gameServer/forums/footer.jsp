<%
   /**
    * $RCSfile: footer.jsp,v $
    * $Revision: 1.9.4.1 $
    * $Date: 2003/01/25 16:58:57 $
    */
%>
<%@ page import="com.jivesoftware.forum.*,
                 com.jivesoftware.forum.util.*,
                 java.util.*,
                 com.jivesoftware.base.*"
%>


<% // Set the content type
   response.setContentType("text/html; charset=" + JiveGlobals.getCharacterEncoding());
%>

<%@ taglib uri="jivetags" prefix="jive" %>

<br><br>

<table cellpadding="0" cellspacing="0" border="0" width="100%">
   <tr>
      <td colspan="3" align="center">
         <%-- Powered by Jive Software --%>
         <a href="http://www.jivesoftware.com/poweredby/" target="_blank"><jive:i18n key="footer.powered_by"/></a>
      </td>
   </tr>

</table>

<%@ include file="../colors.jspf" %>
<%@ include file="../end.jsp" %>
