package org.pente.gameServer.client;

import java.util.*;

import org.pente.gameServer.event.*;
import org.pente.gameServer.core.*;

public class PreferenceHandler {

    private Vector prefs = new Vector();

    private DSGEventSource dsgEventSource;
    private DSGEventListener dsgEventListener;
    
    public PreferenceHandler(
        DSGEventSource dsgEventSource,
        DSGEventListener dsgEventListener) {
        
        this.dsgEventSource = dsgEventSource;
        this.dsgEventListener = dsgEventListener;

        dsgEventSource.addListener(new DSGEventListener() {
            public void eventOccurred(DSGEvent dsgEvent) {
                if (dsgEvent instanceof DSGPreferenceEvent) {
                    DSGPlayerPreference p = ((DSGPreferenceEvent) dsgEvent).getPref();
                    receivePref(p);
                }
            }
        });
    }
    
    private synchronized void receivePref(DSGPlayerPreference pref) {
        prefs.addElement(pref);
    }
    
    public void storePref(String prefName, Object prefValue) {
        synchronized (this) {
            boolean found = false;
            for (int i = 0; i < prefs.size(); i++) {
                DSGPlayerPreference p = (DSGPlayerPreference) prefs.elementAt(i);
                if (p.getName().equals(prefName)) {
                    found = true;
                    p.setValue(prefValue);
                }
            }
            
            if (!found) {
                prefs.addElement(new DSGPlayerPreference(prefName, prefValue));
            }
        }

        dsgEventListener.eventOccurred(new DSGPreferenceEvent(
            new DSGPlayerPreference(prefName, prefValue)));
    }
    
    public synchronized Object getPref(String prefName) {
        for (int i = 0; i < prefs.size(); i++) {
            DSGPlayerPreference p = (DSGPlayerPreference) prefs.elementAt(i);
            if (p.getName().equals(prefName)) {
                return p.getValue();
            }
        }
        return null;
    }
}
