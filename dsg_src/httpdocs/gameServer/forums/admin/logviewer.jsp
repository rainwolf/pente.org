<%
   /**
    *	$RCSfile: logviewer.jsp,v $
    *	$Revision: 1.7.2.1 $
    *	$Date: 2003/03/04 22:22:51 $
    */
%>

<%@ page import="java.io.*,
                 com.jivesoftware.util.*,
                 com.jivesoftware.forum.util.SkinUtils,
                 java.text.*,
                 com.jivesoftware.base.log.Logger"
         errorPage="error.jsp"
%>

<%@ include file="global.jsp" %>

<%!
   static final String ERROR = "error";
   static final String INFO = "info";
   static final String WARN = "warn";
   static final String DEBUG = "debug";
   static final String DEFAULT = ERROR;

   static final String ASCENDING = "asc";
   static final String DESCENDING = "desc";

   static final String[] LINES = {"50", "100", "250", "500"};

   static final String[] REFRESHES = {"None", "10", "30", "60", "90"};

   static final SimpleDateFormat formatter = new SimpleDateFormat("yyyy.MM.dd kk:mm");

   private static final String parseDate(HttpServletRequest request, User pageUser, String input) {
      if (input == null || "".equals(input)) {
         return input;
      }
      if (input.length() < 16) {
         return input;
      }
      String d = input.substring(0, 16);
      // try to parse it
      try {
         Date date = formatter.parse(d);
         StringBuffer buf = new StringBuffer(input.length());
         buf.append("<span class=\"date\" title=\"").append(SkinUtils.dateToText(request, pageUser, date)).append("\">");
         buf.append(d).append("</span>");
         buf.append(input.substring(16, input.length()));
         return buf.toString();
      } catch (ParseException pe) {
         return input;
      }
   }

   private static final String hilite(HttpServletRequest request, User pageUser, String input) {
      if (input == null || "".equals(input)) {
         return input;
      }
      if (input.indexOf("com.jivesoftware.") > -1) {
         StringBuffer buf = new StringBuffer();
         buf.append("<span class=\"hilite\">").append(input).append("</span>");
         return buf.toString();
      }
      return input;
   }

   private static HashMap parseCookie(Cookie cookie) {
      if (cookie == null || cookie.getValue() == null) {
         HashMap empty = new HashMap();
         return empty;
      }
      StringTokenizer tokenizer = new StringTokenizer(cookie.getValue(), "&");
      HashMap valueMap = new HashMap();
      while (tokenizer.hasMoreTokens()) {
         String tok = tokenizer.nextToken();
         int pos = tok.indexOf("=");
         String name = tok.substring(0, pos);
         String value = tok.substring(pos + 1, tok.length());
         valueMap.put(name, value);
      }
      return valueMap;
   }

   private static void saveCookie(HttpServletResponse response, HashMap cookie) {
      StringBuffer buf = new StringBuffer();
      for (Iterator iter = cookie.keySet().iterator(); iter.hasNext(); ) {
         String name = (String) iter.next();
         String value = (String) cookie.get(name);
         buf.append(name).append("=").append(value);
         if (iter.hasNext()) {
            buf.append("&");
         }
      }
      Cookie newCookie = new Cookie("jiveforums.admin.logviewer", buf.toString());
      newCookie.setPath("/");
      newCookie.setMaxAge(60 * 60 * 24 * 30); // one month
      response.addCookie(newCookie);
   }

   private static HashMap getLogUpdate(HttpServletRequest request, HttpServletResponse response,
                                       File logDir) {
      // Get the cookie associated with the log files
      HashMap cookie = parseCookie(CookieUtils.getCookie(request, "jiveforums.admin.logviewer"));
      String[] logs = {"error", "info", "warn", "debug"};
      HashMap newCookie = new HashMap();
      HashMap updates = new HashMap();
      for (int i = 0; i < logs.length; i++) {
         // Check for the value in the cookie:
         String key = logs[i] + ".size";
         long savedSize = 0L;
         if (cookie.containsKey(key)) {
            try {
               savedSize = Long.parseLong((String) cookie.get(key));
            } catch (NumberFormatException nfe) {
            }
         }
         // Update the size in the Map:
         File logFile = new File(logDir, "jive." + logs[i] + ".log");
         long currentSize = logFile.length();
         newCookie.put(key, "" + currentSize);
         if (currentSize != savedSize) {
            updates.put(logs[i], "true");
         }
      }
      saveCookie(response, newCookie);
      return updates;
   }
