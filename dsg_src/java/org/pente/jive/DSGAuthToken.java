package org.pente.jive;

import com.jivesoftware.base.AuthToken;

import java.io.Serializable;

public final class DSGAuthToken implements AuthToken, Serializable {

    private long pid;

    public DSGAuthToken(long pid) {
        this.pid = pid;
    }

    // AuthToken Interface
    public long getUserID() {
        return pid;
    }

    public boolean isAnonymous() {
        return pid == -1L;
    }
}



