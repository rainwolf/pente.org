package org.pente.gameServer.kids;

import org.pente.gameServer.core.DSGPlayerData;
import org.pente.turnBased.TBMessage;

/**
 * Display filter for open-invitation text (decision D3). An open invitation's
 * text is stored before the recipient is known, so it is hidden when served
 * to a kid who may not exchange chat with the inviter.
 */
public final class KidInvitationText {

    private KidInvitationText() {
    }

    /** True when the inviter message must not be shown to this viewer. */
    public static boolean hidden(KidChatPolicy policy, DSGPlayerData viewer,
                                 TBMessage m) {
        if (policy == null || viewer == null || m == null) {
            return false;
        }
        if (viewer.getParentPid() == null) {
            return false;
        }
        if (m.getMoveNum() != 0 || m.getSeqNbr() != 1) {
            return false;
        }
        if (m.getPid() == viewer.getPlayerID()) {
            return false;
        }
        try {
            return !policy.allowed(viewer.getPlayerID(), m.getPid());
        } catch (RuntimeException e) {
            return true;
        }
    }
}
