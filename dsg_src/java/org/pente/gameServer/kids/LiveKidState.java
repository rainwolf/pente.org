package org.pente.gameServer.kids;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** A live-room player's kid state: parent, whitelist and live-chat copy flag. Immutable. */
public final class LiveKidState implements Serializable {

    private static final long serialVersionUID = 1L;

    private final long pid;
    private final Long parentPid;
    private final Set<Long> whitelist;
    private final boolean copyLiveChat;
    private final long loadedSeq;

    public LiveKidState(long pid, Long parentPid, Set<Long> whitelist, boolean copyLiveChat,
                        long loadedSeq) {
        this.pid = pid;
        this.parentPid = parentPid;
        this.whitelist = whitelist == null ? Collections.<Long>emptySet()
                : Collections.unmodifiableSet(new HashSet<Long>(whitelist));
        this.copyLiveChat = copyLiveChat;
        this.loadedSeq = loadedSeq;
    }

    public static LiveKidState notKid(long pid) {
        return new LiveKidState(pid, null, null, false, 0);
    }

    public long getPid() {
        return pid;
    }

    public Long getParentPid() {
        return parentPid;
    }

    public Set<Long> getWhitelist() {
        return whitelist;
    }

    public boolean isCopyLiveChat() {
        return copyLiveChat;
    }

    public long getLoadedSeq() {
        return loadedSeq;
    }

    public boolean isKid() {
        return parentPid != null;
    }
}
