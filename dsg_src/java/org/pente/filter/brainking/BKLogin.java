package org.pente.filter.brainking;

import java.util.*;

import org.pente.filter.*;
import org.pente.filter.http.*;

public class BKLogin implements LineFilter {

    private String uid;
    private Hashtable cookies;
    
    public BKLogin(String uid) {
        this.uid = uid;
    }

    public Hashtable getCookies() {
        
        cookies = BKConstants.getCookies(uid);
        
        FilterController filterController = new HttpFilterController(
            "GET",
            BKConstants.HOST,
            BKConstants.LOGIN_REQUEST,
            new Hashtable(),
            cookies,
            this);
        filterController.run();
        
        return cookies;
    }

    static final String SET_COOKIE = "Set-Cookie: JSESSIONID=";
    public String filterLine(String line) {
        int index = line.indexOf(SET_COOKIE);
        if (index != -1) {
            index += SET_COOKIE.length();
            cookies.put("JSESSIONID", line.substring(index));
        }
        
        return null;
    }
}
