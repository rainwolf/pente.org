<%
/**
 * $RCSfile: interceptors.jsp,v $
 * $Revision: 1.7 $
 * $Date: 2003/01/09 05:59:06 $
 */
%>

<%@ page import="java.beans.*,
                 java.util.*,
                 com.jivesoftware.util.*,
                 com.jivesoftware.forum.*,
                 com.jivesoftware.forum.database.*,
                 com.jivesoftware.forum.util.*,
                 java.lang.reflect.Method"
    errorPage="error.jsp"
%>

<%@ include file="global.jsp" %>

<%! // Global variables/methods for this page

    private boolean isInstalledInterceptor(InterceptorManager interceptorManager, MessageInterceptor interceptor) {
        try {
            int intceptorCount = interceptorManager.getInterceptorCount();
            if (interceptor == null) {
                return false;
            }
            if (intceptorCount < 1) {
                return false;
            }
            String interceptorClassname = interceptor.getClass().getName();
            for (int i=0; i<intceptorCount; i++) {
                MessageInterceptor installedInterceptor = interceptorManager.getInterceptor(i);
                if (interceptorClassname.equals(installedInterceptor.getClass().getName())) {
                    return true;
                }
            }
        }
        catch (Exception ignored) {}
        return false;
    }

    private String getHTML(MessageInterceptor interceptor, PropertyDescriptor descriptor)
    {
        // HTML of the customizer for this property
        StringBuffer html = new StringBuffer(50);
        // Get the name of the property (this becomes the name of the form element)
        String propName = descriptor.getName();
        // Get the current value of the property
        Object propValue = null;
        try {
            propValue = descriptor.getReadMethod().invoke(interceptor,null);
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

    private Map getInterceptorPropertyValues(HttpServletRequest request,
            InterceptorManager interceptorManager, MessageInterceptor interceptor)
    {
        // Map of interceptor property name/value pairs
        Map map = new HashMap();
        try {
            // Property descriptors
            PropertyDescriptor[] descriptors = BeanUtils.getPropertyDescriptors(interceptor.getClass());
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
    String classname = ParamUtils.getParameter(request,"interceptors");
    boolean install = ParamUtils.getBooleanParameter(request,"install");
    boolean remove = ParamUtils.getBooleanParameter(request,"remove");
    int position = ParamUtils.getIntParameter(request,"pos",-1);
    boolean edit = ParamUtils.getBooleanParameter(request,"edit");
    boolean addInterceptor = ParamUtils.getBooleanParameter(request,"addInterceptor");
    String newClassname = ParamUtils.getParameter(request,"newClassname");
    boolean saveProperties = ParamUtils.getBooleanParameter(request,"saveProperties");
    int interceptorIndex = ParamUtils.getIntParameter(request,"interceptorIndex",-1);
    boolean changePosition = ParamUtils.getBooleanParameter(request,"changePos");
    boolean up = ParamUtils.getBooleanParameter(request,"up");
    boolean down = ParamUtils.getBooleanParameter(request,"down");
    String deletePropertyName = ParamUtils.getParameter(request,"deletePropertyName");
    boolean addNewProp = request.getParameter("addNewProp") != null;

    // Determine if we need to delete a String[] property entry
    boolean deletePropEntry = false;
    int deleteIndex = -1;
    for (Enumeration enumr=request.getParameterNames(); enumr.hasMoreElements(); )
    {
        String name = (String)enumr.nextElement();
        if (name.startsWith("deletePropEntry")) {
            try {
                int pos = "deletePropEntry".length();
                deleteIndex = Integer.parseInt(name.substring(pos, name.length()));
            }
            catch (Exception ignored) {}
            if (deleteIndex > -1) {
                deletePropEntry = true;
                break;
            }
        }
    }

    // Indicate if we're doing global interceptors
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

    // Get the interceptor manager
    InterceptorManager interceptorManager = null;
    if (forum != null) {
        interceptorManager = forum.getInterceptorManager();
    }
    else {
        interceptorManager = forumFactory.getInterceptorManager();
    }

    // Add a new property for a String[] property type:
    if (addNewProp) {
        // Get the name of the interceptor for the new property:
        String newPropName = ParamUtils.getParameter(request,"addNewPropName");
        if (newPropName != null) {
            // Get the value of the new property:
            String newPropValue = ParamUtils.getParameter(request,"addNewProp" + newPropName);
            if (newPropValue != null) {
                // The interceptor we're working with:
                MessageInterceptor interceptor = interceptorManager.getInterceptor(interceptorIndex);
                PropertyDescriptor[] descriptors = (Introspector.getBeanInfo(interceptor.getClass())).getPropertyDescriptors();
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
                    String[] entries = (String[])readMethod.invoke(interceptor, null);
                    // Make a new entry array of entries.length+1 because we're
                    // adding one more entry to the property
                    String[] newEntries = new String[entries.length+1];
                    for (int i=0; i<entries.length; i++) {
                        newEntries[i] = entries[i];
                    }
                    // The new prop value goes in the last spot of newEntries:
                    newEntries[newEntries.length-1] = newPropValue;
                    // Use the write method to save the new entries:
                    writeMethod.invoke(interceptor, new Object[]{newEntries});
                    // Save interceptors
                    interceptorManager.saveInterceptors();
                    // Done, so redirect
                    StringBuffer url = new StringBuffer();
                    url.append("interceptors.jsp?forum=").append(forumID);
                    url.append("&edit=true&pos=").append(interceptorIndex);
                    response.sendRedirect(url.toString());
                    return;
                }
            }
        }
    }

    // Remove one of the String[] prop entries:
    if (deletePropEntry) {
        if (deletePropertyName != null) {
            // The interceptor we're working with:
            MessageInterceptor interceptor = interceptorManager.getInterceptor(interceptorIndex);
            PropertyDescriptor[] descriptors = (Introspector.getBeanInfo(interceptor.getClass())).getPropertyDescriptors();
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
                String[] entries = (String[])readMethod.invoke(interceptor, null);
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
                writeMethod.invoke(interceptor, new Object[]{newEntries});
                // Save interceptors
                interceptorManager.saveInterceptors();
                // Done, so redirect
                StringBuffer url = new StringBuffer();
                url.append("interceptors.jsp?forum=").append(forumID);
                url.append("&edit=true&pos=").append(interceptorIndex);
                response.sendRedirect(url.toString());
                return;
            }
        }
    }

    // Save interceptor properties
    if (saveProperties) {
        if (interceptorIndex >= 0) {
            // The interceptor we're working with
            MessageInterceptor interceptor = interceptorManager.getInterceptor(interceptorIndex);
            // A map of name/value pairs. The names are the names of the bean
            // properties and the values come as parameters to this page
            Map properties = getInterceptorPropertyValues(request, interceptorManager, interceptor);
            // Set the properties
            BeanUtils.setProperties(interceptor, properties);
            // Save the interceptors
            interceptorManager.saveInterceptors();
            // Done, so redirect to this page
            response.sendRedirect("interceptors.jsp?forum=" + forumID);
            return;
        }
    }

    // Add a new interceptor to the list of installable interceptors
    if (addInterceptor) {
        try {
            if (newClassname == null) {
                throw new ClassNotFoundException("No classname specified.");
            }
            try {
                // Load the specified class, make sure it's an insance of the interceptor class:
                Class c = ClassUtils.forName(newClassname.trim());
                Object obj = c.newInstance();
                if (obj instanceof MessageInterceptor) {
                    interceptorManager.addInterceptorClass(newClassname.trim());
                }
            }
            catch (Exception e) {
                throw new ClassNotFoundException();
            }
        }
        catch (ClassNotFoundException cnfe) {
            setOneTimeMessage(session, "message",
                "\"" + newClassname + "\" is not a valid classname.");
        }
        response.sendRedirect("interceptors.jsp?forum=" + forumID);
        return;
    }

    // Change the position of a interceptor
    if (changePosition) {
        if (interceptorIndex >= 0) {
            // Get the interceptor at the specified interceptor position
            MessageInterceptor interceptor = interceptorManager.getInterceptor(interceptorIndex);
            // Re-add it based on the "direction" we're doing. First, remove it:
            interceptorManager.removeInterceptor(interceptorIndex);
            if (up) {
                interceptorManager.addInterceptor(interceptorIndex-1, interceptor);
            }
            if (down) {
                interceptorManager.addInterceptor(interceptorIndex+1, interceptor);
            }
            // done, so redirect
            response.sendRedirect("interceptors.jsp?forum=" + forumID);
            return;
        }
    }

    // Number of installed interceptors
    int interceptorCount = interceptorManager.getInterceptorCount();
    // All interceptor classes
    MessageInterceptor[] interceptors = interceptorManager.getAvailableInterceptors();

    if (install && classname != null) {
        try {
            MessageInterceptor newInterceptor = (MessageInterceptor)(ClassUtils.forName(classname)).newInstance();
            interceptorManager.addInterceptor(0, newInterceptor);
            String redirect = "interceptors.jsp";
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
        interceptorManager.removeInterceptor(position);
        String redirect = "interceptors.jsp";
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
        title = "Global Message Interceptors";
    } else {
        title = "Message Interceptors";
    }

    String[][] breadcrumbs = null;
    if (isGlobal) {
        breadcrumbs = new String[][] {
            {"Main", "main.jsp"},
            {title, "interceptors.jsp"}
        };
    }
    else {
        breadcrumbs = new String[][] {
            {"Main", "main.jsp"},
            {"Forums", "forums.jsp"},
            {"Edit Forum", "editForum.jsp?forum="+forumID},
            {title, "interceptors.jsp?forum="+forumID}
        };
    }
%>
<%@ include file="title.jsp" %>

<span>
<font size="-1">
<%  if (isGlobal) { %>
    Interceptors examine messages before they enter the system and can modify or
    reject them. Use the forms below to install and customize global interceptors.
<%  } else { %>
    Interceptors examine messages before they enter the system and can modify or
    reject them. Use the forms below to install and customize local interceptors.
<%  } %>
</font>
</span>

<p>

<script language="JavaScript" type="text/javascript">
var interceptorInfo = new Array(
<%	for (int i=0; i<interceptors.length; i++) {
        try {
            BeanDescriptor descriptor = (Introspector.getBeanInfo(interceptors[i].getClass())).getBeanDescriptor();
%>
    new Array(
        "<%= descriptor.getBeanClass().getName() %>",
        "<%= descriptor.getValue("version") %>",
        "<%= descriptor.getValue("author") %>",
        "<%= StringUtils.replace(descriptor.getShortDescription(), "\"", "\\\"") %>"
    )
<%          if ((interceptors.length-i) > 1) { %>
		,
<%	        }
        } catch (Exception e) {}
    }
%>
);
function properties(theForm) {
    var className = theForm.interceptors.options[theForm.interceptors.selectedIndex].value;
    var selected = 0;
    for (selected=0; selected<interceptorInfo.length; selected++) {
        if (interceptorInfo[selected][0] == className) {
            var version = interceptorInfo[selected][1];
            var author = interceptorInfo[selected][2];
            var description = interceptorInfo[selected][3];
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
    if (interceptorCount > 0) {
%>
<table cellpadding="0" cellspacing="0" border="0">
<tr><td>
    <font size="-1"><b>Current Interceptors</b></font>
    </td>
    <td>
    <a href="#" onclick="helpwin('interceptors','current_interceptors');return false;"
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
    <%  if (interceptorCount > 1) { %>
	<td align="center"><font size="-2" face="verdana"><b>MOVE</b></font></td>
    <%  } %>
	<td align="center"><font size="-2" face="verdana"><b>EDIT</b></font></td>
	<td align="center"><font size="-2" face="verdana"><b>DELETE</b></font></td>
    </tr>
<%  // Loop through all interceptors
    for (int i=0; i<interceptorCount; i++) {
        try {
            MessageInterceptor interceptor = interceptorManager.getInterceptor(i);
            // Descriptor for this interceptor
            BeanDescriptor descriptor = (Introspector.getBeanInfo(interceptor.getClass())).getBeanDescriptor();
            // Properties for this interceptor
            PropertyDescriptor[] descriptors = BeanUtils.getPropertyDescriptors(interceptor.getClass());
            // Version of this interceptor
            String version = (String)descriptor.getValue("version");
            // Description of this interceptor
            String description = StringUtils.escapeHTMLTags(descriptor.getShortDescription());
%>
    <tr bgcolor="#ffffff">
        <td><font size="-1"><%= (i+1) %></font></td>
        <td nowrap><font size="-1"><%= descriptor.getDisplayName() %></font></td>
        <td><font size="-1"><%= (description!=null)?description:"&nbsp;" %></font></td>
        <%  if (interceptorCount > 1) { %>
        <td>
            <%  if ((i+1)<interceptorCount) { %>
                <a href="interceptors.jsp?forum=<%= forumID %>&changePos=true&down=true&interceptorIndex=<%= i %>"
                ><img src="images/arrow_down.gif" width="13" height="9" alt="Move this interceptor down." border="0"></a>
            <%  } else { %>
                <img src="images/blank.gif" width="13" height="9" border="0">
            <%  } %>

            <%  if (i != 0) { %>
                <a href="interceptors.jsp?forum=<%= forumID %>&changePos=true&up=true&interceptorIndex=<%= i %>"
                ><img src="images/arrow_up.gif" width="13" height="9" alt="Move this interceptor up." border="0"></a>
            <%  } else { %>
                <img src="images/blank.gif" width="13" height="9" border="0">
            <%  } %>
        </td>
        <%  } %>
        <td align="center">
            <a href="interceptors.jsp?edit=true&forum=<%= forumID %>&pos=<%= i %>"
            ><img src="images/button_edit.gif" width="17" height="17" alt="Edit the properties of this interceptor" border="0"
            ></a>
        </td>
        <td align="center">
            <a href="interceptors.jsp?remove=true&forum=<%= forumID %>&pos=<%= i %>"
            ><img src="images/button_delete.gif" width="17" height="17" alt="Delete this interceptor" border="0"
            ></a>
        </td>
    </tr>
<%  if (position == i && edit) { %>
    <form action="interceptors.jsp" method="post">
    <input type="hidden" name="forum" value="<%= forumID %>">
    <input type="hidden" name="saveProperties" value="true">
    <input type="hidden" name="interceptorIndex" value="<%= i %>">
    <tr bgcolor="#ffffff">
        <td>&nbsp;</td>
        <td colspan="<%= (interceptorCount > 1)?"5":"4" %>">
            <table cellpadding="2" cellspacing="0" border="0" width="100%">
            <%  int color = 1;
                for (int j=0; j<descriptors.length; j++) {
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
                            <%= getHTML(interceptor, descriptors[j]) %>
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
                        <td width="10%" nowrap>
                            <%= getHTML(interceptor, descriptors[j]) %>
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
<%  } %>
<%      } catch (Exception e) { }
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

<form action="interceptors.jsp" method="post">
<input type="hidden" name="forum" value="<%= forumID %>">
<input type="hidden" name="install" value="true">

<span class="jive-install-interceptor">

<table cellpadding="0" cellspacing="0" border="0">
<tr><td>
    <font size="-1"><b>Install Interceptor</b></font>
    </td>
    <td>
    <a href="#" onclick="helpwin('interceptors','install_interceptor');return false;"
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
                <font size="-2" face="verdana"><b>AVAILABLE INTERCEPTORS</b></font>
            </td>
        </tr>
        <tr bgcolor="#ffffff">
            <td>
                <table cellpadding="1" cellspacing="0" border="0">
                <tr>
                    <td width="48%" valign="top">
                        <font size="-1">
                        <select size="8" name="interceptors" onchange="properties(this.form);">
                        <%  for (int i=0; i<interceptors.length; i++) {
                                if (!isInstalledInterceptor(interceptorManager, interceptors[i])) {
                                    BeanDescriptor descriptor
                                            = (Introspector.getBeanInfo(interceptors[i].getClass())).getBeanDescriptor();
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

<form action="interceptors.jsp">
<input type="hidden" name="addInterceptor" value="true">
<input type="hidden" name="forum" value="<%= forumID %>">
<table cellpadding="0" cellspacing="0" border="0">
<tr><td>
    <font size="-1"><b>Add Interceptor Class</b></font>
    </td>
    <td>
    <a href="#" onclick="helpwin('interceptors','add_interceptor_class');return false;"
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
    	<td><font size="-1"><input type="submit" value="Add Interceptor"></font></td>
    </tr>
    </table>
</ul>
</form>

<p>


</body>
</html>


