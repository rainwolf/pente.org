<%
/**
 *	$RCSfile: editCat.jsp,v $
 *	$Revision: 1.3 $
 *	$Date: 2002/11/22 22:35:08 $
 */
%>

<%@ page import="java.util.*,
                     java.text.*,
                     com.jivesoftware.forum.*,
                     com.jivesoftware.forum.util.*,
                 com.jivesoftware.util.ParamUtils"
    errorPage="error.jsp"
%>

<%@ include file="global.jsp" %>

<%! // Global vars, methods, etc...

    // Date formatter for creation date/modified date
    SimpleDateFormat dateFormatter = new SimpleDateFormat("");
%>

<%	// Permission check
    if (!isSystemAdmin && !isCatAdmin) {
        throw new UnauthorizedException("You don't have admin privileges to perform this operation.");
    }

    // Get parameters
    long categoryID = ParamUtils.getLongParameter(request,"cat",-1L);
    String name = ParamUtils.getParameter(request,"name");
    String description = ParamUtils.getParameter(request,"description",true);
    boolean saveName = ParamUtils.getBooleanParameter(request,"saveName");

    // Load up the forum specified
    ForumCategory category = forumFactory.getForumCategory(categoryID);

    // Put the forum in the session (is needed by the sidebar)
    session.setAttribute("admin.sidebar.forums.currentCategoryID", ""+categoryID);

    // save the name & description if requested
    if (saveName) {
        if (name != null) {
            category.setName(name);
        }
        if (description != null) {
            category.setDescription(description);
        }
        setOneTimeMessage(session,"message","Changes saved.");
        response.sendRedirect("editCat.jsp?cat="+categoryID);
        return;
    }

    name = category.getName();
    description = category.getDescription();
%>

<%  // special onload command to load the sidebar
    onload = " onload=\"parent.frames['sidebar'].location.href='sidebar.jsp?sidebar=forum';\"";
%>
<%@ include file="header.jsp" %>

<p>

<%  // Title of this page and breadcrumbs
    String title = "Category Settings";
    String[][] breadcrumbs = {
        {"Main", "main.jsp"},
        {"Categories &amp; Forums", "forums.jsp?cat="+categoryID},
        {title, "editCat.jsp?cat="+categoryID}
    };
%>
<%@ include file="title.jsp" %>

<font size="-1">
Set the name and description of a category below.
<a href="editCategoryProps.jsp?cat=<%= categoryID %>">Edit Category Extended Properties</a>
</font>

<p>

<font size="-1"><b>General Information</b></font>
<ul>
    <table bgcolor="<%= tblBorderColor %>" cellpadding="0" cellspacing="0" border="0" width="">
    <tr><td>
    <table bgcolor="<%= tblBorderColor %>" cellpadding="3" cellspacing="1" border="0" width="100%">
    <tr bgcolor="#eeeeee">
        <td align="center"><font size="-2" face="verdana"><b>CATEGORY ID</b></font></td>
        <td align="center"><font size="-2" face="verdana"><b>FORUMS</b></font></td>
        <td align="center"><font size="-2" face="verdana"><b>SUB-CATEGORIES</b></font></td>
        <td align="center"><font size="-2" face="verdana"><b>CREATED ON</b></font></td>
        <td align="center"><font size="-2" face="verdana"><b>LAST MODIFIED</b></font></td>
    </tr>
    <tr bgcolor="#ffffff">
        <td align="center" bgcolor="#eeeeee"><font size="-1"><%= category.getID() %></font></td>
        <td align="center"><font size="-1"><%= category.getForumCount() %></font></td>
        <td align="center"><font size="-1"><%= category.getCategoryCount() %></font></td>
        <td align="center"><font size="-1">&nbsp;<%= JiveGlobals.formatDateTime(category.getCreationDate()) %>&nbsp;</font></td>
        <td align="center"><font size="-1">&nbsp;<%= JiveGlobals.formatDateTime(category.getModificationDate()) %>&nbsp;</font></td>
    </tr>
    </table>
    </td></tr>
    </table>
</ul>

<font size="-1"><b>Edit Name and Description</b></font>
<ul>
    <font size="-1">
    Change the name or description of this forum using the form below.
    </font>
    <p>
    <%  String message = getOneTimeMessage(session,"message");
            if (message != null) {
    %>
        <font size="-1" color="#339900"><b><i><%= message %></i></b></font>
        <p>
    <%  } %>
    <form action="editCat.jsp" method="post">
    <input type="hidden" name="saveName" value="true">
    <input type="hidden" name="cat" value="<%= categoryID %>">
    <table cellpadding="2" cellspacing="0" border="0">
    <tr>
    	<td><font size="-1">Name:</font></td>
    	<td><input type="text" name="name" size="40" maxlength="100" value="<%= (name!=null)?name:"" %>"></td>
    </tr>
    <tr>
    	<td valign="top"><font size="-1">Description:</font></td>
    	<td><textarea name="description" cols="40" rows="5" wrap="virtual"><%= (description!=null)?description:"" %></textarea></td>
    </tr>
    <tr>
    	<td>&nbsp;</td>
    	<td><input type="submit" value="Save Changes"></td>
    </tr>
    </table>
    </form>
</ul>

<form action="forums.jsp">
<input type="hidden" name="cat" value="<%= categoryID %>">
<br><hr size="1">
<center>
<input type="submit" value="Back to Category Listing">
</center>
</form>

<p>

<%@ include file="footer.jsp" %>
