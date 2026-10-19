package org.pente.gameServer.kids;

import java.io.Serializable;

/** Parent-controlled copy settings for one kid account. */
public class KidSettings implements Serializable {

    private static final long serialVersionUID = 1L;

    private final long kidPid;
    private final boolean copyMessages;
    private final boolean copyLiveChat;

    public KidSettings(long kidPid, boolean copyMessages, boolean copyLiveChat) {
        this.kidPid = kidPid;
        this.copyMessages = copyMessages;
        this.copyLiveChat = copyLiveChat;
    }

    public static KidSettings off(long kidPid) {
        return new KidSettings(kidPid, false, false);
    }

    public long getKidPid() {
        return kidPid;
    }

    public boolean isCopyMessages() {
        return copyMessages;
    }

    public boolean isCopyLiveChat() {
        return copyLiveChat;
    }
}
