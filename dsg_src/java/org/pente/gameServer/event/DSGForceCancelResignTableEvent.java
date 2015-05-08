package org.pente.gameServer.event;

public class DSGForceCancelResignTableEvent extends AbstractDSGTableEvent {

	public static final int CANCEL = 1;
	public static final int RESIGN = 2;

	private int action;

    public DSGForceCancelResignTableEvent() {
        super();
    }

    public DSGForceCancelResignTableEvent(String player, int table, int action) {
        super(player, table);
        
        this.action = action;
    }

	public boolean forcedResign() {
		return action == RESIGN;
	}
	public boolean forcedCancel() {
		return action == CANCEL;
	}

	public int getAction() {
		return action;
	}

	public String toString() {
		return "force " + (action == CANCEL ? "cancel " : "resign ") + super.toString();
	}
}

