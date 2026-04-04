package org.pente.gameServer.core;

import java.io.Serial;
import java.io.Serializable;

public class DSGAvatar implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private byte[] avatar;
    private String avatarContentType;
    private long avatarLastModified;

    public DSGAvatar(byte[] avatar, String avatarContentType, long avatarLastModified) {
        this.avatar = avatar;
        this.avatarContentType = avatarContentType;
        this.avatarLastModified = avatarLastModified;
    }

    public long getAvatarLastModified() {
        return avatarLastModified;
    }

    public String getAvatarContentType() {
        return avatarContentType;
    }

    public byte[] getAvatar() {
        return avatar;
    }
}
