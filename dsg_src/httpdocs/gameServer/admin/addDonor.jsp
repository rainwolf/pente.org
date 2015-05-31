<%@ page import="org.pente.database.*, 
                 org.pente.gameServer.core.*, 
				 org.pente.gameServer.server.*,
				 java.sql.*" %>

<html>
<body>

<%
    String name = request.getParameter("name");
    if (name != null && !name.equals("")) {

	    DBHandler dbHandler = null;
	    DSGPlayerStorer dsgPlayerStorer = null;

	    Connection con = null;
	    PreparedStatement stmt = null;
	    ResultSet result = null;


	    try {
			dbHandler = (DBHandler) application.getAttribute(DBHandler.class.getName());
			dsgPlayerStorer = (DSGPlayerStorer) application.getAttribute(DSGPlayerStorer.class.getName());
			con = dbHandler.getConnection();

	        DSGPlayerData data = dsgPlayerStorer.loadPlayer(name);
			if (data == null) {
				%> <b>Player data not found</b><br><br> <%
			}
			else {
				// update player color to green
				if (!data.hasPlayerDonated()) {
    				data.setNameColorRGB(Integer.parseInt(request.getParameter("color")));
    				dsgPlayerStorer.updatePlayer(data);
        }

				String addColorOnlyStr = request.getParameter("addColorOnly");
			    boolean addColorOnly = addColorOnlyStr != null && !addColorOnlyStr.equals("");
				if (!addColorOnly) {
					// add player to list of donors
					stmt = con.prepareStatement("insert into dsg_donation " +
						"values(?, ?, sysdate(), ?)");
					stmt.setLong(1, data.getPlayerID());
					stmt.setString(2, request.getParameter("amount"));
					stmt.setString(3, request.getParameter("source"));
					stmt.executeUpdate();

					// add player to donor email list?

					// email player thank you message
					SendMail2.sendMail(
						"rainwolf", "rainwolf@submanifold.be",
						name, request.getParameter("email"),
						"rainwolf", "rainwolf@submanifold.be",
						"Pente.org Donation",
						request.getParameter("emailMessage"),
						false,
						null);

					%> <b>Add donor successful</b><br><br> <%
				} else {
					%> <b>Add color successful</b><br><br> <%
				}
			}
	    } catch (Throwable t) {
			throw t;
		} finally {
			if (result != null) {
			    result.close();
			}
			if (stmt != null) {
			    stmt.close();
			}
			if (con != null) {
			    dbHandler.freeConnection(con);
			}
		}
	}
%>


<form name="addDonor" method="get" action="addDonor.jsp">

Donor name: <input type="text" name="name"><br>
Donor email: <input type="text" name="email"><br>
Add color only (2nd name for 1 player?): <input type="checkbox" name="addColorOnly"><br>
Donation amount: <input type="text" name="amount"><br>
Payment type: <select name="source">
<option value="P">Paypal
<option value="C">Check
<option value="D">Cash
</select><br>
Initial color: <select name="color">
<%!
 		int rgb[] = { -16751616, -12550016, -16776961, -16716545, -8388353, -8355585, -1175063, -4998855, -32768, -16777216 };
		String colors[] = { "Dark Green", "Gray Green", "Royal Blue", "Light Blue", "Dark Purple", "Light Purple", "Pink", "Gold", "Orange", "Black" };
%>
<%
		for (int i = 0; i < rgb.length; i++) { 
  			String selected = (rgb[i] == -16751616) ? " selected" : "";
%>
        	<option value="<%= rgb[i] %>"<%= selected %>><%= colors[i] %>
<%  	} %>
</select><br>
Email message to donor:
<textarea name="emailMessage" cols="80" rows="10">Hi there,
Thank you for your donation, it will really help out Pente.org!

I have updated your player profile so you can now change the color of your name, upload a picture and add a note to your profile whenever you like at http://pente.org/gameServer/myprofile/donor.

Thanks again for the donation and enjoy the extra features!
-rainwolf</textarea><br>
<br>
<input type="submit" value="submit">

</form>

</body>
</html>

