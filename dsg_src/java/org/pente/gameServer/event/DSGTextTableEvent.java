package org.pente.gameServer.event;

public class DSGTextTableEvent extends AbstractDSGTableEvent {

    private String text;

    public DSGTextTableEvent() {
    }

    public DSGTextTableEvent(String player, int tableNum, String text) {
        super(player, tableNum);

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

