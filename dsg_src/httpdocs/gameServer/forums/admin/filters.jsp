<%
/**
 *	$RCSfile: filters.jsp,v $
 *	$Revision: 1.12 $
 *	$Date: 2003/01/06 21:45:49 $
 */
%>

<%@ page import="java.beans.*,
                 java.util.*,
                 com.jivesoftware.util.*,
                 com.jivesoftware.forum.*,
                 com.jivesoftware.forum.database.*,
                 com.jivesoftware.forum.util.*,
                 java.lang.reflect.Method,
                com.jivesoftware.base.Filter"
    errorPage="error.jsp"
%>

<%@ include file="global.jsp" %>

<%! // Global variables/methods for this page
    private boolean isInstalledFilter(FilterManager filterManager,
            Filter filter)
    {
        try {
            int filterCount = filterManager.getFilterCount();
            if (filter == null) {
                return false;
            }
            if (filterCount < 1) {
                return false;
            }
            String filterClassname = filter.getClass().getName();
            for (int i=0; i<filterCount; i++) {
                Filter installedFilter = filterManager.getFilter(i);
                if (filterClassname.equals(installedFilter.getClass().getName())) {
                    return true;
                }
            }
        } catch (Exception e) {}
        return false;
    }

    private String getHTML(Filter filter, PropertyDescriptor descriptor)
    {
        // HTML of the customizer for this property
        StringBuffer html = new StringBuffer(50);
        // Get the name of the property (this becomes the name of the form element)
        String propName = descriptor.getName();
        // Get the current value of the property
        Object propValue = null;
        try {
            propValue = descriptor.getReadMethod().invoke(filter,null);
        }
        catch (Exception e) {e.printStackTrace();}

        // Get the classname of this property
        String className = descriptor.getPropertyType().getName();

        // HTML form elements for number values (rendered as small textfields)
        if ("int".equals(className)
            || "double".equals(className)
            || "long".equals(className))
        {
            html.append("<input type=\"text\" name=\"").append(propName).append("\" size=\"6\" maxlength=\"10\"");
            if (propValue != null) {
                html.append(" value=\"").append(propValue.toString()).append("\"");
            }
            html.append(">");
        }
        // HTML form elements for boolean values (rendered as Yes/No radio buttons)
        else if ("boolean".equals(className)) {
            boolean value = false;
            if ("true".equals(propValue.toString())) {
                value = true;
            }
            html.append("<input type=\"radio\" name=\"").append(propName).append("\" id=\"rb").append(propName).append("1\" ");
            html.append("value=\"true\"");
            html.append((value)?" checked":"");
            html.append("> <label for=\"rb").append(propName).append("1\">Yes</label> ");
            html.append("<input type=\"radio\" name=\"").append(propName).append("\" id=\"rb").append(propName).append("2\" ");
            html.append("value=\"false\"");
            html.append((!value)?" checked":"");
            html.append("> <label for=\"rb").append(propName).append("2\">No</label> ");
        }
        else if ("java.lang.String".equals(className)) {
            // Indicates we should print a textarea if the large text field is specified to be used
            boolean useLarge = ("true".equals(descriptor.getValue("useLargeTextField")));

            // HTML elements for a String or String[] (rendered as a single-line textarea)
            if (descriptor.getPropertyType().isArray()) {
                // Print out a customizer for a String array:
                String[] valArray = (String[])propValue;
                for (int i=0; i<valArray.length; i++) {
                    html.append(printStringHTML(propName+i, valArray[i], useLarge));
                    html.append("<input type=\"submit\" name=\"deletePropEntry")
                        .append(i).append("\" value=\"Delete\">")
                        .append("<br>");
                }
                html.append("<br>");

                html.append(printStringHTML(propName, null, useLarge));

                html.append("<input type=\"hidden\" name=\"addNewPropName");
                html.append("\" value=\"").append(propName).append("\">");
                html.append("<input type=\"submit\" name=\"addNewProp\" ");
                html.append("value=\"Add\">");
                html.append("<input type=\"hidden\" name=\"deletePropertyName");
                html.append("\" value=\"").append(propName).append("\">");
            }
            // Else, it's just a POS (plain old String) :)
            else {
                if (propName.toLowerCase().equals("password")) {
                    html.append("<input type=\"password\"").append(" name=\"").append(propName);
                    html.append("\" size=\"30\" maxlength=\"150\"");
                    if (propValue != null) {
                        html.append(" value=\"").append(escapeHTML(propValue.toString())).append("\"");
                    }
                    html.append(">");
                }
                else {
                    String value = null;
                    if (propValue != null) {
                        value = propValue.toString();
                    }
                    html.append(printStringHTML(propName, value, useLarge));
                }
            }
        }
        if (html.length() == 0) {
            html.append("&nbsp;");
        }
        return html.toString();
    }

    // Handles printing a string text field either as a textfield or a textarea.
    private String printStringHTML(String name, String value, boolean useLarge) {
        StringBuffer buf = new StringBuffer(50);
        if (useLarge) {
            buf.append("<textarea name=\"").append(name).append("\" cols=\"40\" rows=\"3\">");
            if (value != null) {
                buf.append(escapeHTML(value));
            }
            buf.append("</textarea>");
        }
        else {
            buf.append("<input type=\"text\" name=\"").append(name).append("\" size=\"40\" maxlength=\"255\" ");
            if (value != null) {
                buf.append("value=\"").append(escapeHTML(value)).append("\"");
            }
            buf.append(">");
        }
        return buf.toString();
    }

    private Map getFilterPropertyValues(HttpServletRequest request,
            FilterManager filterManager, Filter filter)
    {
        // Map of filter property name/value pairs
        Map map = new HashMap();
        try {
            // Property descriptors
            PropertyDescriptor[] descriptors = BeanUtils.getPropertyDescriptors(filter.getClass());
            // Loop through the properties, get the value of the property as a
            // parameter from the HttpRequest object
            for (int i=0; i<descriptors.length; i++) {
                // Don't set any array properties:
                if (!descriptors[i].getPropertyType().isArray()) {
                    String propName = descriptors[i].getName();
                    String propValue = ParamUtils.getParameter(request,propName);
                    map.put(propName, propValue);
                }
            }
        }
        catch (Exception e) {}
        return map;
    }

    private String escapeHTML(String html) {
        html = StringUtils.replace(html, "\"", "&quot;");
        return StringUtils.escapeHTMLTags(html);
    }