%>

<% // Permission check
   if (!isSystemAdmin) {
      throw new UnauthorizedException("You don't have admin privileges to perform this operation.");
   }

   // Get parameters
   String log = ParamUtils.getParameter(request, "log");
   String numLinesParam = ParamUtils.getParameter(request, "lines");
   int numLines = ParamUtils.getIntParameter(request, "lines", 50);
   int refresh = ParamUtils.getIntParameter(request, "refresh", 10);
   String refreshParam = ParamUtils.getParameter(request, "refresh");
   boolean save = ParamUtils.getBooleanParameter(request, "save");
   String mode = ParamUtils.getParameter(request, "mode");
   boolean debugEnabled = ParamUtils.getBooleanParameter(request, "debugEnabled");
   boolean wasDebugEnabled = ParamUtils.getBooleanParameter(request, "wasDebugEnabled");
   boolean debugAlert = ParamUtils.getBooleanParameter(request, "debugAlert");

   // Enable/disable debugging
   if (request.getParameter("wasDebugEnabled") != null && wasDebugEnabled != debugEnabled) {
      JiveGlobals.setJiveProperty("log.debug.enabled", String.valueOf(debugEnabled));
      response.sendRedirect("logviewer.jsp?log=debug&debugAlert=true");
      return;
   }

   debugEnabled = "true".equals(JiveGlobals.getJiveProperty("log.debug.enabled"));

   // Set defaults
   if (log == null) {
      log = DEFAULT;
   }
   if (mode == null) {
      mode = ASCENDING;
   }
   if (numLinesParam == null) {
      numLinesParam = "50";
   }

   // Other vars
   File logDir = new File(JiveGlobals.getJiveHome(), "logs");
   String filename = "jive." + log + ".log";
   File logFile = new File(logDir, filename);
   boolean tooBig = (logFile.length() / (1024)) > 250;

   // Determine if any of the log files contents have been updated:
   HashMap newlogs = getLogUpdate(request, response, logDir);

   BufferedReader in = new BufferedReader(new FileReader(logFile));
   String line = null;
   int totalNumLines = 0;
   while ((line = in.readLine()) != null) {
      totalNumLines++;
   }
   in.close();
   // adjust the 'numLines' var to match totalNumLines if 'all' was passed in:
   if ("All".equals(numLinesParam)) {
      numLines = totalNumLines;
   }
   String[] lines = new String[numLines];
   in = new BufferedReader(new FileReader(logFile));
   // skip lines
   int start = totalNumLines - numLines;
   if (start < 0) {
      start = 0;
   }
   for (int i = 0; i < start; i++) {
      in.readLine();
   }
   int i = 0;
   if (ASCENDING.equals(mode)) {
      while ((line = in.readLine()) != null && i < numLines) {
         line = parseDate(request, pageUser, line);
         line = hilite(request, pageUser, line);
         lines[i] = line;
         i++;
      }
   } else {
      int end = lines.length - 1;
      while ((line = in.readLine()) != null && i < numLines) {
         line = parseDate(request, pageUser, line);
         line = hilite(request, pageUser, line);
         lines[end - i] = line;
         i++;
      }
   }
   numLines = start + i;
%>

<%@ include file="header.jsp" %>

<% if (refreshParam != null && !"None".equals(refreshParam)) { %>

<meta http-equiv="refresh" content="<%= refresh %>">

<% } %>

<% if (debugAlert) { %>

<script language="JavaScript" type="text/javascript">
   alert('Your change to the debug logging will go into affect after you restart your appserver.');
</script>

<% } %>

<% // Title of this page and breadcrumbs
   String title = "Log Viewer";
   String[][] breadcrumbs = {
      {"Main", "main.jsp"},
      {title, "logviewer.jsp?log=" + log}
   };
