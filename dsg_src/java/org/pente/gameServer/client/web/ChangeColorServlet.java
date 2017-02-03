package org.pente.gameServer.client.web;

import java.awt.Color;
import java.io.*;
import javax.servlet.*;
import javax.servlet.http.*;

import org.apache.log4j.*;

import com.jivesoftware.base.*;

import org.pente.gameServer.core.*;
import org.pente.jive.DSGUserManager;


public class ChangeColorServlet extends HttpServlet {

	private static final Category log4j =
        Category.getInstance(ChangeProfileServlet.class.getName());

    private DSGPlayerStorer dsgPlayerStorer;

    public void init(ServletConfig config) throws ServletException {

        super.init(config);

        try {

            ServletContext ctx = config.getServletContext();

            dsgPlayerStorer = (DSGPlayerStorer) ctx.getAttribute(DSGPlayerStorer.class.getName());

        } catch (Throwable t) {
            log4j.error("Problem in init()", t);
        }
    }

    public void doGet(HttpServletRequest request,
                      HttpServletResponse response)
        throws ServletException, IOException {
        doPost(request, response);
    }

    public void doPost(final HttpServletRequest request,
                       final HttpServletResponse response)
        throws ServletException, IOException {

		String redirectPage = "/gameServer/mobile/empty.jsp";
		String changeProfileError = null;
		String changeProfileSuccess = null;
        DSGPlayerData dsgPlayerData = null;

		
        try {
            
            String name = (String) request.getAttribute("name");
            if (name == null) {
                log4j.error("ChangeColorServlet failed: name=null");
                return;
            }
			log4j.info("ChangeColorServlet: name=" + name);
			
            dsgPlayerData = dsgPlayerStorer.loadPlayer(name);
            if (dsgPlayerData == null || !dsgPlayerData.isActive() || !dsgPlayerData.hasPlayerDonated()) {
                log4j.error("ChangeColorServlet failed: player invalid: " + name);
                return;
            }

			String nameColorStr = (String) request.getParameter("changeNameColor");
			Color nameColor = null;
			if (nameColorStr != null && nameColorStr.length() < 6) {
				changeProfileError = "Name color is too short, must be 6 hexidecimal characters";
			}
			else if (nameColorStr != null) {
				int red = Integer.parseInt(nameColorStr.substring(0, 2), 16);
				int blue = Integer.parseInt(nameColorStr.substring(2, 4), 16);
				int green = Integer.parseInt(nameColorStr.substring(4, 6), 16);
				if (red > 255 || blue > 255 | green > 255) {
					changeProfileError = "Name color invalid, out of range";
				}
				else {
					int min = Math.min(red, Math.min(green, blue));
					int max = Math.max(red, Math.max(green, blue));
					int lum = (min + max) / 2;
					if (lum > 220) {
						changeProfileError = "Name color is too light.";
					}
					else {
						nameColor = new Color(red, blue, green);
					}
				}
			}
		
			if (nameColor != null) {
				dsgPlayerData.setNameColor(nameColor);
			}

			dsgPlayerData.setLastUpdateDate(new java.util.Date());
			dsgPlayerStorer.updatePlayer(dsgPlayerData);
//			((CacheDSGPlayerStorer) dsgPlayerStorer).refreshPlayer(dsgPlayerData.getName());

			 // update jives caches of player data
			UserManager um = UserManagerFactory.getInstance();
			if (um instanceof DSGUserManager) {
				DSGUserManager dum = (DSGUserManager) um;
				if (dum != null) {
					dum.updateUser(dsgPlayerData);
				}
			}

        } catch (DSGPlayerStoreException e) {
        	changeProfileError = "Database error.";
		    log4j.error("Change profile error.", e);
        } catch (Throwable t) {
        	changeProfileError = "Unknown error, contact dweebo.";
        	log4j.error("Change profile error.", t);
        }
        	
		request.setAttribute("changeProfileError", changeProfileError);
		request.setAttribute("changeProfileSuccess", changeProfileSuccess);
		getServletContext().getRequestDispatcher(redirectPage).forward(request, response);
    }
}