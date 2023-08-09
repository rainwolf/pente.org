package org.pente.gameServer.event;

public class DSGGameStateTableEvent extends AbstractDSGTableEvent {

    public static final int NO_GAME_IN_PROGRESS = 1;
    public static final int GAME_IN_PROGRESS = 2;
    public static final int GAME_WAITING_FOR_PLAYER_TO_RETURN = 3;
    public static final int WAIT_GAME_TWO_OF_SET = 4;

    private int state;
    private String changeText;
    private String winner;
    private int gameInSet;

    public DSGGameStateTableEvent() {
        super();
    }

    public DSGGameStateTableEvent(String player, int table, int state, String changeText, int gameInSet) {
        this(player, table, state, changeText, null, gameInSet);
    }

    public DSGGameStateTableEvent(String player, int table, int state,
                                  String changeText, String winner, int gameInSet) {
        super(player, table);

        setState(state);
        setChangeText(changeText);
        setWinner(winner);
        setGameInSet(gameInSet);
    }

    public void setState(int state) {
        this.state = state;
    }

    public int getState() {
        return state;
    }

    public String getChangeText() {
        return changeText;
    }

    public void setChangeText(String changeText) {
        this.changeText = changeText;
    }

    public String getWinner() {
        return winner;
    }

    public void setWinner(String winner) {
        this.winner = winner;
    }

    public String toString() {
        return "game state " + state + " " +
                (changeText == null ? "" : changeText + " ") +
                gameInSet + " " +
                super.toString();
    }

    public int getGameInSet() {
        return gameInSet;
    }

    public void setGameInSet(int gameInSet) {
        this.gameInSet = gameInSet;
    }

}