%>
<%@ include file="title.jsp" %>

<style type="text/css">
    .log TABLE {
        border: 1px #ccc solid;
    }

    .log TH {
        font-family: verdana, arial;
        font-weight: bold;
        font-size: 0.7em;
    }

    .log TR TH {
        background-color: #ddd;
        border-bottom: 1px #ccc solid;
        padding-left: 2px;
        padding-right: 2px;
        text-align: left;
    }

    .log .head-num {
        border-right: 1px #ccc solid;
    }

    .log TD {
        font-family: courier new;
        font-size: 0.75em;
        background-color: #ffe;
    }

    .log .num {
        width: 1%;
        background-color: #eee !important;
        border-right: 1px #ccc solid;
        padding-left: 2px;
        padding-right: 2px;
    }

    .log .line {
        padding-left: 10px;
    }

    .container {
        border-width: 0px 1px 1px 1px;
        border-color: #ccc;
        border-style: solid;
    }

    .info TD {
        font-family: verdana, arial;
        font-size: 0.7em;
    }

    SELECT {
        font-family: verdana, arial;
        font-size: 0.8em;
    }

    .info .label {
        padding-right: 6px;
    }

    .date {
        color: #00f;
        border-width: 0px 0px 1px 0px;
        border-style: dotted;
        border-color: #00f;
    }

    .new {
        font-family: courier new;
        font-weight: bold;
        color: #600;
    }

    .hilite {
        color: #900;
    }
</style>

