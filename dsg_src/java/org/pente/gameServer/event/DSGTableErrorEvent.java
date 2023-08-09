package org.pente.gameServer.event;

public interface DSGTableErrorEvent extends DSGTableEvent {

    public static final int ALREADY_IN_TABLE = 1;
    public static final int NOT_IN_TABLE = 2;
    public static final int GAME_IN_PROGRESS = 3;
    public static final int NO_GAME_IN_PROGRESS = 4;
    public static final int GAME_WAITING_FOR_PLAYER_TO_RETURN = 5;
    public static final int NOT_SITTING = 6;
    public static final int ALREADY_SITTING = 7;
    public static final int SEAT_TAKEN = 8;
    public static final int NOT_ALL_PLAYERS_SITTING = 9;
    public static final int PLAY_ALREADY_CLICKED = 10;
    public static final int NOT_TABLE_OWNER = 11;
    public static final int NOT_TURN = 12;
    public static final int INVALID_MOVE = 13;
    public static final int UNDO_ALREADY_REQUESTED = 14;
    public static final int CANT_UNDO = 15;
    public static final int NO_UNDO_REQUESTED = 16;
    public static final int CANCEL_ALREADY_REQUESTED = 17;
    public static final int NO_CANCEL_REQUESTED = 18;
    public static final int COMPUTER_SITTING = 19;
    public static final int PRIVATE_TABLE = 20;
    public static final int TOURNAMENT_GAME = 21;
    public static final int BOOTED = 22;
    public static final int GUEST_NOT_ALLOWED = 23;
    public static final int WAIT_GAME_TWO_OF_SET = 24;

    public static final int UNKNOWN = 99;

    public void setError(int error);

    public int getError();
}

