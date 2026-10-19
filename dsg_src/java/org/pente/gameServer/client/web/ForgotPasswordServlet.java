package org.pente.gameServer.client.web;

import java.io.*;

import jakarta.servlet.*;
import jakarta.servlet.http.*;

import org.apache.log4j.*;

import org.pente.gameServer.core.*;
import org.pente.gameServer.kids.KidAccountRules;
import org.pente.gameServer.server.*;

public class ForgotPasswordServlet extends HttpServlet {
    private static final long serialVersionUID = -2308537136555198857L;

    private static final Category log4j =
            Category.getInstance(ForgotPasswordServlet.class.getName());

    private transient DSGPlayerStorer dsgPlayerStorer;
    private transient MySQLDSGReturnEmailStorer returnEmailStorer;

    private boolean emailEnabled = true;
    private transient PasswordHelper passwordHelper;

    public void init(ServletConfig config) throws ServletException {

        super.init(config);

        try {

            ServletContext ctx = config.getServletContext();
            returnEmailStorer = (MySQLDSGReturnEmailStorer) ctx.getAttribute(MySQLDSGReturnEmailStorer.class.getName());
            dsgPlayerStorer = (DSGPlayerStorer) ctx.getAttribute(DSGPlayerStorer.class.getName());

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

        String name = request.getParameter("forgotPasswordName");
        String forgotPasswordError = null;
        String redirectPage = "/gameServer/forgotpasswordConfirmation.jsp";
        String lineSeparator = "\n";

        log4j.info("Forgot password: name=" + name);

        DSGPlayerData dsgPlayerData = null;
        DSGPlayerData emailTarget = null;
        try {
            if (name != null) {
                name = name.trim().toLowerCase();
                dsgPlayerData = dsgPlayerStorer.loadPlayer(name);
            }
        } catch (DSGPlayerStoreException e) {
            log4j.error("Problem loading player " + name + " to send password to.", e);
            forgotPasswordError = "Database error, please try again later.";
            redirectPage = "/gameServer/forgotpassword.jsp";
        }

        if (dsgPlayerData == null && forgotPasswordError == null) {
            forgotPasswordError = "Player " + name + " not found, please try again.";
            redirectPage = "/gameServer/forgotpassword.jsp";
        } else if (!emailEnabled) {
            log4j.info("Email not enabled, no password sent.");
            emailTarget = dsgPlayerData;
            request.setAttribute("emailValid", Boolean.valueOf(dsgPlayerData.getEmailValid()));
        } else {

            // a kid's password goes to the parent
            DSGPlayerData parent = null;
            if (dsgPlayerData.getParentPid() != null) {
                try {
                    parent = dsgPlayerStorer.loadPlayer(dsgPlayerData.getParentPid().longValue());
                } catch (DSGPlayerStoreException e) {
                    log4j.error("Problem loading parent of " + dsgPlayerData.getName(), e);
                }
            }
            emailTarget = KidAccountRules.passwordEmailTarget(dsgPlayerData, parent);

            String message;
            String subject;
            if (emailTarget != dsgPlayerData) {
                message = emailTarget.getName() + "," + lineSeparator + lineSeparator;
                message += "Here's the password for the kid account " + dsgPlayerData.getName()
                        + " on Pente.org: ";
                message += passwordHelper.decrypt(dsgPlayerData.getPassword()) + lineSeparator + lineSeparator;
                message += "https://www.pente.org/";
                subject = "Pente.org - Your kid's password";
            } else {
                message = dsgPlayerData.getName() + "," + lineSeparator + lineSeparator;
                message += "Here's your requested password for Dweebo's Stone Games: ";
                message += passwordHelper.decrypt(dsgPlayerData.getPassword()) + lineSeparator + lineSeparator;
                message += "http://www.pente.org/";
                subject = "Pente.org - Your Password";
            }

            String fromEmail = System.getProperty("mail.smtp.user");

            try {
                SendMail2.sendMailSaveInDb(
                        "Pente.org",
                        fromEmail,
                        emailTarget.getPlayerID(),
                        emailTarget.getName(),
                        emailTarget.getEmail(),
                        subject,
                        message,
                        false,
                        null,
                        returnEmailStorer);
            } catch (Throwable t) {
                log4j.error("Error sending password email to " + dsgPlayerData.getName(), t);
                forgotPasswordError = "Error sending email to " + dsgPlayerData.getName() + ".  Please try again later.";
                redirectPage = "/gameServer/forgotpassword.jsp";
            }
        }

        if (forgotPasswordError != null) {
            log4j.info("Forgot password failed: " + forgotPasswordError);
        }

        if (forgotPasswordError != null) {
            request.setAttribute("forgotPasswordError", forgotPasswordError);
            getServletContext().getRequestDispatcher(redirectPage).forward(request, response);
        }
        // send redirect to confirmation to avoid duplicate requests
        else {
            String page = request.getContextPath() + redirectPage +
                    "?forgotPasswordName=" + name;
            if (!emailTarget.getEmailValid()) {
                page += "&emailValid=false";
            }

            response.sendRedirect(page);
        }
    }
}

