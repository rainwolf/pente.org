 	<%@ page import="java.util.*,
                 org.pente.gameServer.core.*,
                 org.pente.message.*,
	             org.pente.turnBased.web.TBEmoticon,
	             com.jivesoftware.base.*,
	             com.jivesoftware.base.filter.*" %>

<%! private static com.jivesoftware.base.FilterChain filters; %>
<%! static {
TBEmoticon emoticon = new TBEmoticon();
emoticon.setImageURL("/gameServer/forums/images/emoticons");
filters = new com.jivesoftware.base.FilterChain(
    null, 1, new com.jivesoftware.base.Filter[] { 
        new HTMLFilter(), new URLConverter(), emoticon, new Newline() }, 
        new long[] { 1, 1, 1, 1 });
}
%>

<%
String error = (String) request.getAttribute("error");
List<DSGMessage> messages = (List<DSGMessage>) request.getAttribute("messages");

Resources resources = (Resources) application.getAttribute(
    Resources.class.getName());
DSGPlayerStorer dsgPlayerStorer = resources.getDsgPlayerStorer();

%>

<% pageContext.setAttribute("title", "My Messages"); %>
<%@ include file="begin.jsp" %>

<link rel="stylesheet" type="text/css" href="<%= request.getContextPath() %>/gameServer/forums/style.jsp" />

<%
DateFormat messageDateFormat = null;
{
DSGPlayerData meData = dsgPlayerStorer.loadPlayer(me);
TimeZone tz = TimeZone.getTimeZone(meData.getTimezone());
messageDateFormat = new SimpleDateFormat("MMM dd, yyyy hh:mm aa z");
messageDateFormat.setTimeZone(tz);
}
%>

<form name="delete_form" method="post" action="/gameServer/mymessages">
  <input type="hidden" name="command" value="delete">

<script language="javascript">
  function checkAll() {
    for (i = 1; i < document.delete_form.mid.length; i++) {
      document.delete_form.mid[i].checked = document.delete_form.mid[0].checked;
    }
  }
</script>

<table width="100%" border="0" colspacing="0" colpadding="0">


<tr>
 <td>
  <h3>My Messages</h3>
 </td>
</tr>
<tr>
  <td>

 <table cellpadding="0" cellspacing="0" border="0">
    <tr><td height="10"></td></tr>
   <tr>
     <td>
       <a href="/gameServer/newMessage.jsp">
	     <img src="/gameServer/forums/images/post-16x16.gif" width="16" height="16" 
	        border="0"></a>&nbsp;
	 </td>
     <td valign="middle">
       <a href="/gameServer/newMessage.jsp">
	     New Message</a>&nbsp;&nbsp;&nbsp;
     </td>
     
     <td>
       <a href="javascript:document.delete_form.submit()">
	     <img src="/gameServer/forums/images/delete-16x16.gif" width="16" height="16" 
	        border="0"></a>&nbsp;
	 </td>
     <td valign="middle">
       <a href="javascript:document.delete_form.submit()">
	     Delete Messages</a>&nbsp;&nbsp;&nbsp;
     </td>
     
     <td>
       <a href="/gameServer/myprofile/prefs#email">
	     <img src="/gameServer/images/email.gif" width="16" height="16" 
	        border="0"></a>&nbsp;
	 </td>
     <td valign="middle">
       <a href="/gameServer/myprofile/prefs#email">
	     Configure Email Preferences</a>
     </td>
    </tr>
  </table>
  
  </td>
</tr>
<tr>
  <td>
    <span id="jive-topic-list">
      <table class="jive-list" cellpadding="3" cellspacing="2" width="100%">
        <tr>
          <th>All<input type="checkbox" name="mid" value="0" onclick="javascript:checkAll()"></th>
          <th class="jive-forum-name" colspan="2">Subject</th>
          <th class="jive-author">From</th>
          <th class="jive-date" nowrap>Date</th>
        </tr>

<% int i = 0;
   for (DSGMessage m : messages) {
       i++; 
       DSGPlayerData from = dsgPlayerStorer.loadPlayer(m.getFromPid()); %>
        <tr class="<%= (i % 2 == 0 ? "jive-even" : "jive-odd") %>" valign="top">
          <td width="1%" valign="middle">
            <input type="checkbox" name="mid" value="<%= m.getMid() %>">
          </td>
          <td width="1%" valign="middle">
            <% String img = m.isRead() ? "read.gif" : "unread.gif"; %>
            <img src="/gameServer/forums/images/<%= img %>" width="9" height="9" border="0">
          </td>
          <td class="jive-topic-name" width="96%">
          <%
            String subject = filters.applyFilters(0, m.getSubject());
            if ("".equals(subject)) {
              subject = "(no subject)";
            }
          %>
            <a href="mymessages?command=view&mid=<%= m.getMid() %>"><%= subject %></a>
          </td>
          <td class="jive-author" width="1%" nowrap>
            <a href="/gameServer/profile?viewName=<%= from.getName() %>"><%= from.getName() %></a>
          </td>
          <td class="jive-date" width="1%" nowrap>
            <%= messageDateFormat.format(m.getCreationDate()) %>
          </td>
        </tr>
<% } %>
      </table>
    </span>
  </td>
</tr>
</table>
</form>

<%@ include file="end.jsp" %>