%>

<%	// Get parameters
    long forumID = ParamUtils.getLongParameter(request,"forum",-1);
    String classname = ParamUtils.getParameter(request,"filters");
    boolean install = ParamUtils.getBooleanParameter(request,"install");
    boolean remove = ParamUtils.getBooleanParameter(request,"remove");
    int position = ParamUtils.getIntParameter(request,"pos",-1);
    boolean edit = ParamUtils.getBooleanParameter(request,"edit");
    boolean addFilter = ParamUtils.getBooleanParameter(request,"addFilter");
    String newClassname = ParamUtils.getParameter(request,"newClassname");
    boolean saveProperties = ParamUtils.getBooleanParameter(request,"saveProperties");
    int filterIndex = ParamUtils.getIntParameter(request,"filterIndex",-1);
    boolean changePosition = ParamUtils.getBooleanParameter(request,"changePos");
    boolean up = ParamUtils.getBooleanParameter(request,"up");
    boolean down = ParamUtils.getBooleanParameter(request,"down");
    String deletePropertyName = ParamUtils.getParameter(request,"deletePropertyName");
    boolean addNewProp = request.getParameter("addNewProp") != null;
    int applyToSubj = ParamUtils.getIntParameter(request, "applyToSubj", -1);
    int applyToBody = ParamUtils.getIntParameter(request, "applyToBody", -1);
    int applyToProp = ParamUtils.getIntParameter(request, "applyToProp", -1);

    // Determine if we need to delete a String[] property entry
    boolean deletePropEntry = false;
    int deleteIndex = -1;
    for (Enumeration en=request.getParameterNames(); en.hasMoreElements(); )
    {
        String name = (String)en.nextElement();
        if (name.startsWith("deletePropEntry")) {
            try {
                int pos = "deletePropEntry".length();
                deleteIndex = Integer.parseInt(
                        name.substring(pos, name.length())
                );
            }
            catch (Exception ignored) {}
            if (deleteIndex > -1) {
                deletePropEntry = true;
                break;
            }
        }
    }

    // Indicate if we're doing global filters
    boolean isGlobal = (forumID == -1L);

    // Security check
    if (isGlobal) {
        if (!isSystemAdmin) {
            throw new UnauthorizedException("You don't have admin privileges to perform this operation.");
        }
    }
    else {
        if (!isSystemAdmin && !isForumAdmin) {
            throw new UnauthorizedException("You don't have admin privileges to perform this operation.");
        }
    }

    // Load the forum
    Forum forum = null;
    if (forumID > 0L) {
        forum = forumFactory.getForum(forumID);
    }

    // Get the filter manager
    FilterManager filterManager = null;
    if (forum != null) {
        filterManager = forum.getFilterManager();
    }
    else {
        filterManager = forumFactory.getFilterManager();
    }

    // Add a new property for a String[] property type:
    if (addNewProp) {
        // Get the name of the filter for the new property:
        String newPropName = ParamUtils.getParameter(request,"addNewPropName");
        if (newPropName != null) {
            // Get the value of the new property:
            String newPropValue = ParamUtils.getParameter(request,"addNewProp" + newPropName);
            if (newPropValue != null) {
                // The filter we're working with:
                Filter filter = filterManager.getFilter(filterIndex);
                PropertyDescriptor[] descriptors = (Introspector.getBeanInfo(filter.getClass())).getPropertyDescriptors();
                PropertyDescriptor propDescriptor = null;
                // Look for the property specified
                for (int i=0; i<descriptors.length; i++) {
                    if (descriptors[i].getName().equals(newPropName)) {
                        propDescriptor = descriptors[i];
                        break;
                    }
                }
                if (propDescriptor != null) {
                    // Get both the read and write methods:
                    Method readMethod = propDescriptor.getReadMethod();
                    Method writeMethod = propDescriptor.getWriteMethod();
                    // Get the String[] via the read method:
                    String[] entries = (String[])readMethod.invoke(filter, null);
                    // Make a new entry array of entries.length+1 because we're
                    // adding one more entry to the property
                    String[] newEntries = new String[entries.length+1];
                    for (int i=0; i<entries.length; i++) {
                        newEntries[i] = entries[i];
                    }
                    // The new prop value goes in the last spot of newEntries:
                    newEntries[newEntries.length-1] = newPropValue;
                    // Use the write method to save the new entries:
                    writeMethod.invoke(filter, new Object[]{newEntries});
                    // Save filters
                    filterManager.saveFilters();
                    // Done, so redirect
                    StringBuffer url = new StringBuffer();
                    url.append("filters.jsp?forum=").append(forumID);
                    url.append("&edit=true&pos=").append(filterIndex);
                    response.sendRedirect(url.toString());
                    return;
                }
            }
        }
    }

    // Remove one of the String[] prop entries:
    if (deletePropEntry) {
        if (deletePropertyName != null) {
            // The filter we're working with:
            Filter filter = filterManager.getFilter(filterIndex);
            PropertyDescriptor[] descriptors = (Introspector.getBeanInfo(filter.getClass())).getPropertyDescriptors();
            PropertyDescriptor propDescriptor = null;
            // Look for the property specified
            for (int i=0; i<descriptors.length; i++) {
                if (descriptors[i].getName().equals(deletePropertyName)) {
                    propDescriptor = descriptors[i];
                    break;
                }
            }
            if (propDescriptor != null) {
                // Get both the read and write methods:
                Method readMethod = propDescriptor.getReadMethod();
                Method writeMethod = propDescriptor.getWriteMethod();
                // Get the String[] via the read method:
                String[] entries = (String[])readMethod.invoke(filter, null);
                // Make a new entry array of entries.length+1 because we're
                // adding one more entry to the property
                String[] newEntries = new String[entries.length-1];
                int offset = 0;
                for (int i=0; i<newEntries.length; i++) {
                    // Skip the index of the item we want to delete
                    if (i == deleteIndex) {
                        offset++;
                    }
                    newEntries[i] = entries[i+offset];
                }
                // Use the write method to save the new entries:
                writeMethod.invoke(filter, new Object[]{newEntries});
                // Save filters
                filterManager.saveFilters();
                // Done, so redirect
                StringBuffer url = new StringBuffer();
                url.append("filters.jsp?forum=").append(forumID);
                url.append("&edit=true&pos=").append(filterIndex);
                response.sendRedirect(url.toString());
                return;
            }
        }
    }

    // Save filter properties
    if (saveProperties) {
        if (filterIndex >= 0) {
            // The filter we're working with
            Filter filter = filterManager.getFilter(filterIndex);
            // A map of name/value pairs. The names are the names of the bean
            // properties and the values come as parameters to this page
            Map properties = getFilterPropertyValues(request, filterManager, filter);
            // Set the properties
            BeanUtils.setProperties(filter, properties);
            // Save the filters
            filterManager.saveFilters();
            // Determine the filter types:
            long filterType = 0L;
            if (applyToSubj == (int)ForumFilterType.FORUM_MESSAGE_SUBJECT) {
                filterManager.addFilterTypes(filterIndex, ForumFilterType.FORUM_MESSAGE_SUBJECT);
            }
            else {
                filterManager.removeFilterTypes(filterIndex, ForumFilterType.FORUM_MESSAGE_SUBJECT);
            }
            if (applyToBody == (int)ForumFilterType.FORUM_MESSAGE_BODY) {
                filterManager.addFilterTypes(filterIndex, ForumFilterType.FORUM_MESSAGE_BODY);
            }
            else {
                filterManager.removeFilterTypes(filterIndex, ForumFilterType.FORUM_MESSAGE_BODY);
            }
            if (applyToProp == (int)ForumFilterType.FORUM_MESSAGE_PROPERTY) {
                filterManager.addFilterTypes(filterIndex, ForumFilterType.FORUM_MESSAGE_PROPERTY);
            }
            else {
                filterManager.removeFilterTypes(filterIndex, ForumFilterType.FORUM_MESSAGE_PROPERTY);
            }

            filterManager.saveFilters();

            // Done, so redirect to this page
            response.sendRedirect("filters.jsp?forum=" + forumID);
            return;
        }
    }

    // Add a new filter to the list of installable filters
    if (addFilter) {
        try {
            if (newClassname == null) {
                throw new ClassNotFoundException("No classname specified.");
            }
            filterManager.addFilterClass(newClassname.trim());
        }
        catch (ClassNotFoundException cnfe) {
            setOneTimeMessage(session, "message",
                "\"" + newClassname + "\" is not a valid classname.");
        }
        response.sendRedirect("filters.jsp?forum=" + forumID);
        return;
    }

    // Change the position of a filter
    if (changePosition) {
        if (filterIndex >= 0) {
            // Get the filter at the specified filter position
            Filter filter = filterManager.getFilter(filterIndex);
            // Re-add it based on the "direction" we're doing
            long filterTypes = filterManager.getFilterTypes(filterIndex);
            // Remove it
            filterManager.removeFilter(filterIndex);
            if (up) {
                filterManager.addFilter(filterIndex-1, filter);
                filterManager.addFilterTypes(filterIndex-1, filterTypes);
            }
            if (down) {
                filterManager.addFilter(filterIndex+1, filter);
                filterManager.addFilterTypes(filterIndex+1, filterTypes);
            }
            // done, so redirect
            response.sendRedirect("filters.jsp?forum=" + forumID);
            return;
        }
    }

    // Number of installed filters
    int filterCount = filterManager.getFilterCount();
    // All filter classes
    Filter[] filters = filterManager.getAvailableFilters();

    if (install && classname != null) {
        try {
            Filter newFilter = (Filter)(ClassUtils.forName(classname)).newInstance();
            filterManager.addFilter(newFilter);
            int index = filterManager.getFilterCount()-1;
            if (applyToSubj == (int)ForumFilterType.FORUM_MESSAGE_SUBJECT) {
                filterManager.addFilterTypes(index, ForumFilterType.FORUM_MESSAGE_SUBJECT);
            }
            if (applyToBody == (int)ForumFilterType.FORUM_MESSAGE_BODY) {
                filterManager.addFilterTypes(index, ForumFilterType.FORUM_MESSAGE_BODY);
            }
            if (applyToProp == (int)ForumFilterType.FORUM_MESSAGE_PROPERTY) {
                filterManager.addFilterTypes(index, ForumFilterType.FORUM_MESSAGE_PROPERTY);
            }
            String redirect = "filters.jsp";
            if (forumID > -1L) {
                redirect += "?forum=" + forumID;
            }
            response.sendRedirect(redirect);
            return;
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    if (remove && position > -1) {
        filterManager.removeFilter(position);
        String redirect = "filters.jsp";
        if (forumID > -1L) {
            redirect += "?forum=" + forumID;
        }
        response.sendRedirect(redirect);
        return;
    }
%>

<%@ include file="header.jsp" %>

<%  // Title of this page and breadcrumbs
    String title = "";
    if (isGlobal) {
        title = "Global Message Filters";
    } else {
        title = "Message Filters";
    }

    String[][] breadcrumbs = null;
    if (isGlobal) {
        breadcrumbs = new String[][] {
            {"Main", "main.jsp"},
            {"Global Message Filters", "filters.jsp"}
        };
    }
    else {
        breadcrumbs = new String[][] {
            {"Main", "main.jsp"},
            {"Forums", "forums.jsp"},
            {"Edit Forum", "editForum.jsp?forum="+forumID},
            {"Message Filters", "filters.jsp?forum="+forumID}
        };
    }
%>
<%@ include file="title.jsp" %>

<span>
<font size="-1">
<%  if (isGlobal) { %>
    Filters dynamically reformat the contents of messages. Use the forms below
    to install and customize global filters.
<%  } else { %>
    Filters dynamically reformat the contents of messages. Use the forms below
    to install and customize filters for this forum.
    Please note that global message filters are always applied before any forum message filters.
<%  } %>
</font>
</span>

<p>

<script language="JavaScript" type="text/javascript">
var filterInfo = new Array(
<%	for (int i=0; i<filters.length; i++) {
            try {
                BeanDescriptor descriptor = (Introspector.getBeanInfo(filters[i].getClass())).getBeanDescriptor();
%>
    new Array(
    "<%= descriptor.getBeanClass().getName() %>",
    "<%= descriptor.getValue("version") %>",
    "<%= descriptor.getValue("author") %>",
    "<%= StringUtils.replace(descriptor.getShortDescription(), "\"", "\\\"") %>"
    )
<%          if ((filters.length-i) > 1) { %>
		,
<%	        }
        } catch (Exception e) {}
    }
%>
);
function properties(theForm) {
    var className = theForm.filters.options[theForm.filters.selectedIndex].value;
    var selected = 0;
    for (selected=0; selected<filterInfo.length; selected++) {
        if (filterInfo[selected][0] == className) {
            var version = filterInfo[selected][1];
            var author = filterInfo[selected][2];
            var description = filterInfo[selected][3];
            theForm.version.value = ((version=="null")?"":version);
            theForm.author.value = ((author=="null")?"":author);
            theForm.description.value = ((description=="null")?"":description);
            break;
        }
    }
}
</script>

<%  // Print out a message if one exists
    String oneTimeMessage = getOneTimeMessage(session, "message");
    if (oneTimeMessage != null) {
%>
    <font size="-1" color="#ff0000">
    <i><%= oneTimeMessage %></i>
    </font>
    <p>
<%  }
%>

<%  // Colors
    String red = "#ffeeee";
    String yellow = "#ffffee";
    if (filterCount > 0) {
%>
<table cellpadding="0" cellspacing="0" border="0">
<tr><td>
    <font size="-1"><b>Current Filters</b></font>
    </td>
    <td>
    <a href="#" onclick="helpwin('filters','current_filters');return false;"
     title="Click for help"
     ><img src="images/help-16x16.gif" width="16" height="16" border="0" hspace="8"></a>
    </td>
</tr>
</table><br>
<ul>
	<table bgcolor="<%= tblBorderColor %>" cellpadding="0" cellspacing="0" border="0">
    <tr><td>
    <table cellpadding="4" cellspacing="1" border="0" width="100%">
	<tr bgcolor="#eeeeee">
	<td align="center"><font size="-2" face="verdana"><b>ORDER</b></font></td>
	<td align="center"><font size="-2" face="verdana"><b>NAME</b></font></td>
	<td align="center"><font size="-2" face="verdana"><b>DESCRIPTION</b></font></td>
    <%  if (filterCount > 1) { %>
	<td align="center"><font size="-2" face="verdana"><b>MOVE</b></font></td>
    <%  } %>
	<td align="center"><font size="-2" face="verdana"><b>EDIT</b></font></td>
	<td align="center"><font size="-2" face="verdana"><b>DELETE</b></font></td>
    </tr>
<%  // Indicates if the previous filter was cacheable
    boolean wasCacheable = false;
    boolean cacheWarning = false;
    // Loop through all filters
    for (int i=0; i<filterCount; i++) {
        try {
            Filter filter = filterManager.getFilter(i);
            // Descriptor for this filter
            BeanDescriptor descriptor = (Introspector.getBeanInfo(filter.getClass())).getBeanDescriptor();
            // Properties for this filter
            PropertyDescriptor[] descriptors = BeanUtils.getPropertyDescriptors(filter.getClass());
            // Version of this filter
            String version = (String)descriptor.getValue("version");
            // Description of this filter
            String description = StringUtils.escapeHTMLTags(descriptor.getShortDescription());
            // Filter types:
            long filterTypes = filterManager.getFilterTypes(i);
            boolean appliesToSubj = (filterTypes & ForumFilterType.FORUM_MESSAGE_SUBJECT)
                                            == ForumFilterType.FORUM_MESSAGE_SUBJECT;
            boolean appliesToBody = (filterTypes & ForumFilterType.FORUM_MESSAGE_BODY)
                                            == ForumFilterType.FORUM_MESSAGE_BODY;
            boolean appliesToProp = (filterTypes & ForumFilterType.FORUM_MESSAGE_PROPERTY)
                                            == ForumFilterType.FORUM_MESSAGE_PROPERTY;
%>
    <tr bgcolor="#ffffff">
        <td><font size="-1"><%= (i+1) %></font></td>
        <td nowrap><font size="-1"><%= descriptor.getDisplayName() %></font></td>
        <td><font size="-1"><%= (description!=null)?description:"&nbsp;" %></font></td>
        <%  if (filterCount > 1) { %>
        <td>
            <%  if ((i+1)<filterCount) { %>
                <a href="filters.jsp?forum=<%= forumID %>&changePos=true&down=true&filterIndex=<%= i %>"
                ><img src="images/arrow_down.gif" width="13" height="9" alt="Move this filter down." border="0"></a>
            <%  } else { %>
                <img src="images/blank.gif" width="13" height="9" border="0">
            <%  } %>

            <%  if (i != 0) { %>
                <a href="filters.jsp?forum=<%= forumID %>&changePos=true&up=true&filterIndex=<%= i %>"
                ><img src="images/arrow_up.gif" width="13" height="9" alt="Move this filter up." border="0"></a>
            <%  } else { %>
                <img src="images/blank.gif" width="13" height="9" border="0">
            <%  } %>
        </td>
        <%  } %>
        <td align="center">
            <a href="filters.jsp?edit=true&forum=<%= forumID %>&pos=<%= i %>"
            ><img src="images/button_edit.gif" width="17" height="17" alt="Edit the properties of this filter" border="0"
            ></a>
        </td>
        <td align="center">
            <a href="filters.jsp?remove=true&forum=<%= forumID %>&pos=<%= i %>"
            ><img src="images/button_delete.gif" width="17" height="17" alt="Delete this filter" border="0"
            ></a>
        </td>
    </tr>
<%  if (position == i && edit) { %>
    <form action="filters.jsp" method="post">
    <input type="hidden" name="forum" value="<%= forumID %>">
    <input type="hidden" name="saveProperties" value="true">
    <input type="hidden" name="filterIndex" value="<%= i %>">
    <tr bgcolor="#ffffff">
        <td>&nbsp;</td>
        <td colspan="<%= (filterCount > 1)?"5":"4" %>">
            <table cellpadding="2" cellspacing="0" border="0" width="100%">
            <%  int color = 1; %>
            <tr bgcolor=<%= (color++%2==0)?"#f4f5f7":"#ffffff" %>>
                <td width="60%">
                    Apply Filtering To:
                </td>
                <td width="20%">&nbsp;</td>
                <td width="20%" nowrap>
                    <table cellpadding="2" cellspacing="0" border="0">
                    <tr>
                        <td>
                            <input type="checkbox" name="applyToSubj" value="<%= ForumFilterType.FORUM_MESSAGE_SUBJECT %>" id="atsubj"
                             <%= ((appliesToSubj)?" checked":"") %>>
                        </td>
                        <td nowrap>
                            <label for="atsubj">Message Subject</label>
                        </td>
                    </tr>
                    <tr>
                        <td>
                            <input type="checkbox" name="applyToBody" value="<%= ForumFilterType.FORUM_MESSAGE_BODY %>" id="atbody"
                             <%= ((appliesToBody)?" checked":"") %>>
                        </td>
                        <td nowrap>
                            <label for="atbody">Message Body</label>
                        </td>
                    </tr>
                    <tr>
                        <td>
                            <input type="checkbox" name="applyToProp" value="<%= ForumFilterType.FORUM_MESSAGE_PROPERTY %>" id="atprop"
                             <%= ((appliesToProp)?" checked":"") %>>
                        </td>
                        <td nowrap>
                            <label for="atprop">Message Property Values</label>
                        </td>
                    </tr>
                    </table>
                </td>
            </tr>
            <%  for (int j=0; j<descriptors.length; j++) {
                    color ++;
                    boolean isString = "java.lang.String".equals(descriptors[j].getPropertyType().getName());
                    if (isString) {
            %>
                    <tr bgcolor=<%= (color%2==0)?"#f4f5f7":"#ffffff" %>>
                        <td colspan="3">
                            <font size="-1"><%= descriptors[j].getDisplayName() %></font>
                            <br>
                            <font size="-2"><%= descriptors[j].getShortDescription() %></font>
                        </td>
                    </tr>
                    <tr bgcolor=<%= (color%2==0)?"#f4f5f7":"#ffffff" %>>
                        <td colspan="3">
                            <%= getHTML(filter, descriptors[j]) %>
                        </td>
                    </tr>
                <%  } else { %>
                    <tr bgcolor=<%= (color%2==0)?"#f4f5f7":"#ffffff" %>>
                        <td width="70%">
                            <font size="-1"><%= descriptors[j].getDisplayName() %></font>
                            <br>
                            <font size="-2"><%= descriptors[j].getShortDescription() %></font>
                        </td>
                        <td width="10%">&nbsp;</td>
                        <td width="20%" nowrap>
                            <%= getHTML(filter, descriptors[j]) %>
                        </td>
                    </tr>
            <%      }
                }
            %>
            <tr>
                <td colspan="4" align="right">
                    <font size="-1">
                    <input type="submit" value="Save Properties">
                    </font>
                </td>
            </tr>
            </table>
        </td>
    </tr>
    </form>
<%          } %>
<%      } catch (Exception e) {}
    }
%>
    </table>
    </td></tr>
    </table>
    <br>

<%  } %>
</ul>
</form>

<p>

<form action="filters.jsp" method="post">
<input type="hidden" name="forum" value="<%= forumID %>">
<input type="hidden" name="install" value="true">

<span class="jive-install-filter">

<table cellpadding="0" cellspacing="0" border="0">
<tr><td>
    <font size="-1"><b>Install Filter</b></font>
    </td>
    <td>
    <a href="#" onclick="helpwin('filters','install_filter');return false;"
     title="Click for help"
     ><img src="images/help-16x16.gif" width="16" height="16" border="0" hspace="8"></a>
    </td>
</tr>
</table><br>

<ul>
	<table bgcolor="<%= tblBorderColor %>" cellpadding="0" cellspacing="0" border="0" width="1%">
    <tr><td>
        <table cellpadding="4" cellspacing="1" border="0" width="100%">
        <tr bgcolor="#eeeeee">
            <td align="center">
                <font size="-2" face="verdana"><b>AVAILABLE FILTERS</b></font>
            </td>
        </tr>
        <tr bgcolor="#ffffff">
            <td>
                <table cellpadding="1" cellspacing="0" border="0">
                <tr>
                    <td width="48%" valign="top">
                        <font size="-1">
                        <select size="8" name="filters" onchange="properties(this.form);">
                        <%  for (int i=0; i<filters.length; i++) {
                                if (!isInstalledFilter(filterManager, filters[i])) {
                                    BeanDescriptor descriptor
                                            = (Introspector.getBeanInfo(filters[i].getClass())).getBeanDescriptor();
                        %>
                                    <option value="<%= descriptor.getBeanClass().getName() %>"
                                     ><%= descriptor.getDisplayName() %>

                        <%      } // end if
                            } // end for
                        %>
                        </select>
                        </font>
                    </td>
                    <td width="2%"><img src="images/blank.gif" width="5" height="1" border="0"></td>
                    <td width="48%" valign="top">

                        <table cellpadding="2" cellspacing="0" border="0" width="100%">
                        <tr>
                            <td><font size="-2">VERSION</font></td>
                            <td><input type="text" size="20" name="version" style="width:100%"></td>
                        </tr>
                        <tr>
                            <td><font size="-2">AUTHOR</font></td>
                            <td><input type="text" size="20" name="author" style="width:100%"></td>
                        </tr>
                        <tr>
                            <td valign="top"><font size="-2">DESCRIPTION</font></td>
                            <td><textarea name="description" cols="20" rows="5" wrap="virtual"></textarea></td>
                        </tr>
                        </table>

                    </td>
                </tr>
                <tr>
                    <td colspan="3">

                        <table cellpadding="2" cellspacing="0" border="0" width="100%">
                        <tr valign="top">
                            <td width="1%" nowrap>
                                Apply Filter To:
                            </td>
                            <td width="99%">
                                <input type="checkbox" name="applyToSubj" value="<%= ForumFilterType.FORUM_MESSAGE_SUBJECT %>" id="ms" checked>
                                <label for="ms">Message Subjects</label>
                                <br>
                                <input type="checkbox" name="applyToBody" value="<%= ForumFilterType.FORUM_MESSAGE_BODY %>" id="mb" checked>
                                <label for="mb">Message Body</label>
                                <br>
                                <input type="checkbox" name="applyToProp" value="<%= ForumFilterType.FORUM_MESSAGE_PROPERTY %>" id="mp">
                                <label for="mp">Message Properties</label>
                            </td>
                        </tr>
                        </table>

                    </td>
                </tr>
                <tr>
                    <td colspan="3" align="center">
                        <font size="-1">
                        <input type="submit" value="Install">
                        </font>
                    </td>
                </tr>
                </table>
            </td>
        </tr>
        </table>
    </td></tr>
    </table>
</ul>

</span>

</form>

<form action="filters.jsp">
<input type="hidden" name="addFilter" value="true">
<input type="hidden" name="forum" value="<%= forumID %>">
<table cellpadding="0" cellspacing="0" border="0">
<tr><td>
    <font size="-1"><b>Add Filter Class</b></font>
    </td>
    <td>
    <a href="#" onclick="helpwin('filters','add_filter_class');return false;"
     title="Click for help"
     ><img src="images/help-16x16.gif" width="16" height="16" border="0" hspace="8"></a>
    </td>
</tr>
</table><br>
<ul>
    <table cellpadding="2" cellspacing="0" border="0">
    <tr>
    	<td><font size="-1">Class Name:</font></td>
    	<td><input type="text" name="newClassname" value="" size="30" maxlength="100"></td>
    	<td><font size="-1"><input type="submit" value="Add Filter"></font></td>
    </tr>
    </table>
</ul>
</form>

<p>


</body>
</html>

