 <div id="login">
    <div class="head">
        <h6>User Login</h6>
    </div>
    <form name="loginform" id="loginform" action="/gameServer/index.jsp" method="post">

    <table border="0" colspacing="1" colpadding="1">
        <tr>
            <td><p>Name:</p></td>
            <td><input type="text" id="name2" name="name2" size="10" maxlength="10"></td>
        </tr>
        <tr>
            <td><p>Password:</p></td>
            <td><input type="password" id="password2" name="password2" size="10" maxlength="16"></td>
        </tr>
        <tr>
            <td>&nbsp;</td>
            <td><input type="submit" value="Login"></td>
        </tr>
        <tr>
            <td colspan="2" align="center"><h6><a style="font-weight:normal" href="/gameServer/forgotpassword.jsp">Forgot password?</a></h6></td>
        </tr>
    </table>
    </form>
 </div>