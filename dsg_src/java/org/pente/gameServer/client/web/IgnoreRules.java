package org.pente.gameServer.client.web;

import org.pente.gameServer.core.DSGIgnoreData;
import org.pente.gameServer.core.DSGPlayerData;

/** Pure rules behind /gameServer/ignored (ChangeIgnoredServlet), kept free of servlet and DB for testing. */
public class IgnoreRules {

    public static final String NOT_FOUND = "Player ignore not found";
    public static final String SELF = "You can't ignore yourself silly";

    private IgnoreRules() {
    }

    /** Why target can't be ignored by player myPid, or null if they can. */
    public static String rejectTarget(DSGPlayerData target, long myPid) {
        if (target == null || !target.isActive()) {
            return NOT_FOUND;
        }
        if (target.getPlayerID() == myPid) {
            return SELF;
        }
        return null;
    }

    /** ORs the requested flags into existing; true if anything changed. */
    public static boolean mergeFlags(DSGIgnoreData existing, boolean invite, boolean chat) {
        boolean newInvite = existing.getIgnoreInvite() || invite;
        boolean newChat = existing.getIgnoreChat() || chat;
        boolean changed = newInvite != existing.getIgnoreInvite() || newChat != existing.getIgnoreChat();
        existing.setIgnoreInvite(newInvite);
        existing.setIgnoreChat(newChat);
        return changed;
    }
}
