package org.pente.gameServer.event;

import org.pente.gameServer.core.*;

public class DSGPreferenceEvent extends AbstractDSGEvent {

    private DSGPlayerPreference pref;
    
    public DSGPreferenceEvent(DSGPlayerPreference pref) {
        this.pref = pref;
    }
    public DSGPlayerPreference getPref() {
        return pref;
    }
    
    public String toString() {
        return "pref=" + pref; 
    }
}
