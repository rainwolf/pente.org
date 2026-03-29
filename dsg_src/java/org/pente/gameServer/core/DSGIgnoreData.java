package org.pente.gameServer.core;

import java.io.Serial;
import java.io.Serializable;

public class DSGIgnoreData implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private long ignoreId;
    private long pid;
    private long ignorePid;
    private boolean ignoreInvite;
    private boolean ignoreChat;
    private boolean guest;

    public long getIgnoreId() {
        return ignoreId;
    }

    public void setIgnoreId(long ignoreId) {
        this.ignoreId = ignoreId;
    }

    public long getIgnorePid() {
        return ignorePid;
    }

    public void setIgnorePid(long ignorePid) {
        this.ignorePid = ignorePid;
    }

    public boolean getIgnoreInvite() {
        return ignoreInvite;
    }

    public void setIgnoreInvite(boolean ignoreInvite) {
        this.ignoreInvite = ignoreInvite;
    }

    public boolean getIgnoreChat() {
        return ignoreChat;
    }

    public void setIgnoreChat(boolean ignoreChat) {
        this.ignoreChat = ignoreChat;
    }

    public long getPid() {
        return pid;
    }

    public void setPid(long pid) {
        this.pid = pid;
    }

    public String toString() {
        return ignoreId + ": " + pid + " ig " + ignorePid + " [" +
                ignoreInvite + ", " + ignoreChat + "]";
    }

    public boolean isGuest() {
        return guest;
    }

    public void setGuest(boolean guest) {
        this.guest = guest;
    }

}
