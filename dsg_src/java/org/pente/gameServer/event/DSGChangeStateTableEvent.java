package org.pente.gameServer.event;

import org.pente.game.*;

public class DSGChangeStateTableEvent extends AbstractDSGTableEvent {

	private boolean timed;
	private int initialMinutes;
	private int incrementalSeconds;
	private boolean rated;
	private int game;
    
    public static final int TABLE_TYPE_PUBLIC = 1;
    public static final int TABLE_TYPE_PRIVATE = 2;
    private int tableType;

    public DSGChangeStateTableEvent() {
        super();
    }

    public DSGChangeStateTableEvent(String player, int table) {
        super(player, table);
    }

	public void setTimed(boolean timed) {
		this.timed = timed;
	}
	public boolean getTimed() {
		return timed;
	}
	
    public void setInitialMinutes(int initialMinutes) {
        this.initialMinutes = initialMinutes;
    }
	public int getInitialMinutes() {
		return initialMinutes;
	}

    public void setTableType(int tableType) {
        this.tableType = tableType;
    }
    public void setTableType(String tableTypeStr) {
        if (tableTypeStr.toLowerCase().equals("public")) {
            tableType = TABLE_TYPE_PUBLIC;
        }
        else if (tableTypeStr.toLowerCase().equals("private")) {
            tableType = TABLE_TYPE_PRIVATE;
        }
    }
    public int getTableType() {
        return tableType;
    }
    public boolean isTablePrivate() {
        return tableType == TABLE_TYPE_PRIVATE;
    }

	public void setIncrementalSeconds(int incrementalSeconds) {
		this.incrementalSeconds = incrementalSeconds;
	}
	public int getIncrementalSeconds() {
		return incrementalSeconds;
	}

    public void setRated(boolean rated) {
        this.rated = rated;
    }

	public boolean getRated() {
		return rated;
	}

    public void setGame(int game) {
        this.game = game;
    }

	public int getGame() {
		return game;
	}

	public String toString() {
		return "change state " + 
			   (getRated() ? "rated " : "not rated ") + 
			   (getTimed() ? "timed " : "not timed ") +
			   getInitialMinutes() + ":" + getIncrementalSeconds() + " " +
               (tableType == TABLE_TYPE_PUBLIC ? "public " : "private ") +
			   GridStateFactory.getGameName(getGame()) + "[" +getGame() + "] " +
			   super.toString();
	}
}

