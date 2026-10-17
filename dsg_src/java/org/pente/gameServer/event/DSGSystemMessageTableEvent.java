package org.pente.gameServer.event;

public class DSGSystemMessageTableEvent extends AbstractDSGTableEvent {
    private static final long serialVersionUID = -4545881470136679681L;

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
