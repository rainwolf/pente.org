package org.pente.gameServer.event;

public class DSGSystemMessageTableEvent extends AbstractDSGTableEvent {

    private String message;

    public DSGSystemMessageTableEvent() {
        super();
    }

    public DSGSystemMessageTableEvent(int table, String message) {
        super("system", table);

        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public String toString() {
        return "system message - " + message + " - " + super.toString();
    }
}
