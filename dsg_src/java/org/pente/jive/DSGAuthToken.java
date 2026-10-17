package org.pente.jive;

import com.jivesoftware.base.AuthToken;

import java.io.Serializable;

public final class DSGAuthToken implements AuthToken, Serializable {

    private static final long serialVersionUID = 5618811718939943524L;

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



