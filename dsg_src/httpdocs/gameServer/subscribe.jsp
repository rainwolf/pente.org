<%@ page import="java.util.*" %>
<%@ page import="java.text.*" %>
<%@ page import="org.pente.gameServer.core.*" %>

<% List<DSGDonationData> donationList = (List<DSGDonationData>) request.getAttribute("donationList"); %>

<% pageContext.setAttribute("title", "Donations"); %>
<%@ include file="begin.jsp" %>



<%
String gifter = request.getParameter("gifter");
if (gifter == null) {
  gifter = "";
}
String giftee = request.getParameter("name");
if (giftee == null) {
  giftee = (String) request.getAttribute("name");
}
if (giftee != null) {
%>
<h3>        Subscribe <%=gifter.equals("")?"":giftee%> to Pente.org </h3>
          
          Pente.org is kept running on ads and player subscriptions! If you enjoy playing here please consider purchasing a subscription. The proceeds will be used to pay for server 
          hosting, maintain security certificates, and to build a better and even more fun pente.org.<br>
<br>

          <font color="<%= textColor2 %>"><b>All subscriptions will get you the following extra features:<br>
            - Change the color of your name!<br>
            - Upload a picture of yourself to your profile!<br>
            - Add a note to your profile!<br>
            - Get access to the database!<br>
            - Participate in more than 1 (King of the) Hill at a time!<br>
            - Play King of the Hill without limits!<br>
            - Request undo in turn-based games!<br>
            - Remove limits on followers!<br>
            - Broadcast alers to followers or friends!<br>
            And optionally, <br>
            - No more ads!<br>
          </b></font>
          <br>
          <br>
          <u><b>Note:</b></u> Your credit card statements will show a charge from ABSTRACTCRE or ABSTRACTCREATIONS.
          <br>
          <br>
          <u><b>Note:</b></u> The upgrade of your account should happen "near"-instantaneous after payment, if that does not happen within a few minutes, then <a href="http://www.pente.org/gameServer/newMessage.jsp?to=rainwolf">message</a> me and I will look into it asap.
          <br>
          <b>Update:</b> Lately Paypal's servers have been a bit slower than usual, resulting in your profile being automatically upgraded between 10 minutes to 8 hours after payment. If this happens, let me know and I will manually fix your account.
          <br>
          <br>

<table align="center" border="1" cellpadding="10" cellspacing="0" width="100%">
<tr>
  <td align="center" width="75%">
     Subscription Type 
  </td>
  <td align="left" colspan="2">
     Price 
  </td>
</tr>
<!-- <tr>
  <td colspan="3">
  Without ads. For desktop users <u>and</u> mobile players.
  </td>
</tr>
 --><tr>
  <td align="left">
      <u> 1 Month</u> of unlimited turn-based games and no advertisements, database access included: 
  </td>
  <td align="center">
     1,99 EUR 
  </td>
  <td align="center">
    <form action="https://www.paypal.com/cgi-bin/webscr" method="post" target="_top">
    <input type="hidden" name="cmd" value="_s-xclick">
    <input type="hidden" name="hosted_button_id" value="9URT663NKHU6J">
<%--
--%>
    <input type="hidden" name="custom" value="<%=gifter + ";" + giftee%>"/>
    <input type="image" src="https://www.paypalobjects.com/en_US/i/btn/btn_buynow_SM.gif" border="0" name="submit" alt="PayPal - The safer, easier way to pay online!">
    <img alt="" border="0" src="https://www.paypalobjects.com/en_US/i/scr/pixel.gif" width="1" height="1">
    </form>
  </td>
</tr>
<!-- <tr>
  <td align="left">
      <u> 1 Year</u> of unlimited turn-based games and no advertisements: 
  </td>
  <td align="center">
     12 EUR 
  </td>
  <td align="center">
    <form action="https://www.paypal.com/cgi-bin/webscr" method="post" target="_top">
    <input type="hidden" name="cmd" value="_s-xclick">
    <input type="hidden" name="hosted_button_id" value="55C84W5NWFGPE">
<%--
--%>
    <input type="hidden" name="custom" value="<%=gifter + ";" + giftee%>"/>
    <input type="image" src="https://www.paypalobjects.com/en_US/i/btn/btn_buynow_SM.gif" border="0" name="submit" alt="PayPal - The safer, easier way to pay online!">
    <img alt="" border="0" src="https://www.paypalobjects.com/en_US/i/scr/pixel.gif" width="1" height="1">
    </form>
  </td>
</tr>
 --><tr>
  <td align="left">
      <u> 1 Year</u> of unlimited turn-based games and no advertisements, including database access: 
  </td>
  <td align="center">
     5,99 EUR 
  </td>
  <td align="center">
    <form action="https://www.paypal.com/cgi-bin/webscr" method="post" target="_top">
    <input type="hidden" name="cmd" value="_s-xclick">
    <input type="hidden" name="hosted_button_id" value="4XYGQWN5FNEDN">
<%--
--%>
    <input type="hidden" name="custom" value="<%=gifter + ";" + giftee%>"/>
    <input type="image" src="https://www.paypalobjects.com/en_US/i/btn/btn_buynow_SM.gif" border="0" name="submit" alt="PayPal - The safer, easier way to pay online!">
    <img alt="" border="0" src="https://www.paypalobjects.com/en_US/i/scr/pixel.gif" width="1" height="1">
    </form>
  </td>
</tr>
<!-- <tr>
  <td align="left">
      <u> 1 Year</u> of unlimited turn-based games, including database access, but <b>with</b> advertisements: 
  </td>
  <td align="center">
     4,99 EUR 
  </td>
  <td align="center">
    <form action="https://www.paypal.com/cgi-bin/webscr" method="post" target="_top">
    <input type="hidden" name="cmd" value="_s-xclick">
    <input type="hidden" name="hosted_button_id" value="XSXJJYYGAJB7G">
    <input type="hidden" name="custom" value="<%=gifter + ";" + giftee%>"/>
    <input type="image" src="https://www.paypalobjects.com/en_US/i/btn/btn_buynow_SM.gif" border="0" name="submit" alt="PayPal - The safer, easier way to pay online!">
    <img alt="" border="0" src="https://www.paypalobjects.com/en_US/i/scr/pixel.gif" width="1" height="1">
    </form>
  </td>
</tr>
 --><tr>
  <td align="left">
      Vacation (days) deals (no subscription required):
      <br> These vacation days do <b><font color="red">not</font></b> roll over to <%=Calendar.getInstance().get(Calendar.YEAR) + 1%>.  
  </td>
  <td align="center" colspan="2">
<form action="https://www.paypal.com/cgi-bin/webscr" method="post" target="_ppptop">
<input type="hidden" name="cmd" value="_s-xclick">
<input type="hidden" name="hosted_button_id" value="D7DCU23JAW26Q">
<input type="hidden" name="on0" value="Vacation Deals">
 <input type="hidden" name="custom" value="<%=gifter + ";" + giftee%>"/>
<select name="os0">
  <option value="10 extra days">10 extra days 3,00 EUR</option>
  <option value="30 extra days">30 extra days 6,00 EUR</option>
  <option value="60 extra days">60 extra days 9,00 EUR</option>
</select>
<input type="hidden" name="currency_code" value="EUR">
<input type="image" src="https://www.paypalobjects.com/en_US/i/btn/btn_buynow_SM.gif" border="0" name="submit" alt="PayPal - The safer, easier way to pay online!">
<img alt="" border="0" src="https://www.paypalobjects.com/en_US/i/scr/pixel.gif" width="1" height="1">
</form>
  </td>
</tr>
<!-- <tr>
  <td colspan="3">
  For Mobile-only users. This applies to users of the iPhone app <u>only</u>, free account restrictions still apply on the website, but not in the app.<br> 
  If you mainly/only use the mobile app (iOS only currently) and don't mind the ads, then the following subscriptions come at a discount. <br>
  </td>
</tr>
<tr>
  <td align="left">
      <u> 1 Month</u> of unlimited turn-based games:  
  </td>
  <td align="center">
     1 EUR 
  </td>
  <td align="center">
    <form action="https://www.paypal.com/cgi-bin/webscr" method="post" target="_top">
    <input type="hidden" name="cmd" value="_s-xclick">
    <%--
    --%>
    <input type="hidden" name="custom" value="<%=gifter + ";" + giftee%>"/>
    <input type="hidden" name="hosted_button_id" value="QG7WMC8VA4V88">
    <input type="image" src="https://www.paypalobjects.com/en_US/i/btn/btn_buynow_SM.gif" border="0" name="submit" alt="PayPal - The safer, easier way to pay online!">
    <img alt="" border="0" src="https://www.paypalobjects.com/en_US/i/scr/pixel.gif" width="1" height="1">
    </form>
  </td>
</tr>
<tr>
  <td align="left">
      <u> 1 Year</u> of unlimited turn-based games: 
  </td>
  <td align="center">
     6 EUR 
  </td>
  <td align="center">
    <form action="https://www.paypal.com/cgi-bin/webscr" method="post" target="_top">
    <input type="hidden" name="cmd" value="_s-xclick">
    <input type="hidden" name="hosted_button_id" value="TVHV4XRQ9EHV2">
<%--
--%>
    <input type="hidden" name="custom" value="<%=gifter + ";" + giftee%>"/>
    <input type="image" src="https://www.paypalobjects.com/en_US/i/btn/btn_buynow_SM.gif" border="0" name="submit" alt="PayPal - The safer, easier way to pay online!">
    <img alt="" border="0" src="https://www.paypalobjects.com/en_US/i/scr/pixel.gif" width="1" height="1">
    </form>
  </td>
</tr>
 -->
 <%if ("rainwolf".equals(giftee) || "rainwolf".equals(gifter)) { %> 
<tr>
  <td align="center">
      <u> 1 month</u> test purchase:  
    (testing testing testing.)
  </td>
  <td align="center">
     8 EUR 
  </td>
  <td align="center">
<form action="https://www.sandbox.paypal.com/cgi-bin/webscr" method="post" target="_top">
<input type="hidden" name="cmd" value="_s-xclick">
<input type="hidden" name="hosted_button_id" value="7HQ4FUNAZYMQN">
    <input type="hidden" name="custom" value="<%=gifter + ";" + giftee%>"/>
<input type="image" src="https://www.sandbox.paypal.com/en_US/i/btn/btn_buynow_SM.gif" border="0" name="submit" alt="PayPal - The safer, easier way to pay online!">
<img alt="" border="0" src="https://www.sandbox.paypal.com/en_US/i/scr/pixel.gif" width="1" height="1">
</form>
  </td>
</tr>
<%}%>
</table>


<br>
<br>
<br>
<br>
<br>
<br>
<br>
<br>
<br>
<br>


<table border="0" cellpadding="0" cellspacing="2" width="100%">
  <tr> 
    <td valign="top">

<h3>  This section is kept for historical reasons.</h3>

  Pente.org used to be supported by ads and donations. Below is the list of people who voluntarily donated and supported pente.org over the years. Donating is still possible and will add you to the list below, but it is separate from the abovementioned subscriptions and benefits.
<br>
<br>
          You can make your donations using 
          <b><a href="javascript:document.paypal_form.submit()">paypal</a></b>, make sure to communicate your <font color="red">username</font> when you donate.<br>
          <b><font color="red">Note: </font></b> This donation will make your name show up in the list of donors below, but it is <b>not</b> a subscription fee. To become a subscriber and get  
          the subscriber benefits, use the buttons above.
          <br>
          <br>
             <a href="javascript:document.paypal_form.submit()"><img src="<%= request.getContextPath() %>/gameServer/images/paypal.gif" border="0"></a>
          <form action="https://www.paypal.com/cgi-bin/webscr" name="paypal_form" method="post">
            <input type="hidden" name="cmd" value="_xclick">
            <input type="hidden" name="business" value="walied.othman@gmail.com">
            <input type="hidden" name="item_name" value="Pente.org donation from <%= request.getAttribute("name") %>">
            <input type="hidden" name="amount" value="10">
            <input type="hidden" name="no_shipping" value="1">
            <input type="hidden" name="return" value="http://www.pente.org/gameServer/donations?command=thanks">
            <input type="hidden" name="cancel_return" value="http://www.pente.org/gameServer/donations">
          </form>

      
    </td>
    <td valign="top" align="right">
    </td>
  </tr>
  <tr> 
    <td colspan="2">&nbsp;</td>
  </tr>
  <tr> 
    <td colspan="2">
       <h3>Thanks to all the players who have donated so far!</h3>
    </td>
  </tr>
  <tr> 
    <td colspan="2">
      <table width="100%" cellspacing="2" cellpadding="0" border="0">
  
<%  
            int year = 0;
            int count = -1;
            Calendar calendar = Calendar.getInstance();
      Iterator donations = donationList.iterator();
      while (donations.hasNext()) {
          DSGDonationData d = (DSGDonationData) donations.next();
          count++;
          calendar.setTime(d.getDonationDate());
          int dYear = calendar.get(Calendar.YEAR);
          if (year != dYear) { 
              year = dYear;
              count = 0; %>
              </tr>
              <tr>
                <td colspan="5">
                  <br><font color="<%= textColor2 %>" face="Verdana, Arial, Helvetica, sans-serif" size="2"><b>
                    <%= dYear %>
                  </font></b><br>
                </td>
              </tr>
              <tr>
             <% }
                else if (count % 5 == 0) { %>
                    </tr>
                    <tr>
             <% } %>
                <td>
                  <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
                    <a href="/gameServer/profile?viewName=<%= d.getName() %>"><%= d.getName() %></a>
                  </font>
                </td>
         <% } %>

        </tr>
      </table>
      <br>
    </td>
  </tr>
</table>

<%  
} else { %>
  <h3>Error: you have to login first</h3>
  <%
}
%>
<%@ include file="end.jsp" %>
