<%@ page import="java.util.*,
                 java.io.*,
                 org.pente.gameServer.core.*,
                 org.pente.gameServer.client.web.*" %>
<%
   {
      DSGPlayerData d = null;
      List<DSGDonationData> donors = dsgPlayerStorer.getAllPlayersWhoDonated();
      int rand = (int) (Math.random() * donors.size());
      DSGDonationData dd = donors.get(rand);
      d = dsgPlayerStorer.loadPlayer(dd.getPid()); %>
<div style="float:left;text-align:center;width:100%;" class="name">
   <%@include file="playerLink.jspf" %>
</div>

<%
   if (d.hasAvatar()) {
      int width = 190;
      try {
         int w = ImageUtils.getWidth(d.getAvatarContentType().substring(6),
            new ByteArrayInputStream(d.getAvatar()));
         if (w < width) width = w;
      } catch (IOException i) {
         i.printStackTrace();
      }
%>
<a href="/gameServer/profile?viewName=<%= d.getName() %>"><img border="0" width="<%= width %>"
                                                               src="/gameServer/avatar?name=<%= d.getName() %>"
                                                               style="border:1px solid gray"></a>
<% } else { %>
<img src="/gameServer/images/no_photo.gif">
<% } %>
<% } %>

<div style="margin-top:5px;" :>
   <div style="float:left;text-align:right;width:45%;margin-right:5px;" class="name">
      <a href="/gameServer/subscriptions"><span style="color:#ff8105">Subscribe<br>today!</span></a></div>
   <div style="float:right;text-align:left;width:45%;"><a href="/gameServer/subscriptions"><img
      src="/gameServer/images/paypal.gif"></a></div>
</div>
<div style="clear:both"></div>
