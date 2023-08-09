<%
   /**
    *	$RCSfile: createCat.jsp,v $
    *	$Revision: 1.2 $
    *	$Date: 2002/10/17 20:10:34 $
    */
%>

<%@ page import="java.util.*,
                 com.jivesoftware.forum.*,
                 com.jivesoftware.forum.util.*,
                 com.jivesoftware.util.ParamUtils"
         errorPage="error.jsp"
%>

<%@ include file="global.jsp" %>

<% // Permission check
   if (!isSystemAdmin && !isCatAdmin) {
      throw new UnauthorizedException("You don't have admin privileges to perform this operation.");
   }

   // Remove the forum in the session (if we come to this page, the sidebar
   // shouldn't show the specific forum options).
   session.removeAttribute("admin.sidebar.forums.currentForumID");

   // Get parameters
   boolean create = request.getParameter("create") != null;
   boolean cancel = request.getParameter("cancel") != null;
   String name = ParamUtils.getParameter(request, "name");
   String description = ParamUtils.getParameter(request, "description");
   long categoryID = ParamUtils.getLongParameter(request, "cat", -1L);

   // Cancel, if requested
   if (cancel) {
      response.sendRedirect("forums.jsp?cat=" + categoryID);
      return;
   }

   // Load the forum category if requested
   ForumCategory category = null;
   if (categoryID != -1L) {
      category = forumFactory.getForumCategory(categoryID);
   } else {
      category = forumFactory.getRootForumCategory();
   }
   // Make sure the user has cat admin priv on this category.
   if (!isSystemAdmin && !category.isAuthorized(ForumPermissions.FORUM_CATEGORY_ADMIN)) {
      throw new UnauthorizedException("You don't have admin privileges to perform this operation.");
   }

   // Check for errors
   boolean errors = false;
   if (create) {
      if (name == null) {
         errors = true;
      }
   }

   // Create a forum if requested
   if (create && !errors) {

      // Create this forum in the category
      ForumCategory newCat = category.createCategory(name, description);

      // redirect back to the forums page
      response.sendRedirect("forums.jsp?cat=" + newCat.getParentCategory().getID());
      return;
   }
%>

<% // special onload command to load the sidebar
   onload = " onload=\"parent.frames['sidebar'].location.href='sidebar.jsp?sidebar=forum';\"";
%>
<%@ include file="header.jsp" %>

<p>

      <%  // Title of this page and breadcrumbs
    String title = "Create a New Category";
    String[][] breadcrumbs = {
        {"Main", "main.jsp"},
        {"Forums", "forums.jsp"},
        {"Create Forum", "createCat.jsp?cat=" + category.getID()}
    };
%>
   <%@ include file="title.jsp" %>

   <font size="-1">
      Use the form below to create a new subcategory. Note, the description of
      the category is optional.
   </font>

<p>

      <%	// error messages
	if(errors) {
%>
   <font color="#ff0000" size="-1">
      Error creating category. Please make sure you've entered a category name.
   </font>
<p>
      <%	} %>

<form action="createCat.jsp" method="post" name="createForm">
   <input type="hidden" name="cat" value="<%= category.getID() %>">

   <font size="-1"><b>Category Properties</b></font>
   <ul>
      <table cellpadding="2" cellspacing="0" border="0">
         <tr>
            <td><font size="-1">Create In Category:</font></td>
            <td>
               <font size="-1">
                  <% if (category == null || category.getID() == 1L) { %>
                  Root Category
                  <% } else { %>
                  <%= category.getName() %>
                  <% } %>
               </font>
            </td>
         </tr>
         <tr>
            <td><font size="-1">Category Name:</font></td>
            <td>
               <input type="text" name="name" size="40" maxlength="255" value="<%= (name!=null)?name:"" %>">
            </td>
         </tr>
         <tr>
            <td valign="top"><font size="-1">Description (optional):</font></td>
            <td>
               <textarea name="description" cols="35" rows="10"
                         wrap="virtual"><%= (description != null) ? description : "" %></textarea>
            </td>
         </tr>
         <td>

      </table>
   </ul>

   <input type="submit" name="create" value="Create Category">
   <input type="submit" name="cancel" value="Cancel">
</form>

<script language="JavaScript" type="text/javascript">
   <!--
   document.createForm.name.focus();
   //-->
</script>

<%@ include file="footer.jsp" %>
