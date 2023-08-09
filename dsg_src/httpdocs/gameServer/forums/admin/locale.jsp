<%
   /**
    *	$RCSfile: locale.jsp,v $
    *	$Revision: 1.4 $
    *	$Date: 2002/11/22 01:52:25 $
    */
%>

<%@ page import="java.util.*,
                 java.text.*,
                 com.jivesoftware.util.*,
                 com.jivesoftware.forum.*,
                 com.jivesoftware.forum.gateway.*,
                 com.jivesoftware.forum.util.*"
%>

<%@ include file="global.jsp" %>

<%! // Global vars, methods, etc

   static final String[][] timeZones = LocaleUtils.getTimeZoneList();

   static final String DEFAULT_CHARSET = "ISO-8859-1";
%>

<% // Permission check
   if (!isSystemAdmin) {
      throw new UnauthorizedException("You don't have admin privileges to perform this operation.");
   }

   Locale[] LOCALES = Locale.getAvailableLocales();
   Arrays.sort(LOCALES, new Comparator() {
      public int compare(Object o1, Object o2) {
         Locale loc1 = (Locale) o1;
         Locale loc2 = (Locale) o2;
         return loc1.getDisplayName().compareTo(loc2.getDisplayName());
      }
   });

   // get parameters
   String localeCode = ParamUtils.getParameter(request, "localeCode");
   String timeZoneID = ParamUtils.getParameter(request, "timeZoneID");
   String charsetChoice = ParamUtils.getParameter(request, "charsetChoice");
   String charset = ParamUtils.getParameter(request, "charset");
   boolean save = ParamUtils.getBooleanParameter(request, "save");

   // save the locale if requested
   if (save) {
      // parse the incoming locale code
      String language = null;
      String country = null;
      String variant = null;
      if (localeCode != null) {
         StringTokenizer tokenizer = new StringTokenizer(localeCode, "_");
         if (tokenizer.hasMoreTokens()) {
            language = tokenizer.nextToken();
            if (tokenizer.hasMoreTokens()) {
               country = tokenizer.nextToken();
               if (tokenizer.hasMoreTokens()) {
                  variant = tokenizer.nextToken();
               }
            }
         }
      }
      Locale newLocale = new Locale(
         language,
         ((country != null) ? country : ""),
         ((variant != null) ? variant : "")
      );
      // finally, set the new jive locale
      JiveGlobals.setLocale(newLocale);

      // Set the timezeone
      try {
         TimeZone tz = TimeZone.getTimeZone(timeZoneID);
         JiveGlobals.setTimeZone(tz);
      } catch (Exception e) {
      }

      // Set the character encoding
      if ("default".equals(charsetChoice)) {
         JiveGlobals.setCharacterEncoding("ISO-8859-1");
      } else if ("unicode".equals(charsetChoice)) {
         JiveGlobals.setCharacterEncoding("UTF-8");
      } else if ("userspef".equals(charsetChoice) && charset != null) {
         JiveGlobals.setCharacterEncoding(charset);
      }

      // we're done so redirect back to this page
      response.sendRedirect("locale.jsp");
      return;
   }

   // Get Jive's global locale
   Locale locale = JiveGlobals.getLocale();

   // Get Jive's global time zone
   TimeZone timeZone = JiveGlobals.getTimeZone();

   // Charset vars
   charset = JiveGlobals.getCharacterEncoding();
   boolean isDefaultCharset = DEFAULT_CHARSET.equals(charset);
   boolean isUnicodeCharset = "UTF-8".equals(charset);
   boolean isUserSpefCharset = !isDefaultCharset && !isUnicodeCharset;

   // Current date
   Date date = new Date();

   // Set the locale in the response object
   response.setLocale(locale);
%>

<%@ include file="header.jsp" %>

<p>

      <%  // Title of this page and breadcrumbs
    String title = "Locale Settings";
    String[][] breadcrumbs = {
        {"Main", "main.jsp"},
        {title, "locale.jsp"}
    };
%>
   <%@ include file="title.jsp" %>

   <font size="-1">
      Edit the global locale, time zone and character encoding below.
      These settings control the way dates, times and text are formatted.
   </font>

<p>

   <font size="-1"><b>Current Locale Settings</b></font>
