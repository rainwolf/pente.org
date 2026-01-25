package org.pente.gameServer.client.web;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.log4j.Category;
import org.pente.gameServer.core.CacheDSGPlayerStorer;
import org.pente.gameServer.core.DSGPlayerData;
import org.pente.gameServer.core.DSGPlayerStorer;

import java.io.IOException;
import java.io.PrintWriter;

public class EmailVerificationServlet extends HttpServlet {

    private static final Category cat = Category.getInstance(EmailPlayerServlet.class.getName());

    CacheDSGPlayerStorer dsgPlayerStorer;

    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        ServletContext ctx = config.getServletContext();
        dsgPlayerStorer = (CacheDSGPlayerStorer) ctx.getAttribute(DSGPlayerStorer.class.getName());
    }

    public void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String verificationCode = request.getParameter("code");
        PrintWriter out = response.getWriter();
        try {
            if (verificationCode == null || verificationCode.isEmpty()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print("Missing verification code.");
                return;
            }

            long pid = dsgPlayerStorer.verifyEmailCode(verificationCode);
            DSGPlayerData player = dsgPlayerStorer.loadPlayer(pid);
            out.print("Email successfully verified for " + player.getName() + ". Thank you!");
        } catch (Exception e) {
            cat.error("Error during email verification", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("An error occurred while verifying your email. Please try again later.");
        }

    }
}
