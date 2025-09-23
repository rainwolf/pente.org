package org.pente.gameServer.client.web;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.*;
import java.io.*;
import java.util.*;

import jakarta.servlet.*;
import jakarta.servlet.http.*;

import org.apache.commons.fileupload2.core.DiskFileItemFactory;
import org.apache.commons.fileupload2.core.FileItem;
import org.apache.commons.fileupload2.core.FileUploadException;
import org.apache.commons.fileupload2.jakarta.servlet5.JakartaServletFileUpload;
import org.apache.log4j.*;

import com.jivesoftware.base.*;

import org.pente.gameServer.core.*;
import org.pente.gameServer.server.*;
import org.pente.jive.DSGUserManager;


public class ChangeAvatarServlet extends HttpServlet {

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
        String viewProfileError = null;
        String changeProfileSuccess = null;
        DSGPlayerData dsgPlayerData = null;

        String pathinfo = request.getPathInfo();
        //default is myInfo
        if (pathinfo == null) {
            pathinfo = "/load";
        }

        try {

            String name = (String) request.getAttribute("name");
            if (name == null) {
                log4j.error("ChangeAvatarServlet failed: name=null");
                return;
            }
            log4j.info("ChangeAvatarServlet: name=" + name);

            dsgPlayerData = dsgPlayerStorer.loadPlayer(name);
            if (dsgPlayerData == null || !dsgPlayerData.isActive()) {
                log4j.error("Change profile failed: player invalid: " + name);
                return;
            }

            // jakarta commons lib to handle upload files
            DiskFileItemFactory factory = DiskFileItemFactory.builder().get();
            JakartaServletFileUpload upload = new JakartaServletFileUpload(factory);
            upload.setSizeMax(4 * 1024 * 1024); //4mb

            // if request is multipart then we are saving, otherwise we are
            // viewing our profile
            if (JakartaServletFileUpload.isMultipartContent(request)) {

                // store request parameters in hash map for easy access
                Map<String, String> params = new HashMap<String, String>();
                // store avatar for later processing
                FileItem avatarFileItem = null;

                // process request to get parameters and avatar upload
                List items = upload.parseRequest(request);
                Iterator iter = items.iterator();
                while (iter.hasNext()) {
                    FileItem item = (FileItem) iter.next();

                    if (!item.getName().equals("")) {
                        avatarFileItem = item;
                    }
                }

                String avatarContentType = null;
                byte avatarBytes[] = null;
                if (avatarFileItem != null) {

                    // check content type of image
                    avatarContentType = avatarFileItem.getContentType().toLowerCase();
                    if (!avatarContentType.equals("image/gif") &&
                            !avatarContentType.equals("image/jpg") &&
                            !avatarContentType.equals("image/jpeg") &&
                            !avatarContentType.equals("image/png")) {
                        changeProfileError = "Invalid picture content-type: " +
                                avatarContentType + ".  Must be a gif, jpg or png image.";
                    } else {

                        byte b[] = ImageUtils.handleImage(avatarContentType.substring(6), avatarFileItem.getInputStream());
                        if (b == null) {
                            avatarBytes = avatarFileItem.get();
                        } else {
                            avatarBytes = b;
                        }
                    }
                }

                if (params.get("removeAvatar") != null) {
                    dsgPlayerStorer.deleteAvatar(dsgPlayerData);
                }

                if (avatarFileItem != null) {
                    dsgPlayerStorer.deleteAvatar(dsgPlayerData);
                    dsgPlayerData.setAvatar(avatarBytes);
                    dsgPlayerData.setAvatarContentType(avatarContentType);
                    dsgPlayerData.setAvatarLastModified(System.currentTimeMillis());
                    dsgPlayerStorer.insertAvatar(dsgPlayerData);
                }

                dsgPlayerData.setLastUpdateDate(new java.util.Date());
                dsgPlayerStorer.updatePlayer(dsgPlayerData);

                // update jives caches of player data
                UserManager um = UserManagerFactory.getInstance();
                if (um instanceof DSGUserManager) {
                    DSGUserManager dum = (DSGUserManager) um;
                    if (dum != null) {
                        dum.updateUser(dsgPlayerData);
                    }
                }
            }

        } catch (DSGPlayerStoreException e) {
            changeProfileError = "Database error.";
            log4j.error("ChangeAvatarServlet error.", e);
        } catch (FileUploadException f) {
            changeProfileError = "Picture upload error, make sure image " +
                    "is smaller than 4mb.";
            log4j.error("ChangeAvatarServlet error.", f);
        } catch (IOException f) {
            changeProfileError = "Error: " + f.getMessage();
            log4j.error("ChangeAvatarServlet error.", f);
        } catch (Throwable t) {
            changeProfileError = "Unknown error, contact dweebo.";
            log4j.error("ChangeAvatarServlet error.", t);
        }

        request.setAttribute("dsgPlayerData", dsgPlayerData);
        request.setAttribute("viewProfileError", viewProfileError);
        request.setAttribute("changeProfileError", changeProfileError);
        request.setAttribute("changeProfileSuccess", changeProfileSuccess);
        getServletContext().getRequestDispatcher(redirectPage).forward(request, response);
    }

    public static BufferedImage resize(BufferedImage img, int newW, int newH) {
        int w = img.getWidth();
        int h = img.getHeight();
        BufferedImage dimg = new BufferedImage(newW, newH, img.getType());
        Graphics2D g = dimg.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(img, 0, 0, newW, newH, 0, 0, w, h, null);
        g.dispose();
        return dimg;
    }

    public static String cleanHomepage(String homePage) {

        if (homePage != null && !homePage.equals("") &&
                !homePage.startsWith("http://") &&
                !homePage.startsWith("https://")) {

            homePage = "http://" + homePage;
        }

        return homePage;
    }
}