package org.pente.gameServer.core;

import org.pente.game.GameData;

import java.util.Date;

public class LiveSet {

    private long sid;
    private long p1Pid;
    private long p2Pid;
    private long g1Gid;
    private long g2Gid;

    private GameData g1;
    private GameData g2;

    public static final String STATUS_ACTIVE = "A";
    public static final String STATUS_CANCELED = "N";
    public static final String STATUS_CANCEL_SINGLE_DISCONNECT = "S";
    public static final String STATUS_CANCEL_DOUBLE_DISCONNECT = "D";
    public static final String STATUS_ONE_GAME_COMPLETED = "O";
    public static final String STATUS_COMPLETED = "C";
    // these are used when player leaves during g1 and other player
    // forces resignation of set or resigns set
    public static final String STATUS_FORCED = "F";
    public static final String STATUS_RESIGN = "R";
    private String status;

    private int winner;
    private Date creationDate;
    private Date completionDate;

    public long getSid() {
        return sid;
    }

    public void setSid(long sid) {
        this.sid = sid;
    }

    public long getP1Pid() {
        return p1Pid;
    }

    public void setP1Pid(long pid) {
        p1Pid = pid;
    }

    public long getP2Pid() {
        return p2Pid;
    }

    public void setP2Pid(long pid) {
        p2Pid = pid;
    }

    public long getG1Gid() {
        return g1Gid;
    }

    public void setG1Gid(long gid) {
        g1Gid = gid;
    }

    public long getG2Gid() {
        return g2Gid;
    }

    public void setG2Gid(long gid) {
        g2Gid = gid;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getWinner() {
        return winner;
    }

    public void setWinner(int winner) {
        this.winner = winner;
    }

    public Date getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(Date creationDate) {
        this.creationDate = creationDate;
    }

    public Date getCompletionDate() {
        return completionDate;
    }

    public void setCompletionDate(Date completionDate) {
        this.completionDate = completionDate;
    }

    public GameData getG1() {
        return g1;
    }

    public void setG1(GameData g1) {
        this.g1 = g1;
        if (g1 != null) {
            this.g1Gid = g1.getGameID();
        }
    }

    public GameData getG2() {
        return g2;
    }

    public void setG2(GameData g2) {
        this.g2 = g2;
        if (g2 != null) {
            this.g2Gid = g2.getGameID();
        }
    }

    public boolean isComplete() {
        return status != STATUS_ACTIVE &&
                status != STATUS_ONE_GAME_COMPLETED;
    }
}
