package org.pente.turnBased.web;

import java.util.Comparator;

import org.pente.game.*;
import org.pente.gameServer.core.*;
import org.pente.turnBased.TBGame;

public class AdminComparator implements Comparator<TBGame> {
    private int sortBy;
    private DSGPlayerStorer dsgPlayerStorer;

    public AdminComparator(int sortBy, DSGPlayerStorer dsgPlayerStorer) {
        this.sortBy = sortBy;
        this.dsgPlayerStorer = dsgPlayerStorer;
    }

    public int compare(TBGame g1, TBGame g2) {
        try {
            if (sortBy == 1) {
                String g1N = GridStateFactory.getGameName(g1.getGame());
                String g2N = GridStateFactory.getGameName(g2.getGame());
                return g1N.compareTo(g2N);
            } else if (sortBy == 2) {
                return g1.getState() - g2.getState();
            } else if (sortBy == 3) {
                DSGPlayerData p1 = dsgPlayerStorer.loadPlayer(g1.getPlayer1Pid());
                String p1Name = p1 == null ? "" : p1.getName();
                DSGPlayerData p2 = dsgPlayerStorer.loadPlayer(g2.getPlayer1Pid());
                String p2Name = p2 == null ? "" : p2.getName();
                return p1Name.compareTo(p2Name);
            } else if (sortBy == 4) {
                DSGPlayerData p1 = dsgPlayerStorer.loadPlayer(g1.getPlayer1Pid());
                DSGPlayerGameData p1g = null;
                if (p1 != null) {
                    p1g = p1.getPlayerGameData(g1.getGame());
                }
                int p1r = p1g == null ? 0 : (int) Math.round(p1g.getRating());

                DSGPlayerData p2 = dsgPlayerStorer.loadPlayer(g2.getPlayer1Pid());
                DSGPlayerGameData p2g = null;
                if (p2 != null) {
                    p2g = p2.getPlayerGameData(g2.getGame());
                }
                int p2r = p2g == null ? 0 : (int) Math.round(p2g.getRating());

                return p1r - p2r;
            } else if (sortBy == 5) {
                DSGPlayerData p1 = dsgPlayerStorer.loadPlayer(g1.getPlayer2Pid());
                String p1Name = p1 == null ? "" : p1.getName();
                DSGPlayerData p2 = dsgPlayerStorer.loadPlayer(g2.getPlayer2Pid());
                String p2Name = p2 == null ? "" : p2.getName();
                return p1Name.compareTo(p2Name);
            } else if (sortBy == 6) {
                DSGPlayerData p1 = dsgPlayerStorer.loadPlayer(g1.getPlayer2Pid());
                DSGPlayerGameData p1g = null;
                if (p1 != null) {
                    p1g = p1.getPlayerGameData(g1.getGame());
                }
                int p1r = p1g == null ? 0 : (int) Math.round(p1g.getRating());

                DSGPlayerData p2 = dsgPlayerStorer.loadPlayer(g2.getPlayer2Pid());
                DSGPlayerGameData p2g = null;
                if (p2 != null) {
                    p2g = p2.getPlayerGameData(g2.getGame());
                }
                int p2r = p2g == null ? 0 : (int) Math.round(p2g.getRating());

                return p1r - p2r;
            } else if (sortBy == 7) {
                return g1.getNumMoves() - g2.getNumMoves();
            } else if (sortBy == 8) {
                return g1.getDaysPerMove() - g2.getDaysPerMove();
            } else if (sortBy == 9) {
                if (g1.getTimeoutDate() == null) {
                    if (g2.getTimeoutDate() == null) {
                        return 0;
                    } else {
                        return 1;
                    }
                } else {
                    if (g2.getTimeoutDate() == null) {
                        return -1;
                    } else {
                        return g1.getTimeoutDate().compareTo(g2.getTimeoutDate());
                    }
                }
            } else if (sortBy == 10) {
                if (g1.isRated()) {
                    return g2.isRated() ? 0 : 1;
                }
                return g2.isRated() ? -1 : 0;
            } else {
                return 0;
            }
        } catch (Throwable t) {
            return 0;
        }
    }
}