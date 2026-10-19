package org.pente.gameServer.kids;

import java.security.SecureRandom;
import java.util.Date;

/** A pending request to link an existing account as a kid of a parent. */
public class KidLinkRequest {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final String code;
    private final long parentPid;
    private final long kidPid;
    private final Date creationDate;

    public KidLinkRequest(String code, long parentPid, long kidPid, Date creationDate) {
        this.code = code;
        this.parentPid = parentPid;
        this.kidPid = kidPid;
        this.creationDate = creationDate;
    }

    /** 32 random bytes as 64 lowercase hex chars. */
    public static String newCode() {
        byte[] b = new byte[32];
        RANDOM.nextBytes(b);
        StringBuilder sb = new StringBuilder(64);
        for (byte x : b) {
            sb.append(Character.forDigit((x >> 4) & 0xF, 16));
            sb.append(Character.forDigit(x & 0xF, 16));
        }
        return sb.toString();
    }

    public String getCode() {
        return code;
    }

    public long getParentPid() {
        return parentPid;
    }

    public long getKidPid() {
        return kidPid;
    }

    public Date getCreationDate() {
        return creationDate;
    }
}
