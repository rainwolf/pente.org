package org.pente.gameServer.kids;

import java.util.Collections;
import java.util.Set;

import org.apache.log4j.Category;

import org.pente.gameServer.core.DSGPlayerData;
import org.pente.gameServer.core.DSGPlayerStorer;
import org.pente.gameServer.core.SystemPids;

/**
 * {@link KidChatPolicy} backed by the player and kid storers. Fails
 * closed: any storer error refuses the chat.
 */
public class StorerKidChatPolicy implements KidChatPolicy {

    private static final Category log4j = Category.getInstance(StorerKidChatPolicy.class.getName());

    private final DSGPlayerStorer players;
    private final KidStorer kids;

    public StorerKidChatPolicy(DSGPlayerStorer players, KidStorer kids) {
        this.players = players;
        this.kids = kids;
    }

    public boolean allowed(long pidA, long pidB) {
        if (pidA == pidB || isSystem(pidA) || isSystem(pidB)) {
            return true;
        }
        try {
            return KidChatRules.allowed(party(pidA), party(pidB));
        } catch (Exception e) {
            log4j.error("KidChatPolicy check failed for " + pidA + ", " + pidB, e);
            return false;
        }
    }

    private static boolean isSystem(long pid) {
        return pid == SystemPids.SYSTEM_PID || pid == SystemPids.COMPUTER_PID;
    }

    private KidChatRules.Party party(long pid) throws Exception {
        DSGPlayerData d = players.loadPlayer(pid);
        if (d == null) {
            return new KidChatRules.Party(pid, false, false, null, Collections.<Long>emptySet());
        }
        Set<Long> whitelist = d.getParentPid() != null
                ? kids.getWhitelist(pid)
                : Collections.<Long>emptySet();
        return KidChatRules.party(d, whitelist);
    }
}
