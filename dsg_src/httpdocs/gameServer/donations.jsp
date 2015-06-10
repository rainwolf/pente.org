<%@ page import="java.util.*" %>
<%@ page import="java.text.*" %>
<%@ page import="org.pente.gameServer.core.*" %>

<% List<DSGDonationData> donationList = (List<DSGDonationData>) request.getAttribute("donationList"); %>

<% pageContext.setAttribute("title", "Donations"); %>
<%@ include file="begin.jsp" %>

<table border="0" cellpadding="0" cellspacing="0" width="100%">
  <tr> 
    <td valign="top">
        <h3>Support Pente.org</h3>
          
          Pente.org is kept running almost entirely on ads and player donations!  If you have enjoyed playing here please consider helping out.<br>
<br>
<%--  There are a few ways you can help:
--%>
<%--            
          <ul>
            <li><a href="#paypal">Donate to Pente.org</a></li>
          </ul>
			<li><a href="http://www.winning-moves.com/products/deluxe_pente.htm?kbid=1005&img=donations">Buy Pente Deluxe from Winning Moves</a></li>
          <img src="http://www.myaffiliateprogram.com/u/wmoves/showban.asp?id=1005&img=donations" width="0" height="0" border="0">
--%>

          Anything helps!  100% of proceeds will be used to pay for server 
          hosting, maintain security certificates, and to build a better and even more fun pente.org.<br>
          <br>

          <font color="<%= textColor2 %>"><b>If you donate at least
          $10.00, you will get the following extra features:<br>
            - Change the color of your name in the game room!<br>
            - Upload a picture of yourself to your profile!<br>
            - Add a note to your profile!<br>
            - No more ads!<br>
          </b></font>
          <br>
      <a href="https://www.paypal.com/verified/pal=walied.othman%40gmail.com">
         <img border="0" align="right" src="images/verification_seal.gif"></a>

       <a name="paypal"><h3>Donate</h3></a>

          You can make your donations using 
          <b><a href="javascript:document.paypal_form.submit()">paypal</a></b>, make sure to communicate your <font color="red">username</font> when you donate.<br><br>
          <h3><font color="red">Notice:</font> Pente.org is transitioning to a subscription model, your donation will get you the abovementioned features for one year following your donation.</h3> <br>
          <center>
             <a href="javascript:document.paypal_form.submit()"><img src="<%= request.getContextPath() %>/gameServer/images/paypal.gif" border="0"></a></center>
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

<%@ include file="end.jsp" %>
