<%@ page import="java.util.*,
                 org.pente.gameServer.core.*, 
                 com.jivesoftware.forum.*" %>

<%@ page import="com.jivesoftware.forum.action.SettingsAction,
                 java.util.Locale"%>
                 
<%
DSGPlayerData dsgPlayerData = (DSGPlayerData) request.getAttribute("dsgPlayerData");
if (dsgPlayerData == null) {
	throw new Exception("Illegal access attempted");
}

String changeProfileError = (String) request.getAttribute("changeProfileError");
String changeProfileSuccess = (String) request.getAttribute("changeProfileSuccess");
%>

<% pageContext.setAttribute("title", "My Profile"); %>
<% pageContext.setAttribute("current", "My Profile"); %>
<%@ include file="begin.jsp" %>
<link rel="stylesheet" type="text/css" href="<%= request.getContextPath() %>/gameServer/forums/style.jsp" />


<%  String selectedTab = "Donor Settings"; %>
<%@ include file="tabs.jsp" %>




<form enctype="multipart/form-data"
      name="change_profile_form"
      method="post"
      action="/gameServer/myprofile/donor">


<table width="100%" border="0" colspacing="0" colpadding="0">


<tr>
 <td>
  <a name="myInfo"><h3>Donor Settings</h3></a>
 </td>
</tr>


<% if (changeProfileError != null) { %>

<tr>
 <td>
  <b><font face="Verdana, Arial, Helvetica, sans-serif" size="2" color="<%= textColor2 %>">
   Changing profile failed: <%= changeProfileError %>
  </b></font>
 </td>
</tr>
   
<% } else if (changeProfileSuccess != null) { %>

<tr>
 <td>
  <font face="Verdana, Arial, Helvetica, sans-serif" size="2" color="<%= textColor2 %>">
   <b><%= changeProfileSuccess %></b>
  </font>
 </td>
</tr>

<%
   }
%>


<% String disabled = dsgPlayerData.hasPlayerDonated() ? "" : "disabled"; %>
<% if (!dsgPlayerData.hasPlayerDonated()) { %>
<tr>
  <td colspan="2">
    <div style="font-family:Verdana, Arial, Helvetica, sans-serif;
     background:#fffbcc;
     border:1px solid #e6db55;
     padding:5px;
     font-weight:bold;
     width:90%;">
    If you support pente.org by donating you can customize your account:<br>
    - You can change the color of your name in the live game room<br>
    - You can upload a picture for your account<br>
    - You can specify a note with your profile for other people to see<br>
    - You will not see any ads<br>
    <br>
    <b><a href="/gameServer/donations">
      Donate now to Pente.org!</a></b>
    </div>
   </td>
</tr>
<% } %>
<tr><td>&nbsp;</td></tr>
<tr>
  <td>
    <a name="nameColor"><h4>Name Color</h4></a>
    <table border="0" colspacing="1" colpadding="1">
     <tr>
      <td width="160" valign="top">
       <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
        Name Color:
       </font>
      </td>
      <td>
       <% String nameColor = "";
          java.awt.Color c = dsgPlayerData.getNameColor();
          if (c != null) {
              String r = Integer.toHexString(c.getRed());
              if (r.equals("0")) r = "00";
              String g = Integer.toHexString(c.getGreen());
              if (g.equals("0")) g = "00";
              String b = Integer.toHexString(c.getBlue());
              if (b.equals("0")) b = "00";
              
              nameColor = (r + g + b).toUpperCase();
          }
       %>
       <input type="text" size="6" maxlength="6" name="changeNameColor" value="<%= nameColor %>" <%= disabled %>>
       <br>
	   <script language="javascript">
	       function pick(val) {
	           document.change_profile_form.changeNameColor.value=val;
	       }
       </script>

	   <% if (dsgPlayerData.hasPlayerDonated()) { %>
         <%@ include file="colors.jsp" %>
	   <% } %>
      </td>
     </tr>
     <tr><td><a name="picture"><h4>Picture</h4></a></td></tr>
     <tr>
       <td valign="top">
         <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
           Current Picture:
         </font>
       </td>
       <td>
         <% if (dsgPlayerData.hasAvatar()) { %>
             <img src="<%= request.getContextPath() %>/gameServer/avatar?name=<%= dsgPlayerData.getName() %>">
             <br>
         <% }
            else { %>
              No picture uploaded.
         <% } %>
       </td>
     </tr>
     
     <% if (dsgPlayerData.hasAvatar()) { %>
     <tr>
       <td>
         <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
           Remove Picture:
         </font>
       </td>
       <td>
         <input type="checkbox" name="removeAvatar" value="yes">
       </td>
     </tr>
     <% } %>
     
     <% if (dsgPlayerData.hasPlayerDonated()) { %>
     <tr>
       <td>
         <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
           Upload New Picture:
         </font>
       </td>
       <td>
         <input type="file" name="avatar">
       </td>
     </tr>
     <% } %>
     
     <tr><td><a name="note"><h4>Note</h4></a></td></tr>
     <tr>
       <td>
         <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
           Note:
         </font>
       </td>
       <td>
         <% String note = dsgPlayerData.getNote() != null ?
                dsgPlayerData.getNote() : ""; %> 
         <input type="text"
                name="note"
                value="<%= note %>" 
                size="50"
                maxlength="100"
                <%= disabled %>>
       </td>
     </tr>
     
     <% if (dsgPlayerData.getName().equals("rainwolf")) { %>
     <tr><td><a name="bio"><h4>Bio </h4></a></td></tr>
     <tr>
       <td>
         <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
           Bio: <%=dsgPlayerData.getName()%>
         </font>
       </td>
       <td>
<!--
         <% String bio = dsgPlayerData.getNote() != null ?
                dsgPlayerData.getNote() : ""; %> 
-->
        <textarea name="bio" wrap="virtual" cols="58" rows="15" maxlength="2048"></textarea>
       </td>
     </tr>
     <% } %>
     
     <tr>
       <td>&nbsp;</td>
       <td>
         <font face="Verdana, Arial, Helvetica, sans-serif" size="2">
         <input type="submit" value="Save changes" <%= disabled %>>
         </font>
       </td>
     </tr>
     <tr>
    </table>
  </td>
</tr> 

</table>
</form>

<%@ include file="end.jsp" %>