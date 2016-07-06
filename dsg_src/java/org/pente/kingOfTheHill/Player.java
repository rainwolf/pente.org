package org.pente.kingOfTheHill;

import java.util.Date;

/**
 * Created by waliedothman on 04/07/16.
 */
public class Player {
    private long pid;

    private Date lastGame;

    public Player(long pid, Date lastGame) {
        this.pid = pid;
        this.lastGame = lastGame;
    }

    public long getPid() {
        return pid;
    }

    public Date getLastGame() {
        return lastGame;
    }

    public void setLastGame(Date lastGame) {
        this.lastGame = lastGame;
    }

//    @Override
//    public boolean equals(Object o) {
//        if (!(o instanceof Player)) {
//            return false;
//        }
//        return pid == ((Player) o).getPid();
//    }
//
//    @Override
//    public int hashCode() {
//        return Long.hashCode(pid);
//    }
}
