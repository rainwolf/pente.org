<%--
  - $RCSfile: index.jsp,v $
  - $Revision: 1.5.4.2 $
  - $Date: 2003/03/28 18:12:00 $
  -
  - Copyright (C) 2002-2003 Jive Software. All rights reserved.
  -
  - This software is the proprietary information of Jive Software. Use is subject to license terms.
--%>

<%@ page import="java.io.*,
                 java.lang.reflect.*,
                 java.util.*"
%>

<%	// Jive installation check. Load the Jive classes by reflection because
    // the Jive classes may or may not be installed.

	boolean doSetup = false;
    boolean configError = false;
    // try loading the JiveGlobals class:
    try {
        Class jiveGlobals = Class.forName("com.jivesoftware.base.JiveGlobals");
        Class[] params = { (new String()).getClass() };
        Method getJiveProperty = jiveGlobals.getMethod("getJiveProperty", params);
        // If the method doesn't exist, something is clearly wrong so trigger setup:
        if (getJiveProperty == null) {
            doSetup = true;
        }
        else {
            // Otherwise, look for the "setup=true" property by
            // calling JiveGlobals.getJiveProperty("setup")
            String[] args = {"setup"};
            Object setupVal = getJiveProperty.invoke(null,args);
            if (setupVal == null || !"true".equals((String)setupVal)) {
                doSetup = true;
            }
        }
    }
    catch (Throwable t) {
        // catch run time errors, like class loader issues (usually happens
        // if the jiveHome path is configured incorrectly, etc)
        configError = true;
        doSetup = true;
    }

	// If we need to run the Jive setup tool, give the user a link to the setup
    // tool.
	if (doSetup || configError) { %>
		<html>
		<head>
		<title>Jive Forums Lite - Admin</title>
		</head>
		<body>
        <font face="arial,helvetica,sans-serif" color="#333333">
        <b>Jive Forums Lite - Admin</b>
        </font>
        <ul>
            <font size="-1" face="arial,helvetica,sans-serif">
        <%  if (doSetup) { %>
		    Jive Forums is not properly configured or is being run for the first time.
            <p>
            <a href="setup/index.jsp">Proceed to the setup tool</a>
        <%  } %>

        <%  if (configError) { %>
		    A general exception occurred. This could be because Jive is not
            setup correctly, specifically, Jive might not be able to find
            your <font color="#336600">jiveHome</font> directory as indicated in the
            <font color="#336600">jive_init.xml</font> file. Please consult the installation
            documentation before you try to correct the
            problem. Remember, you will need to restart your appserver if you
            make changes to the <font color="#336600">jive_init.xml</font> file.
        <%  } %>
            </font>
        </ul>
		</body>
		</html>
<%		// for some reason, we have to call flush.. some app servers won't
		// display the above html w/o flushing the stream
		out.flush();
		return;
	}

    // Check to see if we've already logged into this tool.
	com.jivesoftware.base.AuthToken authToken = (com.jivesoftware.base.AuthToken)session.getAttribute("jive.admin.authToken");
	if (authToken == null) {
		response.sendRedirect("login.jsp");
		return;
	}

    // License check
    com.jivesoftware.base.LicenseManager.validateLicense("Jive Forums Lite","3.0");

    // logout if requested:
	if ("true".equals(request.getParameter("logout"))) {
		try {
            List removables = new ArrayList(10);
            for (Enumeration a=session.getAttributeNames(); a.hasMoreElements();)
            {
                String attribName = (String)a.nextElement();
                if (attribName.startsWith("jive.admin.")
                        || attribName.startsWith("admin."))
                {
                    removables.add(attribName);
                }
            }
            for (int i=0; i<removables.size(); i++) {
                session.removeAttribute((String)removables.get(i));
            }
		}
		catch (IllegalStateException ignored) { // if session is already invalid
		}
		finally {
			response.sendRedirect("index.jsp");
			return;
		}
	}
%>

<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN">

<html>
<head>
    <title>Jive Forums 3 Admin Tool</title>
</head>

    <frameset rows="110,*" framespacing="0" border="0">
        <frame name="header" src="tabs.jsp" marginwidth="5" marginheight="5" scrolling="no" frameborder="0" noresize>
        <frameset cols="180,1,*" framespacing="0" border="0">
            <frame name="sidebar" src="sidebar.jsp" marginwidth="0" marginheight="0" scrolling="yes" frameborder="0">
            <frame name="spacer" src="spacer.html" marginwidth="0" marginheight="0" scrolling="auto" frameborder="0">
            <frame name="main" src="main.jsp" marginwidth="10" marginheight="0" scrolling="auto" frameborder="0">
        </frameset>
    </frameset>

</html>


