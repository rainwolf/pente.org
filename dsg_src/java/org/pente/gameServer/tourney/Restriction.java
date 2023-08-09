package org.pente.gameServer.tourney;

public class Restriction {

    public static final int RATING_RESTRICTION_ABOVE = 1;
    public static final int RATING_RESTRICTION_BELOW = 2;
    public static final int GAMES_RESTRICTION_ABOVE = 3;

    private int type;
    private int value;

    public Restriction(int type, int value) {
        this.type = type;
        this.value = value;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public int getValue() {
        return value;
    }

    public void setValue(int value) {
        this.value = value;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Restriction)) {
            return false;
        }
        Restriction r = (Restriction) o;
        return this.type == r.getType() && this.value == r.getValue();
    }

    @Override
    public int hashCode() {
        return type * value;
    }
}
