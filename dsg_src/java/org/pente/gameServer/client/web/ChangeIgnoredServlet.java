package org.pente.gameServer.client.web;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.*;
import java.io.*;
import java.util.*;

import javax.imageio.ImageIO;
import javax.servlet.*;
import javax.servlet.http.*;

import org.apache.log4j.*;
import org.apache.commons.fileupload.*;

import com.jivesoftware.base.*;

import org.pente.gameServer.core.*;
import org.pente.gameServer.server.*;
import org.pente.jive.DSGUserManager;


public class ChangeIgnoredServlet extends HttpServlet {

    private static final Category log4j =
            Category.getInstance(ChangeProfileServlet.class.getName());

    private Resources resources;
    private DSGPlayerStorer dsgPlayerStorer;
    private RegisterHandler registerHandler;

    private PasswordHelper passwordHelper;

    public void init(ServletConfig config) throws ServletException {

        super.init(config);

        try {

            ServletContext ctx = config.getServletContext();

            resources = (Resources) ctx.getAttribute(Resources.class.getName());
            dsgPlayerStorer = (DSGPlayerStorer) ctx.getAttribute(DSGPlayerStorer.class.getName());
            registerHandler = new DSGPlayerStorerRegisterHandler(dsgPlayerStorer);

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

    public void doPost(final HttpServletRequest request,
                       final HttpServletResponse response)
            throws ServletException, IOException {

        String redirectPage = "/gameServer/myprofile.jsp";
        String changeProfileError = null;
        String viewProfileError = null;
        String changeProfileSuccess = null;
        DSGPlayerData dsgPlayerData = null;

        String pathinfo = request.getPathInfo();
        //default is myInfo
        if (pathinfo == null) {
            pathinfo = "/load";
        }
        String commands[] = pathinfo.substring(1).split("/");
        String command = commands[0];
        if (command.equals("donor")) {
            redirectPage = "/gameServer/myprofileDonor.jsp";
        } else if (command.equals("prefs")) {
            redirectPage = "/gameServer/myprofilePrefs.jsp";
        }

        try {

            String name = (String) request.getAttribute("name");
            if (name == null) {
                log4j.error("Change profile failed: name=null");
                return;
            }
            log4j.info("Change profile: name=" + name + ", command=" + command);

            dsgPlayerData = dsgPlayerStorer.loadPlayer(name);
            if (dsgPlayerData == null || !dsgPlayerData.isActive()) {
                log4j.error("Change profile failed: player invalid: " + name);
                return;
            }

            LoginCookieHandler loginCookieHandler = new LoginCookieHandler();

            // jakarta commons lib to handle upload files
            DiskFileUpload upload = new DiskFileUpload();

            // need to make these configurable
            upload.setSizeThreshold(50 * 1024); //50k to avoid out of memory issues
            upload.setSizeMax(4 * 1024 * 1024); //4mb
            upload.setRepositoryPath("/var/lib/dsg/gameServer");

            // if request is multipart then we are saving, otherwise we are
            // viewing our profile
            if (command.equals("myInfo") || FileUploadBase.isMultipartContent(request)) {

                // store request parameters in hash map for easy access
                Map<String, String> params = new HashMap<String, String>();
                // store avatar for later processing
                FileItem avatarFileItem = null;

                // process request to get parameters and avatar upload
                List items = upload.parseRequest(request);
                List<Integer> newVacationDays = new ArrayList<Integer>();
                Iterator iter = items.iterator();
                while (iter.hasNext()) {
                    FileItem item = (FileItem) iter.next();

                    if (item.isFormField()) {
                        if (item.getFieldName().equals("vacationDay")) {
                            newVacationDays.add(new Integer(item.getString()));
                        } else {
                            params.put(item.getFieldName(), item.getString());
                        }
                    } else if (!item.getName().equals("")) {
                        avatarFileItem = item;
                    }
                }

                if (command.equals("myInfo")) {

                    String password = (String) params.get("changePassword");
                    if (password != null) {
                        password = password.trim();
                    } else {
                        password = "";
                    }
                    String passwordConfirm = (String) params.get("changePasswordConfirm");
                    if (passwordConfirm != null) {
                        passwordConfirm = passwordConfirm.trim();
                    } else {
                        passwordConfirm = "";
                    }
                    String email = (String) params.get("changeEmail");
                    if (email != null) {
                        email = email.trim();
                    } else {
                        email = "";
                    }
                    String emailVisibleStr = (String) params.get("changeEmailVisible");
                    boolean emailVisible = false;
                    if (emailVisibleStr != null) {
                        emailVisible = emailVisibleStr.trim().equals("Y");
                    }

                    String location = (String) params.get("changeLocation");
                    if (location != null) {
                        location = location.trim();
                    }
                    String timezone = (String) params.get("timezone");
                    if (timezone != null) {
                        timezone = timezone.trim();
                    }

                    String sexStr = (String) params.get("changeSex");
                    char sex = DSGPlayerData.UNKNOWN;
                    if (sexStr != null && sexStr.length() > 0) {
                        sex = sexStr.trim().charAt(0);
                        if (sex != DSGPlayerData.MALE &&
                                sex != DSGPlayerData.FEMALE) {
                            sex = DSGPlayerData.UNKNOWN;
                        }
                    }
                    String ageStr = (String) params.get("changeAge");
                    int age = 0;
                    if (ageStr != null && ageStr.length() > 0) {
                        try {
                            age = Integer.parseInt(ageStr);
                        } catch (NumberFormatException ex) {
                        }
                    }

                    String homepage = (String) params.get("changeHomepage");
                    // homepage can be null, or "", if user is attempting to erase previous entry
                    if (homepage != null) {
                        homepage = homepage.trim();
                    }

                    if (changeProfileError != null) {
                    } else if (!password.equals(passwordConfirm)) {
                        changeProfileError = "Passwords don't match.";
                    } else if (location != null && location.length() > 100) {
                        changeProfileError = "Location must be less than 101 characters";
                    } else if (homepage != null && homepage.length() > 100) {
                        changeProfileError = "Home page must be less than 101 characters";
                    } else {

                        if (password.equals("")) {
                            password = dsgPlayerData.getPassword();
                        } else {
                            password = passwordHelper.encrypt(password);
                        }
                        if (registerHandler.isValidRegistration(
                                name, passwordHelper.decrypt(password), email, true)) {

                            changeProfileSuccess = "Your profile has been changed successfully.";
                            if (!dsgPlayerData.getPassword().equals(password)) {
                                dsgPlayerData.setPassword(password);

                                loginCookieHandler.setName(dsgPlayerData.getName());
                                loginCookieHandler.setPassword(dsgPlayerData.getPassword());
                                loginCookieHandler.setCookie(request, response);

                                request.setAttribute("password", password);
                            }

                            // if user changed their email address
                            if (!dsgPlayerData.getEmail().equals(email)) {
                                dsgPlayerData.setEmail(email);
                            }
                            // always reset the email valid to true on any update
                            // in case an email was sent to the correct address
                            // but just didn't get through 1 time
                            dsgPlayerData.setEmailValid(true);

                            dsgPlayerData.setEmailVisible(emailVisible);
                            dsgPlayerData.setAge(age);
                            dsgPlayerData.setSex(sex);
                            dsgPlayerData.setLocation(location);
                            dsgPlayerData.setTimezone(timezone);
                            dsgPlayerData.setHomepage(cleanHomepage(homepage));

                            dsgPlayerData.setLastUpdateDate(new java.util.Date());
                            dsgPlayerStorer.updatePlayer(dsgPlayerData);


                            // update jives caches of player data
                            UserManager um = UserManagerFactory.getInstance();
                            if (um instanceof DSGUserManager) {
                                DSGUserManager dum = (DSGUserManager) um;
                                dum.updateUser(dsgPlayerData);
                            }
                        } else {
                            changeProfileError = "Invalid data, please review instructions and try again.";
                        }
                    }
                } else if (command.equals("donor")) {


                    if (!dsgPlayerData.hasPlayerDonated()) {
                        changeProfileError = "You must be a donor.";
                    } else {
                        String nameColorStr = (String) params.get("changeNameColor");
                        Color nameColor = null;
                        if (nameColorStr != null && nameColorStr.length() < 6) {
                            changeProfileError = "Name color is too short, must be 6 hexidecimal characters";
                        } else if (nameColorStr != null) {
                            int red = Integer.parseInt(nameColorStr.substring(0, 2), 16);
                            int blue = Integer.parseInt(nameColorStr.substring(2, 4), 16);
                            int green = Integer.parseInt(nameColorStr.substring(4, 6), 16);
                            if (red > 255 || blue > 255 | green > 255) {
                                changeProfileError = "Name color invalid, out of range";
                            } else {
                                int min = Math.min(red, Math.min(green, blue));
                                int max = Math.max(red, Math.max(green, blue));
                                int lum = (min + max) / 2;
                                if (lum > 220) {
                                    changeProfileError = "Name color is too light.";
                                } else {
                                    nameColor = new Color(red, blue, green);
                                }
                            }
                        }

                        String note = (String) params.get("note");
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

                        if (changeProfileError == null) {
                            changeProfileSuccess = "Your donor settings have been changed successfully.";

                            if (nameColor != null) {
                                dsgPlayerData.setNameColor(nameColor);
                            }
                            dsgPlayerData.setNote(note);
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
                                dum.updateUser(dsgPlayerData);
                            }
                        }
                    }
                } else if (command.equals("prefs")) {

                    String gameRoomSize = (String) params.get("gameRoomSize");
                    String email = (String) params.get("email");
                    String emailSent = (String) params.get("emailSent");

                    String weekend1 = (String) params.get("weekend1");
                    String weekend2 = (String) params.get("weekend2");

                    String refresh = (String) params.get("refresh");

                    if (weekend1.equals(weekend2)) {
                        changeProfileError = "Must select two different " +
                                "weekend days.";
                        List<DSGPlayerPreference> prefs = dsgPlayerStorer.loadPlayerPreferences(
                                dsgPlayerData.getPlayerID());
                        request.setAttribute("prefs", prefs);
                    } else {
                        DSGPlayerPreference p = new DSGPlayerPreference(
                                "gameRoomSize", gameRoomSize);
                        dsgPlayerStorer.storePlayerPreference(
                                dsgPlayerData.getPlayerID(), p);

                        // stored in session for temporary quick access
                        HttpSession session = request.getSession(false);
                        session.setAttribute("gameRoomSize", gameRoomSize);

                        boolean emailDsgMessages = email != null && email.equals("Y");
                        p = new DSGPlayerPreference(
                                "emailDsgMessages", new Boolean(emailDsgMessages));
                        dsgPlayerStorer.storePlayerPreference(
                                dsgPlayerData.getPlayerID(), p);

                        boolean emailSentDsgMessages = emailSent != null &&
                                emailSent.equals("Y");
                        p = new DSGPlayerPreference(
                                "emailSentDsgMessages", new Boolean(emailSentDsgMessages));
                        dsgPlayerStorer.storePlayerPreference(
                                dsgPlayerData.getPlayerID(), p);

                        int refreshTime = Integer.parseInt(refresh);
                        p = new DSGPlayerPreference("refresh", refreshTime);
                        dsgPlayerStorer.storePlayerPreference(
                                dsgPlayerData.getPlayerID(), p);

                        List<DSGPlayerPreference> prefs = dsgPlayerStorer.loadPlayerPreferences(
                                dsgPlayerData.getPlayerID());
                        // List<Date> vacationDays = dsgPlayerStorer.loadVacationDays(
                        // 	dsgPlayerData.getPlayerID());

                        request.setAttribute("prefs", prefs);
                        // request.setAttribute("vacationDays", vacationDays);

                        // String monthYear = (String) params.get("monthYear");
                        // if (monthYear != null) {
                        // 	request.setAttribute("monthYear", monthYear);
                        // }

                        int weekend[] = new int[2];
                        weekend[0] = Integer.parseInt(weekend1);
                        weekend[1] = Integer.parseInt(weekend2);
                        int weekendStored[] = new int[]{7, 1};
                        DSGPlayerPreference p2 = null;
                        for (Iterator it = prefs.iterator(); it.hasNext(); ) {
                            p2 = (DSGPlayerPreference) it.next();
                            if (p2.getName().equals("weekend")) {
                                weekendStored = (int[]) p2.getValue();
                            }
                        }

                        boolean weekendChanged = false;
                        // if weekend days changed
                        if ((weekend[0] != weekendStored[0] &&
                                weekend[0] != weekendStored[1]) ||
                                (weekend[1] != weekendStored[0] &&
                                        weekend[1] != weekendStored[1])) {

                            weekendChanged = true;

                            p = new DSGPlayerPreference(
                                    "weekend", weekend);
                            dsgPlayerStorer.storePlayerPreference(
                                    dsgPlayerData.getPlayerID(), p);


                            // update prefs to return to user
                            prefs.remove(p2);
                            prefs.add(p);
                        }

                        boolean vacationChanged = false;
                        //       			if (monthYear != null) {
                        // 	int vacationMonth = Integer.parseInt((String) params.get("vacationMonth"));
                        // 	int vacationYear = Integer.parseInt((String) params.get("vacationYear"));
                        // 	// this loop adds any newly checked vacation days to the list
                        // 	outer: for (int newVacationDay : newVacationDays) {
                        // 		for (Date oldVacationDay : vacationDays) {
                        // 			// skip already stored days
                        // 			if (oldVacationDay.getYear() == (vacationYear - 1900) &&
                        // 			    oldVacationDay.getMonth() == vacationMonth &&
                        // 			    oldVacationDay.getDate() == newVacationDay) {
                        // 		    	continue outer;
                        // 		    }
                        // 		}
                        // 		// if get here then we know its a new date
                        // 		vacationDays.add(new Date(vacationYear - 1900, vacationMonth, newVacationDay));
                        // 		vacationChanged = true;
                        // 	}
                        // 	// this loop removes any newly unchecked vacation days from the list
                        // 	outer: for (Iterator<Date> it = vacationDays.iterator(); it.hasNext();) {
                        // 		Date oldVacationDay = it.next();

                        // 		// skip vacation days that have already been used
                        // 		if (oldVacationDay.before(new Date())) {
                        // 			continue;
                        // 		}

                        // 		// check for stored vacation days that are in the
                        // 		// month and year the user is viewing
                        // 		if (oldVacationDay.getYear() == (vacationYear - 1900) &&
                        // 		    oldVacationDay.getMonth() == vacationMonth) {

                        // 			// skip days that are still checked
                        // 			for (int newVacationDay : newVacationDays) {
                        // 				if (newVacationDay == oldVacationDay.getDate()) {
                        // 					continue outer;
                        // 				}
                        // 			}
                        // 			// if get here then we know the vacation day was removed
                        // 			it.remove();
                        // 			vacationChanged = true;
                        // 	    }
                        // 	}
                        // 	if (vacationChanged) {
                        // 		// if changed days, don't go to different month
                        // 		request.setAttribute("monthYear", Integer.toString(vacationYear) + vacationMonth);

                        // 		dsgPlayerStorer.storeVacationDays(
                        // 			dsgPlayerData.getPlayerID(), vacationDays);
                        // 	}
                        // }

                        if (weekendChanged || vacationChanged) {
                            resources.getTbGameStorer().updateDaysOff(
                                    dsgPlayerData.getPlayerID(), weekend);
                        }

                        boolean ignoreUpdated = false;
                        List<DSGIgnoreData> ignoreData = dsgPlayerStorer.getIgnoreData(
                                dsgPlayerData.getPlayerID());

                        for (Iterator<DSGIgnoreData> it = ignoreData.iterator(); it.hasNext(); ) {
                            DSGIgnoreData i = it.next();
                            String iiStr = (String) params.get(i.getIgnorePid() + "_invite");
                            String icStr = (String) params.get(i.getIgnorePid() + "_chat");
                            boolean ignoreInvite = iiStr != null && iiStr.equals("Y");
                            boolean ignoreChat = icStr != null && icStr.equals("Y");
                            // then invite was removed
                            if (!ignoreInvite && !ignoreChat) {
                                dsgPlayerStorer.deleteIgnore(i);
                                ignoreUpdated = true;
                            }
                            // else update invite
                            else if (i.getIgnoreInvite() != ignoreInvite ||
                                    i.getIgnoreChat() != ignoreChat) {
                                i.setIgnoreInvite(ignoreInvite);
                                i.setIgnoreChat(ignoreChat);
                                dsgPlayerStorer.updateIgnore(i);
                                ignoreUpdated = true;
                            }
                        }
                        String newIgnoreName = (String) params.get("ignore_name");
                        String newIgnoreInvite = (String) params.get("ignore_invite");
                        String newIgnoreChat = (String) params.get("ignore_chat");
                        if (newIgnoreName != null && !newIgnoreName.equals("")) {
                            boolean ignoreInvite = newIgnoreInvite != null &&
                                    newIgnoreInvite.equals("Y");
                            boolean ignoreChat = newIgnoreChat != null &&
                                    newIgnoreChat.equals("Y");
                            if (ignoreInvite || ignoreChat) {
                                DSGPlayerData d = dsgPlayerStorer.loadPlayer(newIgnoreName);
                                if (d == null) {
                                    changeProfileError = "Player to ignore not found";
                                } else if (d.getPlayerID() == dsgPlayerData.getPlayerID()) {
                                    changeProfileError = "You can't ignore yourself silly";
                                } else {
                                    boolean alreadyIgnored = false;
                                    for (DSGIgnoreData i : ignoreData) {
                                        if (i.getIgnorePid() == d.getPlayerID()) {
                                            alreadyIgnored = true;
                                            break;
                                        }
                                    }
                                    if (!alreadyIgnored) {
                                        ignoreUpdated = true;
                                        DSGIgnoreData i = new DSGIgnoreData();
                                        i.setPid(dsgPlayerData.getPlayerID());
                                        i.setIgnorePid(d.getPlayerID());
                                        i.setIgnoreInvite(ignoreInvite);
                                        i.setIgnoreChat(ignoreChat);
                                        dsgPlayerStorer.insertIgnore(i);
                                    }
                                }
                            }
                        }

                        if (ignoreUpdated) {
                            ((CacheDSGPlayerStorer) dsgPlayerStorer).notifyIgnoreListeners(
                                    dsgPlayerData.getPlayerID());
                            // reload data
                            ignoreData = dsgPlayerStorer.getIgnoreData(
                                    dsgPlayerData.getPlayerID());
                        }
                        request.setAttribute("ignoreData", ignoreData);

                        if (changeProfileError == null) {
                            String button = (String) params.get("submit");
                            if (button != null && !button.equals("Change")) {
                                changeProfileSuccess = "Your preferences have been changed successfully.";
                            }
                        }
                    }
                }
            }
            // we are loading data to view
            else {
                if (command.equals("prefs")) {
                    List<DSGPlayerPreference> prefs = dsgPlayerStorer.loadPlayerPreferences(
                            dsgPlayerData.getPlayerID());
                    List<Date> vacationDays = dsgPlayerStorer.loadVacationDays(
                            dsgPlayerData.getPlayerID());
                    List<DSGIgnoreData> ignoreData = dsgPlayerStorer.getIgnoreData(
                            dsgPlayerData.getPlayerID());
                    request.setAttribute("prefs", prefs);
                    request.setAttribute("vacationDays", vacationDays);
                    request.setAttribute("ignoreData", ignoreData);
                }
            }

        } catch (DSGPlayerStoreException e) {
            changeProfileError = "Database error.";
            log4j.error("Change profile error.", e);
        } catch (FileUploadException f) {
            changeProfileError = "Picture upload error, make sure image " +
                    "is smaller than 4mb.";
            log4j.error("Change profile error.", f);
        } catch (IOException f) {
            changeProfileError = "Error: " + f.getMessage();
            log4j.error("Change profile error.", f);
        } catch (Throwable t) {
            changeProfileError = "Unknown error, contact dweebo.";
            log4j.error("Change profile error.", t);
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