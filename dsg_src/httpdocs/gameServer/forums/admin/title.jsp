<%
/**
 * $RCSfile: title.jsp,v $
 * $Revision: 1.3 $
 * $Date: 2003/01/07 00:42:39 $
 */
%>

<span class="jive-admin-page-title">
<table cellpadding="2"  cellspacing="0" border="0" width="100%">
<tr>
    <td>
        <%= title %>
    </td>
    <td align="right">
        <span class="jive-breadcrumbs">
        <%  if (breadcrumbs != null) { %>

            <%  for (int _i=0; _i<breadcrumbs.length; _i++) { %>

                <a href="<%= breadcrumbs[_i][1] %>"><%= breadcrumbs[_i][0] %></a>

            <%      if ((_i+1) < breadcrumbs.length) { %>

                &raquo;

            <%      }
                }
            %>

        <%  } %>
        </span>
    </td>
</tr>
</table>
</span>

<br>
