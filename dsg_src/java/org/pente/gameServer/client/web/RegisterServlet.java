package org.pente.gameServer.client.web;

import java.io.*;

import javax.servlet.*;
import javax.servlet.http.*;

import org.apache.log4j.*;

import com.jivesoftware.base.*;
import com.jivesoftware.forum.*;

import org.pente.gameServer.core.*;
import org.pente.gameServer.server.*;
import org.pente.jive.*;

public class RegisterServlet extends HttpServlet {

    private static final Category log4j =
            Category.getInstance(RegisterServlet.class.getName());

    private DSGPlayerStorer dsgPlayerStorer;
    private RegisterHandler registerHandler;
    private String registrationEmail;
    private MySQLDSGReturnEmailStorer returnEmailStorer;

    private boolean emailEnabled;

    private PasswordHelper passwordHelper;

    private static final DSGAuthToken adminToken =
            new DSGAuthToken(22000000000002L);

    public void init(ServletConfig config) throws ServletException {

        super.init(config);

        try {

            ServletContext ctx = config.getServletContext();

            returnEmailStorer = (MySQLDSGReturnEmailStorer)
                    ctx.getAttribute(MySQLDSGReturnEmailStorer.class.getName());

            registrationEmail = config.getInitParameter("registrationEmail");

            dsgPlayerStorer = (DSGPlayerStorer) ctx.getAttribute(DSGPlayerStorer.class.getName());
            registerHandler = new DSGPlayerStorerRegisterHandler(dsgPlayerStorer);

            emailEnabled = ((Boolean) ctx.getAttribute("emailEnabled")).booleanValue();

            passwordHelper = (PasswordHelper) ctx.getAttribute(
                    PasswordHelper.class.getName());

        } catch (Throwable t) {
            log4j.error("Problem in init()", t);
        }
    }

    public void doGet(HttpServletRequest request,
                      HttpServletResponse response)
            throws ServletException, IOException {
        doPost(request, response);
    }

