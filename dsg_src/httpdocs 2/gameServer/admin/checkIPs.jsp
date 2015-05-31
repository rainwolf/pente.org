<html>
<head><title>Check IPs</title></head>
<body>

<h3>Check IPs</h3>

<form name="check" action="viewIPs.jsp" method="post">
  <input type="hidden" name="action" value="check">
  <table border="0" cellspacing="0" cellpadding="0">
    <tr>
        <td valign="top">View IPs for Player:&nbsp;&nbsp;</td>
        <td><input type="text" name="name"></td>
    </tr>
    <tr>
        <td valign="top" align="center" colspan="2">-- Or --</td>
    </tr>
    <tr>
        <td valign="top">View Players for IPs:&nbsp;&nbsp;</td>
        <td><input type="text" name="ip"></td>
    </tr>

    <tr>
        <td>&nbsp;</td>
        <td valign="top">
          <input type="submit" value="Check IPs">
        </td>
      </tr>
  </table>
</form>

<a href=".">Back to admin</a>

</body>
</html>