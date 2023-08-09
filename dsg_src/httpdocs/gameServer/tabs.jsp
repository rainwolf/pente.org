<%--  // This page will print out tabs that look something like this:
    //
    //   +-------+ +-------+ +-------+
    //   |       | |       | |       |
    //   |       +-----------------------------------------+
    //   +-------------------------------------------------+
    //
    // Below is a "tabs" variable which is where the name and link for each tab is stored.
    // Each item in the array is another array of 2 length - the display name of the
    // tab and its URL.
    // Also, a String variable "selectedTab" is assumed which should match the name
    // of the tab that is selected.
    //
    // This page makes good use of CSS to render the tabs - see style.jsp
--%>

<%@ taglib uri="webwork" prefix="ww" %>
<%@ taglib uri="jivetags" prefix="jive" %>

<% String[][] tabs = null;
   String[][][] subtabs = null;
%>

<% tabs = new String[][]{
   {"My Info", "/gameServer/myprofile"},
   {"Preferences", "/gameServer/myprofile/prefs"},
   {"Social", "/gameServer/social?social"},
   {"Subscriber Settings", "/gameServer/myprofile/donor"},
   {"Forum Settings", "/gameServer/forums/settings!tab.jspa"},
   {"Forum Watches", "/gameServer/forums/editwatches!default.jspa"}
};
   subtabs = new String[][][]{
      /* Viewing Preferences */
      new String[][]{{"Privacy Policy", "/gameServer/help/helpWindow.jsp?file=privacyPolicy"},
         {"My Info", "#myInfo"},
         {"View my profile as others see it", "/gameServer/profile?viewName=" + (String) request.getAttribute("name")},
         {"Delete my account", "/gameServer/deletePlayer"}},
      new String[][]{{"Game Room Size", "#gameRoomSize"},
         {"Page Refresh", "#refresh"},
         {"Email", "#email"},
         {"Turn-based", "#turnBased"},
         {"Ignored Players", "#ignore"}},
      new String[][]{{"Following", "#following"},
         {"Followers", "#followers"}},
      new String[][]{{"Name Color", "#nameColor"},
         {"Picture", "#picture"},
         {"Note", "#note"}},
      new String[][]{{"Viewing Preferences", "#viewing"},
         {"Watch Preferences", "#watch"}},

      new String[][]{{"Forums", "#forums"},
         {"Topics", "#topics"}}
   };
%>


<table class="jive-tabs" cellpadding="0" cellspacing="0" border="0">
   <tr>
      <td class="jive-tab-spacer" width="1%"><img src="/gameServer/forums/images/blank.gif" width="10" height="1"
                                                  border="0"></td>

      <% int selectedTabIndex = 0;
         for (int i = 0; i < tabs.length; i++) {
            boolean selected = selectedTab.equals(tabs[i][0]);
            if (selected) {
               selectedTabIndex = i;
            }
      %>
      <td class="jive-<%= (selected?"selected-":"") %>tab" width="1%" nowrap>
         <a href="<%= tabs[i][1] %>"><%= tabs[i][0] %>
         </a>
      </td>
      <td class="jive-tab-spacer" width="1%"><img src="/gameServer/forums/images/blank.gif" width="10" height="1"
                                                  border="0"></td>

      <% } %>
      <td class="jive-tab-spring" width="<%= (99-(tabs.length*2)) %>%">&nbsp;</td>
   </tr>
   <tr>
      <td class="jive-tab-bar" colspan="99">
         <table cellpadding="6" cellspacing="0" border="0">
            <tr>
               <% String[][] subtab = subtabs[selectedTabIndex];
                  for (int i = 0; i < subtab.length; i++) {
               %>
               <td style="font-weight:normal;">
                  <img src="/gameServer/forums/images/down.gif" width="8" height="8" border="0">
                  <a href="<%= subtab[i][1] %>" style="font-family:verdana;font-size:0.85em;color:#666;"
                  ><%= subtab[i][0] %>
                  </a>
               </td>
               <% } %>
            </tr>
         </table>
      </td>
   </tr>
</table>
<%-- drop shadow --%>
<table cellpadding="0" cellspacing="0" border="0" width="100%" bgcolor="#aaaaaa">
   <tr>
      <td><img src="/gameServer/forums/images/blank.gif" width="1" height="1" border="0"></td>
   </tr>
</table>
<table cellpadding="0" cellspacing="0" border="0" width="100%" bgcolor="#dddddd">
   <tr>
      <td><img src="/gameServer/forums/images/blank.gif" width="1" height="1" border="0"></td>
   </tr>
</table>
<table cellpadding="0" cellspacing="0" border="0" width="100%" bgcolor="#eeeeee">
   <tr>
      <td><img src="/gameServer/forums/images/blank.gif" width="1" height="1" border="0"></td>
   </tr>
</table>
<%-- drop shadow --%>