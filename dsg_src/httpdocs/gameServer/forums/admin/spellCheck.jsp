<%@ page import="java.io.*,
                 java.util.*,
                 com.jivesoftware.forum.*,
                 com.jivesoftware.forum.util.*,
                 com.jivesoftware.util.*"
	errorPage="error.jsp"
%>

<%@ include file="global.jsp" %>
 
<%	if (!isSystemAdmin) {
        throw new UnauthorizedException("You don't have admin privileges to perform this operation.");
    }

    // Get parameters
    String newCustomWord = ParamUtils.getParameter(request, "newCustomWord");
    String newDictionary = ParamUtils.getParameter(request,"newDictionary");
    String[] deleteableCustomWordList = ParamUtils.getParameters(request,"customWordList");
    boolean removeCustomWord = ParamUtils.getBooleanParameter(request,"removeCustomWord");
    boolean addCustomWord = ParamUtils.getBooleanParameter(request,"addCustomWord");
    boolean setDictionary = ParamUtils.getBooleanParameter(request,"setDictionary");

    // Current dictionaries:
    String currentDict = JiveGlobals.getJiveProperty("spelling.admin.dictionary");
    if (currentDict == null) {
        currentDict = "EN"; // English (US) by default
    }

    // Dictionary filenames:
    String[][] dictionaries = new String[][] {
        { "EN",     "English",            "ssceam.tlx,ssceam2.clx" },
        { "EN UK",  "English (UK)",       "sscebr.tlx,sscebr2.clx" },
        { "FR",     "French",             "sscefr.tlx,sscefr2.clx" },
        { "FR CA",  "French (Canada)",    "sscefr.tlx,sscefr2.clx" },
        { "DE",     "German",             "sscege.tlx,sscege2.clx" },
        { "IT",     "Italian",            "ssceit.tlx,ssceit2.clx" },
        { "ES",     "Spanish",            "sscesp.tlx,sscesp2.clx" },
        { "",       "Other",              "" }
    };

    if (setDictionary) {
        if (newDictionary != null) {
            String newDictFiles = "";
            for (int i=0; i<dictionaries.length; i++) {
                if (newDictionary.equals(dictionaries[i][0])) {
                    newDictFiles = dictionaries[i][2];
                }
            }
            // Remove the option settings:
            JiveGlobals.deleteJiveProperty("spelling.SPLIT_CONTRACTED_WORDS_OPT");
            JiveGlobals.deleteJiveProperty("spelling.SPLIT_WORDS_OPT");
            JiveGlobals.deleteJiveProperty("spelling.ALLOW_ACCENTED_CAPS_OPT");
            // Set the options based on the dictionary:
            // SPLIT_CONTRACTED_WORDS_OPT -- true for Italian, French:
            if ("IT".equals(newDictionary)
                    || "FR".equals(newDictionary)
                    || "FR CA".equals(newDictionary))
            {
                JiveGlobals.setJiveProperty("spelling.SPLIT_CONTRACTED_WORDS_OPT","true");
            }
            // SPLIT_WORDS_OPT -- true for German
            if ("DE".equals(newDictionary)) {
                JiveGlobals.setJiveProperty("spelling.SPLIT_WORDS_OPT","true");
            }
            // ALLOW_ACCENTED_CAPS_OPT -- false for Canadian French
            if ("FR CA".equals(newDictionary)) {
                JiveGlobals.setJiveProperty("spelling.ALLOW_ACCENTED_CAPS_OPT","false");
            }
            // Set the current dictionary: If we're using German, Italian,
            // Spanish or French, also set the English dictionary too.
            if ("DE".equals(newDictionary)
                    || "IT".equals(newDictionary)
                    || "ES".equals(newDictionary)
                    || "FR CA".equals(newDictionary)
                    || "FR".equals(newDictionary))
            {
                  JiveGlobals.setJiveProperty("spelling.dictionaries",
                          "ssceam.tlx,ssceam2.clx," + newDictFiles);
            }
            else {
                JiveGlobals.setJiveProperty("spelling.dictionaries",newDictFiles);
            }
            // Set another Jive property which tells us what spell checker
            // we're using (for this page):
            JiveGlobals.setJiveProperty("spelling.admin.dictionary",newDictionary);
            // Reload the dictionaries:
            SpellChecker.reloadDictionaries();
            // Done, so redirect
            response.sendRedirect("spellCheck.jsp");
            return;
        }
    }

    if (removeCustomWord) {
        for (int i=0; i<deleteableCustomWordList.length; i++) {
            SpellChecker.deleteCustomWord(deleteableCustomWordList[i]);
        }
        response.sendRedirect("spellCheck.jsp");
        return;
    }

    if (addCustomWord) {
        if (newCustomWord != null) {
            SpellChecker.addCustomWord(newCustomWord);
        }
        response.sendRedirect("spellCheck.jsp");
        return;
    }

	String customWordList[] = SpellChecker.getCustomWords();
    // Sort the list:
    Arrays.sort(customWordList);

    // special onload command to load the sidebar
    onload = " onload=\"parent.frames['sidebar'].location.href='sidebar.jsp?sidebar=system';\"";
