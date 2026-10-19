package org.pente.gameServer.kids;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.pente.gameServer.core.DSGPlayerData;
import org.pente.gameServer.core.SystemPids;

/**
 * Who a kid account may chat with. Pure: callers supply each side's
 * flags and whitelist. Two players may chat only when each side
 * accepts the other.
 */
public final class KidChatRules {

    private KidChatRules() {
    }

    /** One side of a conversation. */
    public static final class Party {
        public final long pid;
        public final boolean admin;
        public final boolean computer;
        public final Long parentPid;
        public final Set<Long> whitelist;

        public Party(long pid, boolean admin, boolean computer, Long parentPid, Set<Long> whitelist) {
            this.pid = pid;
            this.admin = admin;
            this.computer = computer;
            this.parentPid = parentPid;
            this.whitelist = whitelist == null
                    ? Collections.<Long>emptySet()
                    : Collections.unmodifiableSet(new HashSet<>(whitelist));
        }

        public boolean isKid() {
            return parentPid != null;
        }
    }

    public static Party party(DSGPlayerData d, Set<Long> whitelist) {
        return new Party(d.getPlayerID(), d.isAdmin(), d.isComputer(), d.getParentPid(), whitelist);
    }

    public static boolean allowed(Party a, Party b) {
        if (a.pid == b.pid) {
            return true;
        }
        return acceptable(a, b) && acceptable(b, a);
    }

    /** Whether {@code side} accepts chat with {@code other}. Non-kids accept everyone. */
    public static boolean acceptable(Party side, Party other) {
        if (!side.isKid()) {
            return true;
        }
        long p = other.pid;
        return p == side.parentPid
                || side.whitelist.contains(p)
                || p == SystemPids.SYSTEM_PID
                || p == SystemPids.COMPUTER_PID
                || other.admin
                || other.computer;
    }
}
