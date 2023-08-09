<%
   /**
    *	$RCSfile: colorPicker.jsp,v $
    *	$Revision: 1.1 $
    *	$Date: 2002/08/16 06:52:22 $
    */
%>

<%@ page import="java.util.*,
                 com.jivesoftware.forum.*,
                 com.jivesoftware.forum.util.*,
                 com.jivesoftware.util.ParamUtils" %>

<%! // global vars, methods
   static final String[] hex = {"00", "33", "66", "99", "cc", "ff"};
   static final String[] bigHex = {"000000", "111111", "222222", "333333", "444444", "555555", "666666", "777777", "888888", "999999", "aaaaaa", "bbbbbb", "cccccc", "dddddd", "eeeeee", "ffffff"};
%>

<%@ include file="global.jsp" %>

<% // Get parameters
   String defaultColor = "#" + ParamUtils.getParameter(request, "defaultColor");
   String element = ParamUtils.getParameter(request, "element");
%>

<%@ include file="header.jsp" %>

<p>

   <script language="JavaScript" type="text/javascript">
      <!--
      var defaultColor = '<%= defaultColor %>';
      var choice = false;
      var openerForm = opener.document.skinForm;
      var openerEl = opener.document.skinForm.<%= element %>;

      function colorIn(color) {
         if (!choice) {
            openerEl.value = color;
            document.f.colorVal.value = color;
         }
      }

      function accept() {
         choice = true;
      }

      function choiceController() {
         if (document.f.formAction.value == "cancel") {
            cancel();
         } else {
            ok();
         }
      }

      function ok() {
         openerEl.value = document.f.colorVal.value;
         openerForm.formAction.value = 'save';
         opener.document.skinForm.submit();
         window.close();
      }

      function cancel() {
         openerEl.value = defaultColor;
         window.close();
      }

      //-->
   </script>

      <%  // Title of this page and breadcrumbs
    String title = "Color Picker";
    String[][] breadcrumbs = {
        {"Close", "javascript:cancel();"}
    };
%>
   <%@ include file="title.jsp" %>

   <font size="-1">Click to choose a color.</font>

<p>

<form name="f" onsubmit="choiceController();">
   <input type="hidden" name="formAction" value="">
   <table cellpadding="0" cellspacing="1" border="1" align="center">
      <% for (int i = 0; i < hex.length; i++) { %>
      <tr>
         <% for (int j = 0; j < hex.length; j++) { %>

         <% for (int k = 0; k < hex.length; k++) { %>
         <td bgcolor="#<%= hex[i] %><%= hex[j] %><%= hex[k] %>"
         ><a href="#" onmouseover="colorIn('#<%= hex[i] %>
            <%= hex[j] %>
            <%= hex[k] %>');" onclick="accept();return false;"
         ><img src="images/blank.gif" width="10" height="15" alt="#<%= hex[i] %><%= hex[j] %><%= hex[k] %>" border="0"></a>
         </td>
         <% } %>

         <% } %>
      </tr>
      <% } %>
      <tr>
         <td colspan="<%= hex.length * hex.length %>" align="center">
            <table cellpadding="0" cellspacing="1" border="1" align="center" width="100%">
               <tr>
                  <% for (int i = 0; i < bigHex.length; i++) { %>
                  <td bgcolor="#<%= bigHex[i] %>"
                  ><a href="#" onmouseover="colorIn('#<%= bigHex[i] %>');" onclick="accept();return false;"
                  ><img src="images/blank.gif" width="12" height="15" alt="#<%= bigHex[i] %>" border="0"></a></td>
                  <% } %>
               </tr>
            </table>
         </td>
      </tr>
      <tr>
         <td colspan="<%= hex.length * hex.length %>">
            <table width="100%">
               <tr>
                  <td><input type="text" size="10" name="colorVal"></td>
                  <td align="right">
                     <font size="-1">
                        <input type="submit" value="OK" name="ok" onclick="this.form.formAction.value='ok';">
                        <input type="submit" value="Cancel" name="cancel"
                               onclick="this.form.formAction.value='cancel';">
                     </font>
                  </td>
               </tr>
            </table>
         </td>
      </tr>
   </table>
</form>

<%@ include file="footer.jsp" %>
