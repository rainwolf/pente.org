<%@ page import="java.util.Calendar" %>

<%! private static int year;

   static {
      Calendar now = Calendar.getInstance();
      year = now.get(Calendar.YEAR);
   }
%>

<tr>
   <td colspan="5">
      <table border="0" cellspacing="0" cellpadding="0" width="100%">
         <tr>
            <td bgcolor="black">
               <table border="0" cellspacing="0" cellpadding="0" width="100%">
                  <tr bgcolor="<%= bgColor1 %>">
                     <td width="150">&nbsp;</td>
                     <td align="center">
                        <font face="Verdana, Arial, Helvetica, sans-serif" size="-2" color="<%= textColor1 %>">
                           Copyright &copy; 1999-<%= year %> Pente.org. All rights reserved.
                        </font>
                     </td>
                     <td width="150">&nbsp;</td>
                  </tr>
               </table>
            </td>
         </tr>
      </table>
   </td>
</tr>