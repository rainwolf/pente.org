package org.pente.gameServer.event;

public class DSGArenaCreateTableEvent extends AbstractDSGTableEvent {

    private int game;
    private int minutes;
    private int increment;
    private boolean rated;
    private int color;

    public DSGArenaCreateTableEvent(String player, int table, int game, int minutes, int increment, boolean rated, int color) {
        super(player, table);
        this.game = game;
        this.minutes = minutes;
        this.increment = increment;
        this.rated = rated;
        this.color = color;
    }

    public String toString() {
        return "Arena create table: (game, minutes, seconds, increment, rated) = (" + game + ", " + minutes + ", " + increment + ", " + rated + ") " + super.toString();
    }

    public int getGame() {
        return game;
    }

    public int getMinutes() {
        return minutes;
    }

    public boolean isRated() {
        return rated;
    }

    public int getColor() {
        return color;
    }

    public int getIncrement() {
        return increment;
    }
}


