package org.pente.gameServer.event;

public class DSGForceCancelResignTableErrorEvent extends AbstractDSGTableErrorEvent {

    private int action;

    public DSGForceCancelResignTableErrorEvent() {
        super();
    }

    public DSGForceCancelResignTableErrorEvent(String player, int table, int error, int action) {
        super(player, table, error);

        this.action = action;
    }

    public boolean forcedResign() {
        return action == DSGForceCancelResignTableEvent.RESIGN;
    }

    public boolean forcedCancel() {
        return action == DSGForceCancelResignTableEvent.CANCEL;
    }

    public int getAction() {
        return action;
    }

    public String toString() {
        return "force " + (action == DSGForceCancelResignTableEvent.CANCEL ? "cancel " : "resign ") + super.toString();
    }
}