%>
<%@ include file="header.jsp" %>

<p>

<%  // Title of this page and breadcrumbs
    String title = "Spell Check Settings";
    String[][] breadcrumbs = {
        {"Main", "main.jsp"},
        {title, "spellCheck.jsp"}
    };
%>
<%@ include file="title.jsp" %>

<font size="-1"><b>Main Dictionary</b></font>
<ul>
    <font size="-1">
    <%  for (int i=0; i<dictionaries.length; i++) {
            if (currentDict.equals(dictionaries[i][0])) {
    %>
        <%= dictionaries[i][1] %>
    <%          break;
            }
        }
    %>
    </font>
</ul>

<form action="spellCheck.jsp">
<input type="hidden" name="setDictionary" value="true">
<font size="-1"><b>Change Main Dictionary</b></font>
<ul>
    <font size="-1">
    <select size="1" name="newDictionary">
    <%  for (int i=0; i<dictionaries.length; i++) {
            if (!"".equals(dictionaries[i][0])) {
                String selected = "";
                if (currentDict.equals(dictionaries[i][0])) {
                    selected = " selected";
                }
    %>
        <option value="<%= dictionaries[i][0] %>"<%= selected %>><%= dictionaries[i][1] %>  &nbsp;&nbsp;
    <%      }
        }
    %>
    </select>
    <input type="submit" value="Set Dictionary">
    </font>

</ul>
</form>

<font size="-1"><b>Custom Dictionary</b></font>
<ul>
    <font size="-1">
    The custom dictionary contains words that should not be marked as incorrect,
    such as company names, acronyms, etc.
    </font><p>

    <form action="spellCheck.jsp">
    <input type="hidden" name="removeCustomWord" value="true">

    <table bgcolor="<%= tblBorderColor %>" cellpadding="0" cellspacing="0" border="0">
    <tr><td>
    <table bgcolor="<%= tblBorderColor %>" cellpadding="3" cellspacing="1" border="0">
    <tr><td bgcolor="#eeeeee" align="center">
        <font size="-2" face="verdana"><b>WORD</b></font>
        </td>
        <td bgcolor="#eeeeee" align="center">
        <font size="-2" face="verdana"><b>DELETE?</b></font>
        </td>
    </tr>
    <%  if (customWordList.length == 0) { %>
    <tr bgcolor="#ffffff">
        <td colspan="2" align="center"><font size="-1"><i>No Custom Words</i></font></td>
    </tr>
    <%  } %>
    <%  for (int i=0; i<customWordList.length; i++) { %>
    <tr bgcolor="#ffffff">
        <td>
        <font size="-1"><%= customWordList[i] %></font>
        </td>
        <td align="center"><input type="checkbox" name="customWordList" value="<%= customWordList[i] %>"></td>
    </tr>
    <%  } %>
    <tr bgcolor="#ffffff">
        <td colspan="2" align="center">
        <input type="submit" value="Remove Word">
        </td>
    </tr>
    </table>
    </td></tr>
    </table>

    </form>
</ul>

<font size="-1"><b>Add Word to Custom Dictionary</b></font>
<ul>
    <form action="spellCheck.jsp">
    <input type="hidden" name="addCustomWord" value="true">

    <font size="-1">
    Add Word:
    </font>
    <input type="text" name="newCustomWord" value="" size="30" maxlength="50">
    <input type="submit" value="Add New Word">

    </form>
</ul>



<%@ include file="footer.jsp" %>
