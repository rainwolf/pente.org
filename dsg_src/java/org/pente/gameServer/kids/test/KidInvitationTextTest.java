package org.pente.gameServer.kids.test;

import java.util.Date;

import junit.framework.TestCase;

import org.pente.gameServer.core.SimpleDSGPlayerData;
import org.pente.gameServer.kids.KidChatPolicy;
import org.pente.gameServer.kids.KidInvitationText;
import org.pente.turnBased.TBMessage;

/** Open-invitation text filtered when served to a kid (decision D3). */
public class KidInvitationTextTest extends TestCase {

    private static final long KID = 100L;
    private static final long PARENT = 1L;
    private static final long STRANGER = 200L;

    /** Refuses only the kid/stranger pair. */
    private final KidChatPolicy policy = (a, b) ->
            !((a == KID && b == STRANGER) || (a == STRANGER && b == KID));

    public KidInvitationTextTest(String name) {
        super(name);
    }

    private static SimpleDSGPlayerData player(long pid, Long parentPid) {
        SimpleDSGPlayerData d = new SimpleDSGPlayerData();
        d.setPlayerID(pid);
        d.setParentPid(parentPid);
        return d;
    }

    private static TBMessage message(long pid, int moveNum, int seqNbr) {
        TBMessage m = new TBMessage();
        m.setPid(pid);
        m.setMoveNum(moveNum);
        m.setSeqNbr(seqNbr);
        m.setMessage("play me");
        m.setDate(new Date());
        return m;
    }

    public void testStrangerInviterTextHiddenFromKid() {
        assertTrue(KidInvitationText.hidden(
                policy, player(KID, PARENT), message(STRANGER, 0, 1)));
    }

    public void testAllowedInviterTextShown() {
        assertTrue(!KidInvitationText.hidden(
                policy, player(KID, PARENT), message(300L, 0, 1)));
        // The kid's own invitation text is never hidden from the kid.
        assertTrue(!KidInvitationText.hidden(
                policy, player(KID, PARENT), message(KID, 0, 1)));
    }

    public void testNonKidViewerSeesText() {
        // Policy would refuse the pair, but the viewer is not a kid.
        assertTrue(!KidInvitationText.hidden(
                policy, player(STRANGER, null), message(KID, 0, 1)));
    }

    public void testOnlyMoveZeroFirstMessageHidden() {
        assertTrue(!KidInvitationText.hidden(
                policy, player(KID, PARENT), message(STRANGER, 0, 2)));
        assertTrue(!KidInvitationText.hidden(
                policy, player(KID, PARENT), message(STRANGER, 3, 1)));
    }

    public void testNullPolicyOrViewerShows() {
        assertTrue(!KidInvitationText.hidden(
                null, player(KID, PARENT), message(STRANGER, 0, 1)));
        assertTrue(!KidInvitationText.hidden(
                policy, null, message(STRANGER, 0, 1)));
    }
}
