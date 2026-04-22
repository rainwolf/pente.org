package org.pente.gameServer.event;

public class DSGArenaCreateTableEvent extends AbstractDSGTableEvent {

    private int game;
    private int initialMinutes;
    private int incrementalSeconds;
    private boolean rated;
    private int playAs;
    private boolean timed;

    public DSGArenaCreateTableEvent(String player, int table, int game, int minutes, int increment, boolean rated, int color, boolean timed) {
        super(player, table);
        this.game = game;
        this.initialMinutes = minutes;
        this.incrementalSeconds = increment;
        this.rated = rated;
        this.playAs = color;
        this.timed = timed;
    }

    public String toString() {
        return "Arena create table: (game, minutes, seconds, increment, rated) = (" + game + ", " + initialMinutes + ", " + incrementalSeconds + ", " + rated + ") " + super.toString();
    }

    public int getGame() {
        return game;
    }

    public int getInitialMinutes() {
        return initialMinutes;
    }

    public boolean isRated() {
        return rated;
    }

    public int getPlayAs() {
        return playAs;
    }

    public int getIncrementalSeconds() {
        return incrementalSeconds;
    }

    public boolean isTimed() {
        return timed;
    }

}


