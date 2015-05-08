package org.pente.jive;

import java.util.*;

import com.jivesoftware.util.*;
import com.jivesoftware.base.event.*;
import com.jivesoftware.base.*;

import org.apache.log4j.*;

import org.pente.gameServer.core.DSGPlayerData;

public class DSGUser extends SimpleUserAdapter {

    private static final Category log4j =
        Category.getInstance(DSGUserManager.class.getName());
        
    private String location;
    private int age;
    private String homepage;
    private boolean avatar;
	private String timeZone;
	private String sig = "";
	
    /** used for tournament forum creation */
    public DSGUser(long id) {
        this.ID = id;
    }
    
    /** normal use */
    public DSGUser(DSGPlayerData baseData) {
        
        this.ID = baseData.getPlayerID();
        this.username = baseData.getName();
        this.name = baseData.getName();
        this.email = baseData.getEmail();
        this.emailVisible = baseData.getEmailVisible();
        this.creationDate = baseData.getRegisterDate();
        this.modificationDate = baseData.getLastUpdateDate();
        
        this.location = baseData.getLocation();
        this.age = baseData.getAge();
        this.homepage = baseData.getHomepage();
        this.avatar = baseData.hasAvatar();
        this.timeZone = baseData.getTimezone();
        if (baseData.hasPlayerDonated() && baseData.getNote() != null) {
        	this.sig = baseData.getNote();
        }
    }

    // names are always visible
	public boolean isNameVisible() {
		return true;
	}

    public void updateUser(DSGPlayerData data) {

    	boolean fireEvent = false;
    	
    	Map<String, String> params = new HashMap<String, String>();
		params.put("Type", "propertyModify");

    	if (!data.getTimezone().equals(timeZone)) {
			
			fireEvent = true;
			params.put("PropertyKey", "jiveTimeZoneID");
			params.put("originalValue", timeZone);
			
			timeZone = data.getTimezone();		
		}
		if (data.getAge() != age) {
			fireEvent = true;
			params.put("PropertyKey", "dsgAge");
			params.put("originalValue", Integer.toString(age));
			
			age = data.getAge();
		}
		if (!data.getLocation().equals(location)) {
			fireEvent = true;
			params.put("PropertyKey", "dsgLocation");
			params.put("originalValue", location);
			
			location = data.getLocation();
		}
		if (!data.getHomepage().equals(homepage)) {
			fireEvent = true;
			params.put("PropertyKey", "dsgHomePage");
			params.put("originalValue", homepage);
			
			homepage = data.getHomepage();
		}
		if (data.hasAvatar() != avatar) {

			fireEvent = true;
			params.put("PropertyKey", "dsgAvatar");
			params.put("originalValue", avatar ? "yes" : null);
			
			avatar = data.hasAvatar();
		}
		if (!sig.equals(data.getNote())) {

			fireEvent = true;
			params.put("PropertyKey", "sig");
			params.put("originalValue", "sig");
			
			sig = "";
			if (data.getNote() != null) {
				sig = data.getNote();
			}
		}

    	if (fireEvent) {
			UserEvent event = new UserEvent(UserEvent.USER_MODIFIED, 
					this, params);
			EventDispatcher.getInstance().notifyListeners(event);
    	}
    }

    // not used
	public String getProperty(String name) {

        if (name.equals("jiveTimeZoneID")) {
        	return timeZone;
        }  	
        else if (name.equals("dsgLocation")) {
            if (location == null ||
                location.equals("")) {
                return null;
            }
            else {
                return location;
            }
        }
        else if (name.equals("dsgAge")) {
            if (age == 0) {
                return null;
            }
            else {
                return Integer.toString(age);
            }
        }
        else if (name.equals("dsgHomePage")) {
            if (homepage == null ||
                homepage.equals("")) {
                return null;
            }
            else {
                return homepage;
            }
        }
        else if (name.equals("dsgAvatar")) {
            if (avatar) {
                return "yes";
            }
            else {
                return null;
            }
        }
        else if (name.equals("dsgSig")) {
            return sig;
        }
        else {
            return super.getProperty(name);
        }
	}

    // Cacheable Interface
    public int getCachedSize() {

        int size = super.getCachedSize();

        size += CacheSizes.sizeOfString(location);      // location
        size += CacheSizes.sizeOfString(homepage);      // homepage
        size += CacheSizes.sizeOfString(timeZone);		// timezone
        size += CacheSizes.sizeOfInt();					// age
        size += CacheSizes.sizeOfBoolean();             // avatar
        size += CacheSizes.sizeOfString(sig);			// sig

        return size;
    }
}