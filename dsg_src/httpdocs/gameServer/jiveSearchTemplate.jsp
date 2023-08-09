<%@ taglib uri="webwork" prefix="ww" %>
<%@ taglib uri="jivetags" prefix="jive" %>

<table border="0" cellspacing="0" cellpadding="1" width="100%">
   <tr>
      <td bgcolor="black">
         <table border="0" cellspacing="1" cellpadding="5" width="100%">
            <tr>
               <td bgcolor="#8b0000">
                  <font face="Verdana, Arial, Helvetica, sans-serif" size="3" color="white">
                     <b>Search <a href="<%= request.getContextPath() %>/gameServer/forums"><font
                        color="white">Forums</font></a></b>
                  </font>
               </td>
            </tr>
            <tr>
               <td bgcolor="#dcdcdc">

                  <form action="<%= request.getContextPath() %>/gameServer/forums/search!execute.jspa">

                     <table cellpadding="3" cellspacing="0" border="0" width="100%">
                        <tr>
                           <td>
                              <input type="text" name="q" size="10" maxlength="150">
                           </td>
                           <td>
                              <input type="submit" value="Go">
                           </td>
                        </tr>
                        <tr>
                           <td colspan="2">
                              <select size="6" name="objID" style="width:130px" multiple>
                                 <option value="">
                                    <jive:i18n key="searchbox.choose_forums"/>

                                       <% for (Iterator it = forumFactory.getRootForumCategory().getRecursiveForums(); it.hasNext();) {
                               Forum forum = (Forum) it.next(); %>
                                 <option value="f<%= forum.getID() %>">&nbsp;&#149;&nbsp; <%= forum.getName() %>
                                       <% } %>
                              </select>
                           </td>
                        </tr>
                     </table>
                  </form>
               </td>
            </tr>
            <tr>
               <td colspan="2" bgcolor="#dcdcdc" align="center">
                  <%-- More search options --%>
                  <a href="<%= request.getContextPath() %>/gameServer/forums/search!default.jspa">Advanced Search</a>
               </td>
            </tr>
         </table>
      </td>
   </tr>
</table>