<ul>
   <font size="-1">
      <table cellpadding="2" cellspacing="0" border="0">
         <tr>
            <td><font size="-1">Current Locale:</font></td>
            <td><font size="-1"><b><%= locale.getDisplayName() %>
            </b></font></td>
         </tr>
         <tr>
            <td><font size="-1">Sample Date:</font></td>
            <td><font size="-1"><b><%= JiveGlobals.formatDate(date) %>
            </b></font></td>
         </tr>
         <tr>
            <td><font size="-1">Sample Date and Time:</font></td>
            <td><font size="-1"><b><%= JiveGlobals.formatDateTime(date) %>
            </b></font></td>
         </tr>
         <tr>
            <td><font size="-1">Character Encoding:</font></td>
            <td><font size="-1"><b><%= JiveGlobals.getCharacterEncoding() %>
            </b></font></td>
         </tr>
      </table>
   </font>
</ul>

<p>

   <font size="-1"><b>Change Locale Settings</b></font>
<ul>
   <form action="locale.jsp">
      <input type="hidden" name="save" value="true">
      <table cellpadding="3" cellspacing="1" border="0">
         <tr>
            <td><font size="-1">Locale:</font></td>
            <td>
               <a href="#" onclick="helpwin('locale','locale');return false;"
                  title="Click for help"
               ><img src="images/help-16x16.gif" width="16" height="16" border="0" hspace="8"></a>
            </td>
            <td>
               <select size="1" name="localeCode">
                  <% for (int i = 0; i < LOCALES.length; i++) {
                     String selected = "";
                     if (locale.equals(LOCALES[i])) {
                        selected = " selected";
                     }
                     String countryCode = LOCALES[i].getLanguage();
                     boolean localeSupported = false;
                     if ("en".equals(countryCode)) {
                        localeSupported = true;
                     }
                  %>
                  <option value="<%= LOCALES[i].toString() %>"<%= selected %>
                     <%= localeSupported?" style=\"background-color:#dddddd;\"":"" %>><%= LOCALES[i].getDisplayName() %>
                        <%  } %>
               </select>
            </td>
         </tr>
         <tr>
            <td><font size="-1">Time Zone:</font></td>
            <td>
               <a href="#" onclick="helpwin('locale','time_zone');return false;"
                  title="Click for help"
               ><img src="images/help-16x16.gif" width="16" height="16" border="0" hspace="8"></a>
            </td>
            <td>
               <select size="1" name="timeZoneID">
                  <% for (int i = 0; i < timeZones.length; i++) {
                     String selected = "";
                     if (timeZone.getID().equals(timeZones[i][0].trim())) {
                        selected = " selected";
                     }
                  %>
                  <option value="<%= timeZones[i][0] %>"<%= selected %>><%= timeZones[i][1] %>
                        <%  } %>
               </select>
            </td>
         </tr>
         <tr>
            <td valign="top"><font size="-1">Character Set:</font></td>
            <td valign="top">
               <a href="#" onclick="helpwin('locale','charset');return false;"
                  title="Click for help"
               ><img src="images/help-16x16.gif" width="16" height="16" border="0" hspace="8"></a>
            </td>
            <td>
               <table cellpadding="2" cellspacing="0" border="0">
                  <tr>
                     <td><input type="radio" name="charsetChoice" value="default" id="rb01"
                        <%= isDefaultCharset?" checked":"" %>>
                     </td>
                     <td>
                        <font size="-1">
                           <label for="rb01">English and other Western languages (ISO-8859-1)</label>
                        </font>
                     </td>
                  </tr>
                  <tr>
                     <td><input type="radio" name="charsetChoice" value="unicode" id="rb02"
                        <%= isUnicodeCharset?" checked":"" %>>
                     </td>
                     <td>
                        <font size="-1"><label for="rb02">Unicode (UTF-8)</label></font>
                     </td>
                  </tr>
                  <tr>
                     <td><input type="radio" name="charsetChoice" value="userspef" id="rb03"
                        <%= isUserSpefCharset?" checked":"" %>>
                     </td>
                     <td>
                        <font size="-1"><label for="rb03">User Specified:</label></font>
                        <input type="text" name="charset" size="20" maxlength="100"
                               value="<%= isUserSpefCharset?charset:"" %>"
                               onfocus="this.form.charsetChoice[2].checked=true;">
                     </td>
                  </tr>
               </table>
            </td>
         </tr>
         <tr>
            <td colspan="3">
               <input type="submit" value="Save Settings">
            </td>
         </tr>
      </table>
   </form>
</ul>

<p>

   <%@ include file="footer.jsp" %>