    public void doPost(HttpServletRequest request,
                       HttpServletResponse response)
            throws ServletException, IOException {

        String redirectPage = "/join.jsp";
        String registrationError = null;

        String name = request.getParameter("registerName");
        if (name == null) {
            name = request.getParameter("name");
        }
        if (name != null) {
            name = name.trim().toLowerCase();
        }
        String password = request.getParameter("registerPassword");
        if (password != null) {
            password = password.trim();
        }
        String passwordConfirm = request.getParameter("registerPasswordConfirm");
        if (passwordConfirm != null) {
            passwordConfirm = passwordConfirm.trim();
        }
        String email = request.getParameter("registerEmail");
        if (email != null) {
            email = email.replace(" ", "");
        }
        String emailVisibleStr = request.getParameter("registerEmailVisible");
        boolean emailVisible = false;
        if (emailVisibleStr != null) {
            emailVisible = emailVisibleStr.trim().equals("Y");
        }
        String emailUpdatesStr = request.getParameter("registerEmailUpdates");
        boolean emailUpdates = false;
        if (emailUpdatesStr != null) {
            emailUpdates = emailUpdatesStr.trim().equals("Y");
        }
        String location = request.getParameter("registerLocation");
        if (location != null) {
            location = location.trim();
        }
        String timezone = request.getParameter("timezone");
        if (timezone != null) {
            timezone = timezone.trim();
        } else {
            timezone = "America/New_York";
        }
        String sexStr = request.getParameter("registerSex");
        char sex = DSGPlayerData.UNKNOWN;
        if (sexStr != null && !sexStr.isEmpty()) {
            sex = sexStr.trim().charAt(0);
            if (sex != DSGPlayerData.MALE &&
                    sex != DSGPlayerData.FEMALE) {
                sex = DSGPlayerData.UNKNOWN;
            }
        }
        String ageStr = request.getParameter("registerAge");
        int age = 0;
        if (ageStr != null && !ageStr.isEmpty()) {
            try {
                age = Integer.parseInt(ageStr);
            } catch (NumberFormatException ex) {
            }
        }
        String homepage = request.getParameter("registerHomepage");
        if (homepage != null) {
            homepage = ChangeProfileServlet.cleanHomepage(homepage.trim());
        }

        log4j.info("Register: name=" + name + ", email=" + email +
                ", emailVisible=" + emailVisible +
                ", location=" + location + ", sex=" + sex + ", age=" + age);

        if (name == null || password == null || passwordConfirm == null || email == null ||
                name.equals("") || password.equals("") || passwordConfirm.equals("") || email.equals("")) {
            registrationError = "Please fill in all fields.";
        } else if (!password.equals(passwordConfirm)) {
            registrationError = "Passwords don't match.";
        } else if (location != null && location.length() > 100) {
            registrationError = "Location must be less than 101 characters";
        } else if (homepage != null && homepage.length() > 100) {
            registrationError = "Home page must be less than 101 characters";
        } else if (request.getParameter("agreePolicy") == null ||
                !request.getParameter("agreePolicy").equals("Y")) {
            registrationError = "You must read and agree to Pente.org's Policy for " +
                    "Playing Rated Games";
        } else {
            int registerResult = RegisterHandler.SUCCESS;
            String encryptedPassword = passwordHelper.encrypt(password);

            // preserve the dweebo name!
            if (name.indexOf("dwe") >= 0) {
                registerResult = RegisterHandler.ERROR_NAME_TAKEN;
            } else if (name.startsWith("mm")) {
                registerResult = RegisterHandler.ERROR_NAME_TAKEN;
            } else if (name.indexOf("mm_ai") >= 0) {
                registerResult = RegisterHandler.ERROR_NAME_TAKEN;
            } else if (name.indexOf("admin") >= 0) {
                registerResult = RegisterHandler.ERROR_NAME_TAKEN;
            } else if (name.indexOf("guest") >= 0) {
                registerResult = RegisterHandler.ERROR_NAME_TAKEN;
            }

            if (registerResult == RegisterHandler.SUCCESS) {
                registerResult = registerHandler.register(
                        name, password, encryptedPassword, email, emailVisible, emailUpdates,
                        location, timezone, sex, age, homepage);

            }

            switch (registerResult) {
                case RegisterHandler.ERROR_INVALID_DATA:
                    registrationError = "Invalid registration data, please review instructions and try again.";
                    break;
                case RegisterHandler.ERROR_NAME_TAKEN:
                    registrationError = "Requested name " + name + " is already taken, please choose another.";
                    break;
                case RegisterHandler.ERROR_UNKNOWN:
                    registrationError = "Database error, please try again later.";
                    break;
                case RegisterHandler.SUCCESS:
                    redirectPage = "/gameServer/index.jsp";

                    LoginCookieHandler loginCookieHandler = new LoginCookieHandler();
                    loginCookieHandler.setName(name);
                    loginCookieHandler.setPassword(encryptedPassword);
                    loginCookieHandler.setCookie(request, response);

                    request.setAttribute("name", name);
                    request.setAttribute("password", encryptedPassword);
                    request.setAttribute("email", email);
                    HttpSession session = request.getSession(true);
                    session.setAttribute("name", name);
                    session.setAttribute("password", password);
                    session.setAttribute("ip", request.getRemoteAddr());

                    try {
                        ForumFactory forumFactory = ForumFactory.getInstance(
                                adminToken);
                        UserManager userManager = forumFactory.getUserManager();
                        User newUser = userManager.getUser(name);

                        // set default properties for forums
                        newUser.setProperty("jiveThreadRange", "15");
                        newUser.setProperty("jiveTimeZoneID", "America/New_York");
                        newUser.setProperty("jiveAutoWatchNewTopics", "true");
                        newUser.setProperty("jiveAutoWatchReplies", "true");
                        newUser.setProperty("jiveAutoAddEmailWatch", "true");

                        // add watch for news forum
                        if (emailUpdates) {
                            WatchManager watchManager = forumFactory.getWatchManager();
                            Forum newsForum = forumFactory.getForum(4);
                            Watch emailUpdatesWatch = watchManager.createWatch(
                                    newUser, newsForum);
                            emailUpdatesWatch.setWatchType(Watch.EMAIL_WATCH);
                            emailUpdatesWatch.setExpirable(false);
                        }

                    } catch (Throwable t) {
                        log4j.error("Problem adding watch during " +
                                "registration for player " + name, t);
                    }

                    DSGPlayerData dsgPlayerData = null;
                    try {
                        dsgPlayerData = dsgPlayerStorer.loadPlayer(name);

                        if (emailEnabled) {
                            sendRegistrationEmail(dsgPlayerData.getPlayerID(),
                                    name, password, email);
                        } else {
                            log4j.info("Email not enabled, no registration email sent.");
                        }

                    } catch (DSGPlayerStoreException e) {
                        log4j.error("Error getting player data", e);
                    }

                    break;
            }
        }

        if (registrationError != null) {
            log4j.info("Registration failed: " + registrationError);
        }

        request.setAttribute("registrationError", registrationError);
        if (registrationError != null) {
            getServletContext().getRequestDispatcher(redirectPage).forward(
                    request, response);
        }
        // send redirect to confirmation to avoid duplicate registrations
        else {
            response.sendRedirect(request.getContextPath() + redirectPage);
        }
    }

    private void sendRegistrationEmail(long playerID, String name, String password, String email) {

        try {

            String message;
            String line;
            String lineSeparator = "\n";

            BufferedReader fileIn = new BufferedReader(new FileReader(registrationEmail));

            message = "Dear " + name + "," + lineSeparator + lineSeparator;

            while (true) {
                line = fileIn.readLine();
                if (line == null) {
                    break;
                }
                message += line + lineSeparator;
            }

            message += lineSeparator;
            message += "Name: " + name + lineSeparator;
            message += "Password: " + password + lineSeparator + lineSeparator;
            message += "Have a great time!" + lineSeparator;

            String fromEmail = System.getProperty("mail.smtp.user");

            SendMail2.sendMailSaveInDb(
                    "Pente.org",
                    fromEmail,
                    playerID,
                    name,
                    email,
                    "Pente Registration",
                    message,
                    false,
                    null,
                    returnEmailStorer);

        } catch (Throwable t) {
            log4j.error("Problem sending registration email for " + name, t);
        }
    }
}