<form action="logviewer.jsp">

   <input type="hidden" name="log" value="<%= log %>">

   <table class="jive-tabs" cellpadding="0" cellspacing="0" border="0">
      <tr>
         <td class="jive-tab-spacer" width="1%"><img src="images/blank.gif" width="5" height="1" border="0"></td>
         <td class="jive-<%= (("error".equals(log))?"selected-":"") %>tab" width="1%" nowrap>
            <a href="logviewer.jsp?log=error"
            >Error</a>
            <span class="new">
        <%= ((newlogs.containsKey("error")) ? "*" : "") %>
        </span>
         </td>
         <td class="jive-tab-spacer" width="1%"><img src="images/blank.gif" width="5" height="1" border="0"></td>
         <td class="jive-<%= (("info".equals(log))?"selected-":"") %>tab" width="1%" nowrap>
            <a href="logviewer.jsp?log=info"
            >Info</a>
            <span class="new">
        <%= ((newlogs.containsKey("info")) ? "*" : "") %>
        </span>
         </td>
         <td class="jive-tab-spacer" width="1%"><img src="images/blank.gif" width="5" height="1" border="0"></td>
         <td class="jive-<%= (("warn".equals(log))?"selected-":"") %>tab" width="1%" nowrap>
            <a href="logviewer.jsp?log=warn"
            >Warn</a>
            <span class="new">
        <%= ((newlogs.containsKey("warn")) ? "*" : "") %>
        </span>
         </td>
         <td class="jive-tab-spacer" width="1%"><img src="images/blank.gif" width="5" height="1" border="0"></td>
         <td class="jive-<%= (("debug".equals(log))?"selected-":"") %>tab" width="1%" nowrap>
            <a href="logviewer.jsp?log=debug"
            >Debug</a>
            <span class="new">
        <%= ((newlogs.containsKey("debug")) ? "*" : "") %>
        </span>
         </td>
         <td class="jive-tab-spring" width="92%" align="right" nowrap>
            &nbsp;
         </td>
      </tr>
   </table>
   <table class="container" cellpadding="6" cellspacing="0" border="0" width="100%">
      <tr>
         <td>

    <span class="info">
    <table cellpadding="2" cellspacing="0" border="0" width="100%">
    <tr><td colspan="5"><img src="images/blank.gif" width="1" height="4" border="0"></td></tr>
    <tr>
        <td class="label" width="1%">Filename:</td>
        <td width="1%" nowrap><b><%= logFile.getName() %></b></td>
        <td rowspan="3" width="96%">&nbsp;</td>
        <td class="label" width="1%">Order:</td>
        <td width="1%" nowrap>
            <input type="radio" name="mode" value="desc"<%= ("desc".equals(mode)?" checked":"") %>
                   onclick="this.form.submit();"
                   id="rb01"
            > <label for="rb01">Newest at top</label>
            <input type="radio" name="mode" value="asc"<%= ("asc".equals(mode)?" checked":"") %>
                   onclick="this.form.submit();"
                   id="rb02"
            > <label for="rb02">Newest at bottom</label>
        </td>
    </tr>
    <tr>
        <td class="label" width="1%" nowrap>Last Modified:</td>
        <%  Date lastMod = new Date(logFile.lastModified());
            DateFormat dateFormatter = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);
        %>
        <td width="1%" nowrap><span
           title"<%= SkinUtils.dateToText(request, pageUser, lastMod) %>"><%= dateFormatter.format(lastMod) %></span>
         </td>
         <td class="label" width="1%">Lines:</td>
         <td width="1%" nowrap>
            <select name="lines" size="1"
                    onchange="this.form.submit();">
               <% for (int j = 0; j < LINES.length; j++) {
                  String selected = (LINES[j].equals(numLinesParam)) ? " selected" : "";
               %>
               <option value="<%= LINES[j] %>"<%= selected %>><%= LINES[j] %>

                     <%  } %>
                     <%  if (!tooBig) { %>
               <option value="All"<%= (("All".equals(numLinesParam))?" selected":"") %>
               >All
                     <%  } %>
            </select>
         </td>
      </tr>
      <tr>
         <td class="label" width="1%">Size:</td>
         <% ByteFormat byteFormatter = new ByteFormat(); %>
         <td width="1%" nowrap><%= byteFormatter.format(logFile.length()) %>
         </td>
         <td class="label" width="1%">Refresh:</td>
         <td width="1%" nowrap>
            <select size="1" name="refresh" onchange="this.form.submit();">
               <% for (int j = 0; j < REFRESHES.length; j++) {
                  String selected = REFRESHES[j].equals(refreshParam) ? " selected" : "";
               %>
               <option value="<%= REFRESHES[j] %>"<%= selected %>><%= REFRESHES[j] %>

                     <%  } %>
            </select>
            (seconds)
         </td>
      </tr>

      <% // Print out a special switch to enable/disable debugging
         if ("debug".equals(log)) {
      %>
      <input type="hidden" name="wasDebugEnabled" value="<%= debugEnabled %>">
      <tr valign="top">
         <td class="label" width="1%">Debugging Enabled:</td>
         <td width="1%" nowrap>

            <input type="radio" name="debugEnabled" value="true"<%= ((debugEnabled) ? " checked" : "") %> id="de01">
            <label for="de01">Enabled</label>

            <input type="radio" name="debugEnabled" value="false"<%= ((!debugEnabled) ? " checked" : "") %> id="de02">
            <label for="de02">Disabled</label>

            (change requires restart)

            <br>

            <input type="submit" name="" value="Update">
         </td>
         <td colspan="3">&nbsp;</td>
      </tr>

      <% } %>

      <tr>
         <td colspan="5"><img src="images/blank.gif" width="1" height="8" border="0"></td>
      </tr>
   </table>
   </span>

   </td></tr>
   </table>

   <br>

   <span class="log">
<table cellpadding="1" cellspacing="0" border="0" width="100%">
<tr>
    <th class="head-num">line</th>
    <th>message</th>
</tr>
<tr>
    <td width="1%" nowrap class="num">
        <% if (ASCENDING.equals(mode)) { %>
            <% for (int j = start + 1; j <= numLines; j++) { %>
                <%= j %><br>
            <% } %>
        <% } else { %>
            <% for (int j = numLines; j >= start + 1; j--) { %>
                <%= j %><br>
            <% } %>
        <% } %>
    </td>
    <td width="99%" class="line">
        <% for (int j = 0; j < lines.length; j++) {
           if (lines[j] != null) {
        %>
            <nobr><%= lines[j] %></nobr><br>

        <% }
        }
        %>
    </td>
</tr>
</table>
</span>

</form>

<%@ include file="footer.jsp" %>
