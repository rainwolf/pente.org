<%! private static final String images[] = new String[]{
   "shirt1.jpg", "shirt2.jpg", "shirt3.jpg", "shirt4.jpg"};
   private static int dsgStoreCounter = 0;
%>
<% String image = images[dsgStoreCounter++ % images.length]; %>
<% adWidth = 550; %>

<td width="<%= (width - 500) / 2 %>">&nbsp;</td>
<td align="center" valign="middle">
   <table border="1" cellpadding="0" cellspacing="0"
          width="<%= adWidth %>" bgcolor="white" bordercolor="gray">

      <tr>
         <td>
            <table border="0" cellpadding="2" cellspacing="0">
               <tr>
                  <td colspan="3" align="center">
                     <font color="black" face="Courier">ADVERTISEMENT</font><br><br>
                  </td>
               </tr>
               <tr>
                  <td colspan="3">
                     <font size="3" color="black" face="Verdana, Arial, Helvetica, sans-serif">
                        Do you live and breath Pente? Do you <b>LOVE</b> Pente.org?<br>
                        Then you need some <a target="_blank" href="http://www.cafepress.com/dweebos">
                        Official Pente.org Merchandise</a>. Get Pente.org shirts, hats
                        and mugs today! All proceeds go towards site improvements and
                        server costs.<br>
                        <br>
                     </font>
                  </td>
               </tr>
               <tr>
                  <td>
                     <a target="_blank" href="http://www.cafepress.com/dweebos">
                        <img src="/gameServer/images/store/<%= image %>" width="150" height="150" border="0"></a>
                  </td>
                  <td>
                     <a target="_blank" href="http://www.cafepress.com/dweebos">
                        <img src="/gameServer/images/store/hat.jpg" width="150" height="150" border="0"></a>
                  </td>
                  <td>
                     <a target="_blank" href="http://www.cafepress.com/dweebos">
                        <img src="/gameServer/images/store/mug.jpg" width="150" height="150" border="0"></a>
                  </td>
               </tr>
               <tr>
                  <td colspan="3">
                     <font size="3" color="black" face="Verdana, Arial, Helvetica, sans-serif">
                        <b><font color="red">Special!</font></b> - Buy Pente.org products and get the
                        following extras:
                        <ul>
                           <li>Colored name in the game room</li>
                           <li>Picture and note in your profile</li>
                           <li>Access to play turn-based games</li>
                        </ul>
                     </font>
                  </td>
            </table>
         </td>
      </tr>
   </table>
</td>