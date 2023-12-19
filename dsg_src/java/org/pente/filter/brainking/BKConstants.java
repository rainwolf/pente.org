package org.pente.filter.brainking;

import java.util.*;

public class BKConstants {

    static final String HOST = "www.brainking.com";
    static final String LOGIN_REQUEST = "/game/FirstPage";

    static final String USERID_COOKIE = "uid";

    static final String USER_PROFILE_REQUEST = "/game/Profile";
    static final String USER_PROFILE_PARAM = "u";

    public static final Hashtable<String, String> getCookies(String uid) {
        Hashtable<String, String> defaults = new Hashtable<>();
        defaults.put("lang", "en");
        defaults.put("uid", uid);

        return defaults;
    }
}
