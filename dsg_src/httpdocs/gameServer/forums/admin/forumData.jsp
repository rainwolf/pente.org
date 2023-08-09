<%
   /**
    *	$RCSfile: forumData.jsp,v $
    *	$Revision: 1.2 $
    *	$Date: 2002/10/02 01:20:37 $
    */
%>

<%@ page import="java.util.*,
                 java.text.SimpleDateFormat,
                 com.jivesoftware.forum.*,
                 com.jivesoftware.forum.util.*,
                 com.jivesoftware.util.ParamUtils"
         errorPage="error.jsp"
%>

<%@ include file="global.jsp" %>

<% ////////////////////
   // Security check

   // make sure the user is authorized to administer users:

%>

<% ////////////////////
   // get parameters

   long sourceID = ParamUtils.getLongParameter(request, "source", -1L);
   long targetID = ParamUtils.getLongParameter(request, "target", -1L);
   String newName = ParamUtils.getParameter(request, "forumName");
   boolean merge = ParamUtils.getBooleanParameter(request, "merge");
   boolean confirm = ParamUtils.getBooleanParameter(request, "confirmed");

   boolean equalsError = (sourceID == targetID);
   boolean noForumSpecified = (sourceID == -1 || targetID == -1);
   boolean noNameSpecified = (newName == null);
   boolean errors = (equalsError || noForumSpecified || noNameSpecified);
%>

<% ////////////////
   // Merge forums, if we've chosen a source and target, have confirmed, and there are no errors
   if (merge && confirm && !errors) {

      // Get Forum objects for the source and target forums.
      Forum sourceForum = forumFactory.getForum(sourceID);
      Forum targetForum = forumFactory.getForum(targetID);
      forumFactory.mergeForums(targetForum, sourceForum);
      if (newName != null) {
         targetForum.setName(newName);
      }
      // merge the forums
      // while(sourceForum.getMessageCount() > 0)  // merge the forums until the sourceforum has 0 messages.
      // {
      //	forumFactory.mergeForums(sourceForum, targetForum);
      // }

      // now, the forum is empty, delete sourceForum
      // forumFactory.deleteForum(sourceForum);

      // change the name of the merged forum.
      // targetForum.setName(newName);
   }
%>

<html>
<head>
   <title></title>
   <link rel="stylesheet" href="style/global.css">

   <script language="JavaScript">

      function changeName() {

         document.mergeForums.forumName.value = document.mergeForums.source.options[document.mergeForums.source.selectedIndex].text;
      }

   </script>
</head>

<body bgcolor="#ffffff" text="#000000" link="#0000ff" vlink="#800080" alink="#ff0000" onLoad=changeName()>

   <%	///////////////////////
	// pageTitleInfo variable (used by include/pageTitle.jsp)
	String[] pageTitleInfo = { "Forums : Merge Forum Data" };
%>
   <%	///////////////////
	// pageTitle include
%>

<p>

      <%
///////////
// if we haven't chosen the two forums to merge.

if(!merge || (merge && errors)) {
%>
<form name="mergeForums" action="forumData.jsp" method="post">
   <input type=hidden name="merge" value="true">

   <% if (merge && equalsError) {
   %> <span class="errorText">
		You must choose 2 distinct Forums.<p>
		</span>
   <% } %>

   <% if (merge && noForumSpecified) {
   %> <span class="errorText">
		You must select a source forum and a target forum<p>
		</span>
   <% } %>

   <% if (merge && noNameSpecified) {
   %> <span class="errorText">
		You must enter a name for the merged Forum.<p>
		</span>
   <% } %>

   Choose two forums to Merge:<p>

   <table border=0 cellpadding=2 cellspacing=2>
      <tr>
         <td><select name="source" onClick=changeName()>
            <%
               Iterator forumIterator = forumFactory.getRootForumCategory().getRecursiveForums();
               while (forumIterator.hasNext()) {
                  Forum tempForum = (Forum) forumIterator.next();
            %>
            <option value="<%= tempForum.getID() %>"><%= tempForum.getName() %>
                  <%
			}
	            %>
         </select>
         </td>
         <td> &nbsp; &nbsp; &nbsp; &nbsp; &nbsp;</td>
         <td><select name="target">
            <%
               forumIterator = forumFactory.getRootForumCategory().getRecursiveForums();
               while (forumIterator.hasNext()) {
                  Forum tempForum = (Forum) forumIterator.next();
            %>
            <option value="<%= tempForum.getID() %>"><%= tempForum.getName() %>
                  <%
			}
		    %>
         </select>
         </td>
      </tr>
   </table>

   <p>
      Name of the combined Forum:
   <p>
      <input type=text name="forumName">
   <p>

      <input type=submit value="Merge Forums">

</form>
   <% } %>

   <%	//////////////////
	// We have a valid input, confirm the merge.
	if(merge && !confirm && !errors) {
%>
<ul>
   <table border=0 cellpadding=0 cellspacing=0 width=500>
      <tr>
         <td>Warning: This operation will combine Forums
            <u><%= forumFactory.getForum(sourceID).getName() %>
            </u> and
            <u><%= forumFactory.getForum(targetID).getName() %>
            </u>, creating
            a new Forum named <u><%= newName %>
            </u>.
            This is most likely an irreversible operation. Are you sure you want
            to do this?
         </td>
      </tr>
   </table>
</ul>
<p>

<form action="forumData.jsp" method="post">
   <input type=hidden name="confirmed" value="true">
   <input type=hidden name="merge" value="true">
   <input type=hidden name="source" value="<%= sourceID %>">
   <input type=hidden name="target" value="<%= targetID %>">
   <input type=hidden name="forumName" value="<%= newName %>">

   <input type=submit value="  Yes   ">
   <input type=submit name="cancel" value=" Cancel " style="font-weight:bold;"
          onclick="location.href='forumData.jsp';return false;">
</form>

   <%	} %>

   <%	/////////////
	// Completion message
	if(merge && confirm && !errors) {
%>

<u><%= forumFactory.getForum(sourceID).getName() %>
</u> has been successfully merged with
<u><%= forumFactory.getForum(targetID).getName() %>
</u>.
   <%
	}
%>

<body>
</html>

