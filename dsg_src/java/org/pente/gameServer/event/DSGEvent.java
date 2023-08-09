package org.pente.gameServer.event;

public interface DSGEvent extends java.io.Serializable {
    public String toString();

    public void setCurrentTime();

    public long getTime();
}

