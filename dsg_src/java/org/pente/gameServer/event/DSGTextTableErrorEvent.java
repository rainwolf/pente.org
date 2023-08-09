package org.pente.gameServer.event;

public class DSGTextTableErrorEvent extends AbstractDSGTableErrorEvent {

    private String text;

    public DSGTextTableErrorEvent() {
    }

    public DSGTextTableErrorEvent(String player, int table, String text, int error) {
        super(player, table, error);

        setText(text);
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }

    public String toString() {
        return "text \"" + getText() + "\" " + super.toString();
    }
}

