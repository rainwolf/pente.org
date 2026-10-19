package org.pente.gameServer.kids;

import java.util.Date;

import org.pente.gameServer.core.DSGPlayerData;
import org.pente.gameServer.core.DSGPlayerStorerRegisterHandler;
import org.pente.gameServer.core.SimpleDSGPlayerData;

/** Error strings for kid accounts (API contract text, verbatim) and pure account rules. */
public class KidAccountRules {

    public static final String LOGIN_REQUIRED = "Login required";
    public static final String UNKNOWN_ACTION = "Unknown action";
    public static final String BAD_ORIGIN = "Request origin not allowed";
    public static final String KIDS_CANT_MANAGE = "Kid accounts can't manage kids";
    public static final String VERIFY_EMAIL =
        "Verify your email address before adding kids";
    public static final String INVALID_DATA =
        "Invalid registration data, please review instructions and try again.";
    public static final String PLAYER_NOT_FOUND = "Player not found";
    public static final String CANT_ADD_SELF = "You can't add yourself as a kid";
    public static final String CANT_CONVERT =
        "That account can't become a kid account";
    public static final String KID_NOT_FOUND = "Kid not found";
    public static final String INVALID_PASSWORD =
        "Passwords must be 5-16 letters, numbers or _";
    public static final String CANT_APPROVE = "A kid can't approve themself";
    public static final String NOT_APPROVED = "Player is not approved";
    public static final String DATABASE = "Database error";
    public static final String PROFILE_MANAGED =
        "Your password and email are managed by your parent.";
    public static final String KID_PM_ERROR =
        "You can only send messages to players your parent approved.";
    public static final String KID_BROADCAST_ERROR = "Kid accounts can't broadcast.";
    public static final String LINK_INVALID = "This link is invalid or has expired.";

    private static final long LINK_LIFETIME_MS = 7L * 24 * 60 * 60 * 1000;

    private KidAccountRules() {
    }

    public static String nameTaken(String name) {
        return "Requested name " + name + " is already taken, please choose another.";
    }

    private static boolean isKid(DSGPlayerData p) {
        return p.getParentPid() != null;
    }

    public static String parentError(DSGPlayerData caller, boolean addingKid) {
        if (isKid(caller)) {
            return KIDS_CANT_MANAGE;
        }
        if (addingKid && !caller.getEmailValid()) {
            return VERIFY_EMAIL;
        }
        return null;
    }

    private static boolean unusable(DSGPlayerData target) {
        return target == null || !target.isActive() || target.isGuest();
    }

    public static String conversionError(DSGPlayerData caller, DSGPlayerData target,
                                         boolean targetHasKids) {
        if (unusable(target)) {
            return PLAYER_NOT_FOUND;
        }
        if (target.getPlayerID() == caller.getPlayerID()) {
            return CANT_ADD_SELF;
        }
        if (target.isAdmin() || target.isComputer() || isKid(target) || targetHasKids) {
            return CANT_CONVERT;
        }
        return null;
    }

    public static boolean conversionSilentlyRefused(DSGPlayerData target,
                                                    boolean targetHasKids) {
        return target.isAdmin() || isKid(target) || targetHasKids;
    }

    public static SimpleDSGPlayerData newKidData(DSGPlayerData parent, String name,
                                                 String encryptedPassword) {
        SimpleDSGPlayerData data = new SimpleDSGPlayerData();
        data.setName(name);
        data.setPassword(encryptedPassword);
        data.setEmail(parent.getEmail());
        data.setEmailVisible(false);
        data.setEmailValid(false);
        data.setTimezone(parent.getTimezone());
        data.setSex(DSGPlayerData.UNKNOWN);
        data.setAge(0);
        data.setPlayerType(DSGPlayerData.HUMAN);
        data.setParentPid(Long.valueOf(parent.getPlayerID()));
        data.setMobileAdult(false);
        data.setStatus(DSGPlayerData.ACTIVE);
        Date now = new Date();
        data.setRegisterDate(now);
        data.setLastLoginDate(now);
        data.setLastUpdateDate(now);
        return data;
    }

    public static boolean emailVisibleFor(boolean isKid, boolean requested) {
        return !isKid && requested;
    }

    public static boolean ownsKid(DSGPlayerData caller, DSGPlayerData kid) {
        return kid != null && kid.getParentPid() != null
            && kid.getParentPid().longValue() == caller.getPlayerID();
    }

    public static boolean passwordValid(String pw) {
        return pw != null
            && DSGPlayerStorerRegisterHandler.PASSWORD_PATTERN.matcher(pw).matches();
    }

    public static String whitelistAddError(DSGPlayerData kid, DSGPlayerData target) {
        if (unusable(target)) {
            return PLAYER_NOT_FOUND;
        }
        if (target.getPlayerID() == kid.getPlayerID()) {
            return CANT_APPROVE;
        }
        return null;
    }

    public static String profileChangeError(boolean isKid, String currentEmail,
                                            String newEmail, String newPassword) {
        if (!isKid) {
            return null;
        }
        boolean passwordChange = newPassword != null && !newPassword.isEmpty();
        boolean emailChange = newEmail != null && !newEmail.isEmpty()
            && !newEmail.equals(currentEmail);
        return passwordChange || emailChange ? PROFILE_MANAGED : null;
    }

    public static boolean linkExpired(Date created, Date now) {
        return now.getTime() - created.getTime() > LINK_LIFETIME_MS;
    }

    public static DSGPlayerData passwordEmailTarget(DSGPlayerData player,
                                                    DSGPlayerData parent) {
        return isKid(player) && parent != null ? parent : player;
    }

    public static String passwordEmailLogLine(DSGPlayerData player,
                                              DSGPlayerData target) {
        return "Forgot password: name=" + player.getName() + " mailTo="
            + target.getName() + " " + target.getEmail();
    }
}
